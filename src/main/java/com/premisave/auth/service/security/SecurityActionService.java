package com.premisave.auth.service.security;

import com.premisave.auth.config.SecurityProperties;
import com.premisave.auth.entity.KnownDevice;
import com.premisave.auth.entity.SecurityActionToken;
import com.premisave.auth.entity.User;
import com.premisave.auth.entity.UserSession;
import com.premisave.auth.enums.ActionTokenType;
import com.premisave.auth.enums.Role;
import com.premisave.auth.enums.SecurityEventType;
import com.premisave.auth.enums.SecuritySeverity;
import com.premisave.auth.exception.ApiException;
import com.premisave.auth.repository.SecurityActionTokenRepository;
import com.premisave.auth.repository.UserRepository;
import com.premisave.auth.service.security.EmailTemplateRenderer.Row;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Short-lived single-use tokens that travel by email: the "this was not me" link in sign-in
 * alerts and the administrator invitation. Only the SHA-256 hash is stored. Taking a token
 * is one atomic update, so a link can be used once even if two requests race.
 *
 * Opening a link never changes anything. The frontend first calls inspect (read only) and
 * the owner then confirms explicitly, which also protects against email scanners that
 * fetch every link in a message.
 */
@Service
public class SecurityActionService {

    private static final String INVALID = "This link is invalid or has expired.";

    public record Preview(Instant signedInAt, String device, String approximateLocation,
                          String maskedIp, String method, boolean sessionStillActive) {
    }

    public record ConfirmResult(boolean sessionRevoked, boolean deviceTrustRemoved,
                                boolean passwordResetEmailSent, String securityUrl, String message) {
    }

    public record Created(String rawToken, Instant expiresAt) {
    }

    private final SecurityActionTokenRepository tokens;
    private final MongoTemplate mongo;
    private final UserRepository users;
    private final SessionService sessions;
    private final DeviceService devices;
    private final PasswordResetService passwordReset;
    private final SecurityAuditService audit;
    private final SecurityNotificationService notifications;
    private final SecurityProperties properties;
    private final Clock clock;

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public SecurityActionService(SecurityActionTokenRepository tokens, MongoTemplate mongo, UserRepository users,
                                 SessionService sessions, DeviceService devices, PasswordResetService passwordReset,
                                 SecurityAuditService audit, SecurityNotificationService notifications,
                                 SecurityProperties properties, Clock clock) {
        this.tokens = tokens;
        this.mongo = mongo;
        this.users = users;
        this.sessions = sessions;
        this.devices = devices;
        this.passwordReset = passwordReset;
        this.audit = audit;
        this.notifications = notifications;
        this.properties = properties;
        this.clock = clock;
    }

