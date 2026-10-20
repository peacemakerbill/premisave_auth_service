package com.premisave.auth.service.security;

import com.premisave.auth.entity.User;
import com.premisave.auth.enums.SecurityEventType;
import com.premisave.auth.enums.SecuritySeverity;
import org.springframework.stereotype.Component;

/**
 * What must happen after any password change, whoever made it: end the other sessions
 * (all of them for a reset), write the audit event and tell the account owner.
 */
@Component
public class PasswordChangeSecurityHandler {

    private final SessionService sessions;
    private final SecurityAuditService audit;
    private final SecurityNotificationService notifications;

    public PasswordChangeSecurityHandler(SessionService sessions, SecurityAuditService audit,
                                         SecurityNotificationService notifications) {
        this.sessions = sessions;
        this.audit = audit;
        this.notifications = notifications;
    }

    /**
     * @param keepSessionId the session that made the change and stays signed in; null ends every session
     * @param actor         id of the user or administrator who changed it, or SYSTEM
     * @param how           short description shown in the email, for example "Changed in your profile"
     * @return number of sessions that were ended
     */
    public int afterPasswordChange(User user, String keepSessionId, SecurityEventType type, String actor,
                                   String how, ClientContext ctx) {
        int ended = sessions.revokeAll(user.getId(), keepSessionId, "PASSWORD_CHANGED", actor);
        audit.event(type)
                .severity(SecuritySeverity.WARNING)
                .user(user.getId())
                .actor(actor)
                .client(ctx)
                .session(keepSessionId)
                .reason(how)
                .detail("sessionsEnded", ended)
                .write();
        if (ended > 0) {
            audit.event(SecurityEventType.ALL_SESSIONS_REVOKED)
                    .user(user.getId())
                    .actor(actor)
                    .client(ctx)
                    .reason("Password change")
                    .detail("sessionsEnded", ended)
                    .write();
        }
        notifications.sendPasswordChanged(user.getId(), user.getEmail(), user.getFirstName(),
                ctx == null ? null : ctx.ip(), ended, how);
        return ended;
    }
}
