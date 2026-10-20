package com.premisave.auth.service.security;

import com.premisave.auth.config.SecurityProperties;
import com.premisave.auth.entity.KnownDevice;
import com.premisave.auth.entity.User;
import com.premisave.auth.repository.KnownDeviceRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Recognises returning browsers and apps by an opaque device token.
 *
 * The token is random, so it cannot be guessed, and only its hash is stored. It identifies
 * a browser profile, not a person or a physical machine: clearing cookies makes the same
 * browser look new, and copying the token to another machine makes that machine look
 * recognised. That is why trust never replaces authentication.
 */
@Service
public class DeviceService {

    /**
     * @param device       the recognised or newly created device
     * @param isNew        true when this token was not known for the user
     * @param firstDevice  true when the account had no devices and had never signed in before
     * @param issuedToken  the new raw token to hand to the client, only when one was created
     */
    public record Recognition(KnownDevice device, boolean isNew, boolean firstDevice, String issuedToken) {
    }

    private final KnownDeviceRepository repository;
    private final MongoTemplate mongo;
    private final SecurityProperties properties;
    private final Clock clock;

    public DeviceService(KnownDeviceRepository repository, MongoTemplate mongo,
                         SecurityProperties properties, Clock clock) {
        this.repository = repository;
        this.mongo = mongo;
        this.properties = properties;
        this.clock = clock;
    }

    public Recognition recognize(User user, ClientContext ctx, DeviceInfo info, GeoLocation geo,
                                 boolean accountHasSignedInBefore) {
        Instant now = Instant.now(clock);

        if (ctx.deviceToken() != null) {
            String hash = Tokens.sha256Hex(ctx.deviceToken());
            Optional<KnownDevice> existing = repository.findByUserIdAndTokenHash(user.getId(), hash);
            if (existing.isPresent()) {
                KnownDevice device = existing.get();
                touch(device, ctx, info, geo, now);
                return new Recognition(device, false, false, null);
            }
        }

        boolean firstDevice = !accountHasSignedInBefore && repository.countByUserId(user.getId()) == 0;
        enforceDeviceCap(user.getId());

        String rawToken = Tokens.newToken();
        KnownDevice device = new KnownDevice();
        device.setUserId(user.getId());
        device.setTokenHash(Tokens.sha256Hex(rawToken));
        device.setLabel(info.label());
        device.setDeviceType(info.deviceType());
        device.setOs(info.os());
        device.setBrowser(info.browser());
        device.setFirstSeenAt(now);
        device.setLastSeenAt(now);
        device.setLastIp(ctx.ip());
        device.setLastCountry(geo.country());
        device.setLastRegion(geo.region());
        device.setLastCity(geo.city());
        device.setSignInCount(1);
        device = repository.save(device);
        return new Recognition(device, true, firstDevice, rawToken);
    }

    private void touch(KnownDevice device, ClientContext ctx, DeviceInfo info, GeoLocation geo, Instant now) {
        Update update = new Update()
                .set("lastSeenAt", now)
                .set("lastIp", ctx.ip())
                .set("label", info.label())
                .set("deviceType", info.deviceType())
                .set("os", info.os())
                .set("browser", info.browser())
                .set("lastCountry", geo.country())
                .set("lastRegion", geo.region())
                .set("lastCity", geo.city())
                .inc("signInCount", 1);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(device.getId())), update, KnownDevice.class);
        device.setLastSeenAt(now);
        device.setLastIp(ctx.ip());
        device.setLabel(info.label());
        device.setSignInCount(device.getSignInCount() + 1);
    }

    /** Keeps the list bounded: when the cap is reached the least recently used untrusted device is dropped. */
    private void enforceDeviceCap(String userId) {
        int cap = Math.max(5, properties.getMaxDevicesPerUser());
        if (repository.countByUserId(userId) < cap) {
            return;
        }
        List<KnownDevice> candidates = repository.findByUserIdAndTrustedFalseOrderByLastSeenAtAsc(userId);
        if (!candidates.isEmpty()) {
            repository.deleteById(candidates.get(0).getId());
        }
    }

    public List<KnownDevice> list(String userId) {
        return repository.findByUserIdOrderByLastSeenAtDesc(userId);
    }

    public Optional<KnownDevice> find(String deviceId, String userId) {
        return repository.findByIdAndUserId(deviceId, userId);
    }

    public Optional<KnownDevice> findByToken(String userId, String rawToken) {
        if (rawToken == null) {
            return Optional.empty();
        }
        return repository.findByUserIdAndTokenHash(userId, Tokens.sha256Hex(rawToken));
    }

    public void markAlerted(KnownDevice device) {
        Instant now = Instant.now(clock);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(device.getId())),
                new Update().set("lastAlertAt", now), KnownDevice.class);
        device.setLastAlertAt(now);
    }

    public void trust(KnownDevice device) {
        Instant now = Instant.now(clock);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(device.getId())),
                new Update().set("trusted", true).set("trustedAt", now).unset("trustRevokedAt"), KnownDevice.class);
        device.setTrusted(true);
        device.setTrustedAt(now);
        device.setTrustRevokedAt(null);
    }

    public void revokeTrust(KnownDevice device) {
        Instant now = Instant.now(clock);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(device.getId())),
                new Update().set("trusted", false).set("trustRevokedAt", now), KnownDevice.class);
        device.setTrusted(false);
        device.setTrustRevokedAt(now);
    }

    /**
     * Invalidates the remembered-device credential: the stored hash is replaced, so the old
     * cookie or header no longer matches and the next sign-in from that browser is treated as new.
     * Trust is removed too.
     */
    public void revokeCredential(KnownDevice device) {
        Instant now = Instant.now(clock);
        String burned = "revoked:" + Tokens.newToken();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(device.getId())),
                new Update().set("tokenHash", burned).set("trusted", false).set("trustRevokedAt", now), KnownDevice.class);
        device.setTokenHash(burned);
        device.setTrusted(false);
        device.setTrustRevokedAt(now);
    }

    public void remove(KnownDevice device) {
        repository.deleteById(device.getId());
    }
}