    public String frontendBase() {
        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    /** Link target for the "this was not me" button; the frontend route reads ?token= and calls inspect/confirm. */
    public String reportUrl(String rawToken) {
        return frontendBase() + "/security/report?token=" + rawToken;
    }

    public String invitationUrl(String rawToken) {
        return frontendBase() + "/admin-invitation?token=" + rawToken;
    }

    public String createReportToken(String userId, String sessionId, String deviceId) {
        Instant now = Instant.now(clock);
        return store(ActionTokenType.REPORT_UNAUTHORIZED_SIGN_IN, userId, sessionId, deviceId,
                now.plus(Duration.ofMinutes(Math.max(5, properties.getActionTokenTtlMinutes())))).rawToken();
    }

    public Created createInvitation(String userId) {
        Instant now = Instant.now(clock);
        return store(ActionTokenType.ADMIN_INVITATION, userId, null, null,
                now.plus(Duration.ofHours(Math.max(1, properties.getInvitationTtlHours()))));
    }

    public boolean hasActiveInvitation(String userId) {
        return tokens.existsByUserIdAndTypeAndUsedAtIsNullAndExpiresAtAfter(userId, ActionTokenType.ADMIN_INVITATION, Instant.now(clock));
    }

    private Created store(ActionTokenType type, String userId, String sessionId, String deviceId, Instant expiresAt) {
        String raw = Tokens.newToken();
        SecurityActionToken token = new SecurityActionToken();
        token.setTokenHash(Tokens.sha256Hex(raw));
        token.setType(type);
        token.setUserId(userId);
        token.setSessionId(sessionId);
        token.setDeviceId(deviceId);
        token.setCreatedAt(Instant.now(clock));
        token.setExpiresAt(expiresAt);
        tokens.save(token);
        return new Created(raw, expiresAt);
    }

    /** Read-only description of the reported sign-in. Does not consume the token. */
    public Preview inspect(String rawToken) {
        SecurityActionToken token = findUsable(rawToken, ActionTokenType.REPORT_UNAUTHORIZED_SIGN_IN)
                .orElseThrow(() -> ApiException.badRequest(INVALID));
        UserSession session = token.getSessionId() == null ? null : sessions.find(token.getSessionId()).orElse(null);
        if (session == null) {
            return new Preview(token.getCreatedAt(), null, null, null, null, false);
        }
        GeoLocation geo = new GeoLocation(session.getCountry(), session.getRegion(), session.getCity());
        return new Preview(session.getCreatedAt(), session.getDeviceLabel(), geo.describe(), maskIp(session.getIpAddress()),
                session.getLoginMethod() == null ? null : session.getLoginMethod().label(),
                session.isActive(Instant.now(clock)));
    }

    /**
     * The owner confirms "this was not me": the reported session is revoked at once, every other
     * session of the same device too, the device loses its trust and its remembered-device
     * credential, and the owner is told. The password is not changed here; if asked, the
     * normal password-reset email is sent so the owner chooses a new one themselves.
     */
    public ConfirmResult confirmReport(String rawToken, boolean requestPasswordReset, ClientContext ctx) {
        SecurityActionToken token = take(rawToken, ActionTokenType.REPORT_UNAUTHORIZED_SIGN_IN)
                .orElseThrow(() -> ApiException.badRequest(INVALID));
        User user = users.findById(token.getUserId()).orElseThrow(() -> ApiException.badRequest(INVALID));

        boolean revoked = false;
        if (token.getSessionId() != null) {
            revoked = sessions.revoke(token.getSessionId(), user.getId(), "REPORTED_UNAUTHORIZED", user.getId());
        }

        boolean trustRemoved = false;
        if (token.getDeviceId() != null) {
            sessions.revokeForDevice(user.getId(), token.getDeviceId(), "REPORTED_UNAUTHORIZED", user.getId());
            Optional<KnownDevice> device = devices.find(token.getDeviceId(), user.getId());
            if (device.isPresent()) {
                trustRemoved = device.get().isTrusted();
                devices.revokeCredential(device.get());
            }
        }

        boolean resetSent = false;
        if (requestPasswordReset) {
            passwordReset.sendResetFor(user);
            resetSent = true;
        }

        boolean admin = user.getRole() == Role.ADMIN;
        audit.event(SecurityEventType.UNAUTHORIZED_SIGN_IN_REPORTED)
                .severity(SecuritySeverity.CRITICAL)
                .user(user.getId())
                .actor(user.getId())
                .client(ctx)
                .session(token.getSessionId())
                .device(token.getDeviceId())
                .detail("sessionRevoked", revoked)
                .detail("trustRemoved", trustRemoved)
                .detail("passwordResetRequested", resetSent)
                .write();
        if (admin) {
            audit.event(SecurityEventType.ADMIN_SUSPICIOUS_ACTIVITY)
                    .severity(SecuritySeverity.CRITICAL)
                    .user(user.getId())
                    .client(ctx)
                    .reason("Administrator reported an unauthorized sign-in")
                    .write();
        }

        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Session ended", revoked ? "Yes" : "It had already ended"));
        rows.add(new Row("Device trust removed", "Yes"));
        rows.add(new Row("Password reset email", resetSent ? "Sent" : "Not requested"));
        notifications.sendNotice(user.getId(), user.getEmail(), user.getFirstName(),
                "Unauthorized sign-in reported on your Premisave account",
                "We ended the reported sign-in",
                "You told us a recent sign-in to your account was not yours. We ended that session and removed the device's remembered status.",
                rows,
                "Your password has not been changed. We strongly recommend choosing a new one and reviewing your other active sessions.",
                "Review account security", notifications.securityUrl());

        return new ConfirmResult(revoked, trustRemoved, resetSent, notifications.securityUrl(),
                "The reported sign-in was ended. Please change your password and review your other sessions.");
    }

    /** Looks a token up without using it. */
    Optional<SecurityActionToken> findUsable(String rawToken, ActionTokenType type) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(Tokens.sha256Hex(rawToken.trim()))
                .filter(t -> t.getType() == type && t.getUsedAt() == null && t.getExpiresAt().isAfter(Instant.now(clock)));
    }

    /** Marks a token used in one atomic step. Empty when unknown, wrong type, expired or already used. */
    public Optional<SecurityActionToken> take(String rawToken, ActionTokenType type) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
            return Optional.empty();
        }
        Instant now = Instant.now(clock);
        Query query = Query.query(Criteria.where("tokenHash").is(Tokens.sha256Hex(rawToken.trim()))
                .and("type").is(type.name())
                .and("usedAt").is(null)
                .and("expiresAt").gt(now));
        SecurityActionToken taken = mongo.findAndModify(query, new Update().set("usedAt", now),
                FindAndModifyOptions.options().returnNew(true), SecurityActionToken.class);
        return Optional.ofNullable(taken);
    }

    static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        if (ip.contains(".")) {
            int last = ip.lastIndexOf('.');
            return last > 0 ? ip.substring(0, last) + ".x" : ip;
        }
        String[] parts = ip.split(":");
        return parts.length >= 2 ? parts[0] + ":" + parts[1] + "::" : ip;
    }
}
