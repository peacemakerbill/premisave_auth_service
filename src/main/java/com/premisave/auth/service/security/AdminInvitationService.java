package com.premisave.auth.service.security;

import com.premisave.auth.entity.SecurityActionToken;
import com.premisave.auth.entity.User;
import com.premisave.auth.enums.ActionTokenType;
import com.premisave.auth.enums.Language;
import com.premisave.auth.enums.Role;
import com.premisave.auth.enums.SecurityEventType;
import com.premisave.auth.enums.SecuritySeverity;
import com.premisave.auth.exception.ApiException;
import com.premisave.auth.repository.UserRepository;
import com.premisave.auth.service.UsernameService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Creates administrator accounts that do not exist yet, safely.
 *
 * No password is ever chosen for such an account. It is created unverified, with a random
 * password nobody knows, and the owner of the address receives a single-use link to choose
 * their own password. Until that link is used the account cannot sign in, and nobody else can
 * register that address because the account already exists.
 */
@Slf4j
@Service
public class AdminInvitationService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SecurityActionService actions;
    private final SecurityAuditService audit;
    private final SecurityNotificationService notifications;
    private final AdminAccessService adminAccess;
    private final UsernameService usernameService;
    private final StringRedisTemplate redis;

    public AdminInvitationService(UserRepository users, PasswordEncoder passwordEncoder, SecurityActionService actions,
                                  SecurityAuditService audit, SecurityNotificationService notifications,
                                  AdminAccessService adminAccess, UsernameService usernameService,
                                  StringRedisTemplate redis) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.actions = actions;
        this.audit = audit;
        this.notifications = notifications;
        this.adminAccess = adminAccess;
        this.usernameService = usernameService;
        this.redis = redis;
    }

    /** Creates the pending administrator and emails the invitation. Returns the new user. */
    public User createPendingAdmin(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName(displayNameFrom(email));
        user.setRole(Role.ADMIN);
        user.setActive(true);
        user.setVerified(false);
        user.setArchived(false);
        user.setLanguage(Language.ENGLISH);
        // Random and discarded: nobody knows it, so there is no default credential to steal.
        user.setPassword(passwordEncoder.encode(Tokens.newToken()));
        user = users.save(user);

        try {
            usernameService.assignInBackground(user.getId());
        } catch (RuntimeException e) {
            log.debug("Username assignment skipped: {}", e.getClass().getSimpleName());
        }

        audit.event(SecurityEventType.ADMIN_PROVISIONED)
                .severity(SecuritySeverity.WARNING)
                .user(user.getId())
                .reason("Administrator account pre-provisioned from INITIAL_ADMIN_EMAILS")
                .write();
        sendInvitation(user);
        return user;
    }

    /** Issues a fresh invitation for a pending administrator whose earlier link expired. */
    public void reinvite(User user) {
        sendInvitation(user);
    }

    private void sendInvitation(User user) {
        SecurityActionService.Created created = actions.createInvitation(user.getId());
        try {
            notifications.sendAdminProvisioned(user.getId(), user.getEmail(), actions.invitationUrl(created.rawToken()), created.expiresAt());
        } catch (RuntimeException e) {
            // The account and its token exist; the invitation can be re-sent at the next start.
            log.warn("Administrator invitation could not be queued: {}", e.getClass().getSimpleName());
        }
    }

    /** Uses the invitation: sets the owner's chosen password, verifies the email and activates the account. */
    public void accept(String rawToken, String newPassword, String confirmPassword, ClientContext ctx) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw ApiException.badRequest("Password must be at least " + MIN_PASSWORD_LENGTH + " characters long.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw ApiException.badRequest("Passwords do not match.");
        }

        SecurityActionToken token = actions.take(rawToken, ActionTokenType.ADMIN_INVITATION)
                .orElseThrow(() -> ApiException.badRequest("This invitation link is invalid or has expired."));
        User user = users.findById(token.getUserId())
                .orElseThrow(() -> ApiException.badRequest("This invitation link is invalid or has expired."));

        if (newPassword.equalsIgnoreCase(user.getEmail())) {
            throw ApiException.badRequest("Password must not be your email address.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setVerified(true);
        user.setActive(true);
        users.save(user);
        try {
            redis.delete("user:" + user.getId());
        } catch (RuntimeException ignored) {
            // cache only
        }

        audit.event(SecurityEventType.ADMIN_INVITATION_ACCEPTED)
                .severity(SecuritySeverity.WARNING)
                .user(user.getId())
                .actor(user.getId())
                .client(ctx)
                .write();

        // Covers the case where the role was changed while the invitation was outstanding.
        adminAccess.grantIfEligible(user, "INVITATION");
    }

    static String displayNameFrom(String email) {
        String local = email.substring(0, Math.max(0, email.indexOf('@')));
        String cleaned = local.replaceAll("[^A-Za-z]+", " ").trim();
        if (cleaned.isEmpty()) {
            return "Administrator";
        }
        String first = cleaned.split(" ")[0];
        return first.substring(0, 1).toUpperCase(Locale.ROOT) + first.substring(1).toLowerCase(Locale.ROOT);
    }
}
