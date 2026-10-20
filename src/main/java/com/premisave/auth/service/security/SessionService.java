package com.premisave.auth.service.security;

import com.mongodb.client.result.UpdateResult;
import com.premisave.auth.config.SecurityProperties;
import com.premisave.auth.entity.KnownDevice;
import com.premisave.auth.entity.User;
import com.premisave.auth.entity.UserSession;
import com.premisave.auth.enums.LoginMethod;
import com.premisave.auth.repository.UserSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Server-side sessions. A session is what an access token and its refresh token belong to
 * (the "sid" claim). Revoking it makes both stop working at once in this service.
 *
 * Redis keeps a short-lived "known good" marker and a "revoked" marker so the per-request
 * check usually avoids the database; the database stays the source of truth.
 */
@Slf4j
@Service
public class SessionService {

    private static final Duration ACTIVE_CACHE_TTL = Duration.ofSeconds(60);

    private final UserSessionRepository repository;
    private final MongoTemplate mongo;
    private final StringRedisTemplate redis;
    private final SecurityProperties properties;
    private final Clock clock;

    public SessionService(UserSessionRepository repository, MongoTemplate mongo, StringRedisTemplate redis,
                          SecurityProperties properties, Clock clock) {
        this.repository = repository;
        this.mongo = mongo;
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    public Instant now() {
        return Instant.now(clock);
    }

    public Duration sessionLifetime() {
        return Duration.ofDays(Math.max(1, properties.getSessionTtlDays()));
    }

    public UserSession open(User user, KnownDevice device, ClientContext ctx, DeviceInfo info,
                            GeoLocation geo, LoginMethod method) {
        Instant now = now();
        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setDeviceId(device == null ? null : device.getId());
        session.setLoginMethod(method);
        session.setIpAddress(ctx.ip());
        session.setUserAgent(UserAgentParser.sanitize(ctx.userAgent()));
        session.setDeviceLabel(info.label());
        session.setCountry(geo.country());
        session.setRegion(geo.region());
        session.setCity(geo.city());
        session.setCreatedAt(now);
        session.setLastSeenAt(now);
        session.setExpiresAt(now.plus(sessionLifetime()));
        return repository.save(session);
    }

    public Optional<UserSession> find(String sessionId) {
        return sessionId == null ? Optional.empty() : repository.findById(sessionId);
    }

    public Optional<UserSession> findForUser(String sessionId, String userId) {
        return repository.findByIdAndUserId(sessionId, userId);
    }

    public List<UserSession> activeSessions(String userId) {
        return repository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(userId, now());
    }

    /** Per-request check used by the JWT filter. */
    public boolean isActive(String sessionId, String userId) {
        if (sessionId == null || userId == null) {
            return false;
        }
        try {
            if (Boolean.TRUE.equals(redis.hasKey(revokedKey(sessionId)))) {
                return false;
            }
            if (userId.equals(redis.opsForValue().get(activeKey(sessionId)))) {
                return true;
            }
        } catch (RuntimeException e) {
            log.debug("Session cache unavailable, using the database: {}", e.getClass().getSimpleName());
        }

        UserSession session = repository.findById(sessionId).orElse(null);
        boolean active = session != null && userId.equals(session.getUserId()) && session.isActive(now());
        try {
            if (active) {
                redis.opsForValue().set(activeKey(sessionId), userId, ACTIVE_CACHE_TTL);
            } else if (session != null && session.getRevokedAt() != null) {
                redis.opsForValue().set(revokedKey(sessionId), "1", sessionLifetime());
            }
        } catch (RuntimeException ignored) {
            // cache only
        }
        return active;
    }

    /** Revokes one session of this user. Returns true if it was active and is now revoked. */
    public boolean revoke(String sessionId, String userId, String reason, String actor) {
        Query query = Query.query(Criteria.where("_id").is(sessionId)
                .and("userId").is(userId)
                .and("revokedAt").is(null));
        Update update = new Update()
                .set("revokedAt", now())
                .set("revokedReason", reason)
                .set("revokedBy", actor);
        UpdateResult result = mongo.updateFirst(query, update, UserSession.class);
        boolean changed = result.getModifiedCount() > 0;
        if (changed) {
            markRevoked(List.of(sessionId));
        }
        return changed;
    }

    /** Revokes every active session of the user except one (pass null to revoke all). Returns how many. */
    public int revokeAll(String userId, String exceptSessionId, String reason, String actor) {
        List<UserSession> active = activeSessions(userId);
        List<String> ids = active.stream()
                .map(UserSession::getId)
                .filter(id -> !id.equals(exceptSessionId))
                .toList();
        if (ids.isEmpty()) {
            return 0;
        }
        Query query = Query.query(Criteria.where("_id").in(ids).and("revokedAt").is(null));
        Update update = new Update()
                .set("revokedAt", now())
                .set("revokedReason", reason)
                .set("revokedBy", actor);
        UpdateResult result = mongo.updateMulti(query, update, UserSession.class);
        markRevoked(ids);
        return (int) result.getModifiedCount();
    }

    /** Revokes every active session that was started from the given device. */
    public int revokeForDevice(String userId, String deviceId, String reason, String actor) {
        List<String> ids = activeSessions(userId).stream()
                .filter(s -> deviceId.equals(s.getDeviceId()))
                .map(UserSession::getId)
                .toList();
        if (ids.isEmpty()) {
            return 0;
        }
        Query query = Query.query(Criteria.where("_id").in(ids).and("revokedAt").is(null));
        Update update = new Update()
                .set("revokedAt", now())
                .set("revokedReason", reason)
                .set("revokedBy", actor);
        UpdateResult result = mongo.updateMulti(query, update, UserSession.class);
        markRevoked(ids);
        return (int) result.getModifiedCount();
    }

    /**
     * Atomically advances the refresh generation if the presented one is the current one.
     * Empty means the session is gone, revoked or expired, or an older refresh token was replayed.
     */
    public Optional<UserSession> rotate(String sessionId, String userId, int presentedGeneration) {
        Instant now = now();
        Query query = Query.query(Criteria.where("_id").is(sessionId)
                .and("userId").is(userId)
                .and("revokedAt").is(null)
                .and("expiresAt").gt(now)
                .and("refreshGeneration").is(presentedGeneration));
        Update update = new Update().inc("refreshGeneration", 1).set("lastRefreshAt", now).set("lastSeenAt", now);
        UserSession updated = mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), UserSession.class);
        return Optional.ofNullable(updated);
    }

    /** Updates lastSeenAt at most once per configured interval. */
    public void touch(String sessionId) {
        Instant now = now();
        Instant threshold = now.minus(Duration.ofMinutes(Math.max(1, properties.getSessionTouchMinutes())));
        Query query = Query.query(Criteria.where("_id").is(sessionId).and("lastSeenAt").lt(threshold));
        try {
            mongo.updateFirst(query, new Update().set("lastSeenAt", now), UserSession.class);
        } catch (RuntimeException e) {
            log.debug("Could not update session activity: {}", e.getClass().getSimpleName());
        }
    }

    private void markRevoked(List<String> ids) {
        try {
            for (String id : ids) {
                redis.delete(activeKey(id));
                redis.opsForValue().set(revokedKey(id), "1", sessionLifetime());
            }
        } catch (RuntimeException e) {
            // The database already says revoked; only the cache could not be updated.
            log.warn("Could not mark sessions revoked in cache: {}", e.getClass().getSimpleName());
        }
    }

    private static String activeKey(String sid) {
        return "sess:ok:" + sid;
    }

    private static String revokedKey(String sid) {
        return "sess:revoked:" + sid;
    }
}
