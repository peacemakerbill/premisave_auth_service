package com.premisave.auth.service.security;

import com.mongodb.client.result.UpdateResult;
import com.premisave.auth.entity.User;
import com.premisave.auth.enums.Role;
import com.premisave.auth.enums.SecurityEventType;
import com.premisave.auth.enums.SecuritySeverity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * The only place that grants the ADMIN role by configuration.
 *
 * The role is decided here on the server from the stored, verified email. Nothing the
 * client sends (request body, JWT claims, local storage) is consulted, and every path that
 * can produce a verified account (email verification, sign-in, social login, invitation,
 * startup provisioning) calls grantIfEligible, so there is no alternative route around it.
 *
 * Role revocation policy: removing an email from the configuration never removes the ADMIN
 * role. Demotion is an explicit action by an administrator (PUT /admin/users/change-role/{id}), which
 * is written to the audit trail with actor, target, reason and time.
 */
@Slf4j
@Service
public class AdminAccessService {

    private final MongoTemplate mongo;
    private final AdminEmailPolicy policy;
    private final SecurityAuditService audit;
    private final SecurityNotificationService notifications;
    private final StringRedisTemplate redis;
    private final Clock clock;

    public AdminAccessService(MongoTemplate mongo, AdminEmailPolicy policy, SecurityAuditService audit,
                              SecurityNotificationService notifications, StringRedisTemplate redis, Clock clock) {
        this.mongo = mongo;
        this.policy = policy;
        this.audit = audit;
        this.notifications = notifications;
        this.redis = redis;
        this.clock = clock;
    }

    /**
     * Grants ADMIN if the user's verified, active email is on the configured lists.
     *
     * @param source where the check ran, for the audit trail (EMAIL_VERIFICATION, SIGN_IN, SOCIAL_LOGIN ...)
     * @return true if the role changed in this call
     */
    public boolean grantIfEligible(User user, String source) {
        if (user == null || user.getId() == null || user.getEmail() == null) {
            return false;
        }
        if (user.getRole() == Role.ADMIN || !policy.isConfiguredAdmin(user.getEmail())) {
            return false;
        }
        if (!user.isVerified() || !user.isActive() || user.isArchived()) {
            return false;
        }
        try {
            return promote(user, "SYSTEM", "Email address is listed in the administrator configuration (" + source + ")", source);
        } catch (RuntimeException e) {
            // Never block sign-in or verification because the role update failed; it is retried next time.
            log.error("Could not apply configured admin role: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /** Sets the role with a conditional update, so concurrent calls promote and notify exactly once. */
    boolean promote(User user, String actor, String reason, String source) {
        Role previous = user.getRole();
        Query query = Query.query(Criteria.where("_id").is(user.getId()).and("role").ne(Role.ADMIN.name()));
        Update update = new Update().set("role", Role.ADMIN.name()).set("updatedAt", LocalDateTime.now(clock));
        UpdateResult result = mongo.updateFirst(query, update, User.class);
        if (result.getModifiedCount() == 0) {
            return false;
        }

        user.setRole(Role.ADMIN);
        try {
            redis.delete("user:" + user.getId());
        } catch (RuntimeException ignored) {
            // cache only; the role is always read from the database per request
        }

        audit.event(SecurityEventType.ADMIN_ROLE_ASSIGNED)
                .severity(SecuritySeverity.WARNING)
                .user(user.getId())
                .actor(actor)
                .reason(reason)
                .detail("previousRole", previous == null ? "NONE" : previous.name())
                .detail("newRole", Role.ADMIN.name())
                .detail("source", source)
                .write();
        try {
            notifications.sendAdminRoleAssigned(user.getId(), user.getEmail(), user.getFirstName(), reason, Instant.now(clock));
        } catch (RuntimeException e) {
            // The role is already stored; a mail problem must not make the grant look like it failed.
            log.warn("Admin role notice could not be queued: {}", e.getClass().getSimpleName());
        }
        log.info("ADMIN role assigned to user {} via {}", user.getId(), source);
        return true;
    }

    /** Case-insensitive lookup, because older accounts may have been stored with mixed-case emails. */
    public User findByEmailIgnoreCase(String email) {
        Query query = Query.query(Criteria.where("email").regex("^" + Pattern.quote(email) + "$", "i"));
        return mongo.findOne(query, User.class);
    }
}
