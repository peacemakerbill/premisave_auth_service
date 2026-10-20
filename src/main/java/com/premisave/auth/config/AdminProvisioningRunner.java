package com.premisave.auth.config;

import com.premisave.auth.entity.User;
import com.premisave.auth.enums.Role;
import com.premisave.auth.service.security.AdminAccessService;
import com.premisave.auth.service.security.AdminEmailPolicy;
import com.premisave.auth.service.security.AdminInvitationService;
import com.premisave.auth.service.security.SecurityActionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Replaces the old AdminUserInitializer, which created admin@premisave.com with a fixed password.
 *
 * For each address in INITIAL_ADMIN_EMAILS, at startup:
 *   - account exists, already ADMIN: nothing changes
 *   - account exists, verified and active: it is promoted to ADMIN
 *   - account exists, not verified yet: nothing now; it is promoted when the email is verified
 *   - ADMIN account created earlier, never activated, invitation expired: a new invitation is sent
 *   - no account: an unverified ADMIN account is created and the owner receives an invitation link
 *
 * It is idempotent (a second start changes nothing and sends nothing while an invitation is
 * still valid) and it never throws: a problem with one address is logged and the rest continue.
 */
@Slf4j
@Component
@Order(50)
public class AdminProvisioningRunner implements ApplicationRunner {

    private final AdminEmailPolicy policy;
    private final AdminAccessService adminAccess;
    private final AdminInvitationService invitations;
    private final SecurityActionService actions;

    public AdminProvisioningRunner(AdminEmailPolicy policy, AdminAccessService adminAccess,
                                   AdminInvitationService invitations, SecurityActionService actions) {
        this.policy = policy;
        this.adminAccess = adminAccess;
        this.invitations = invitations;
        this.actions = actions;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (policy.initialAdminEmails().isEmpty()) {
            log.info("No INITIAL_ADMIN_EMAILS configured; no administrator accounts are provisioned at startup.");
            return;
        }
        for (String email : policy.initialAdminEmails()) {
            try {
                provision(email);
            } catch (RuntimeException e) {
                log.error("Could not provision administrator {}: {}", AdminEmailPolicy.mask(email), e.getClass().getSimpleName());
            }
        }
    }

    void provision(String email) {
        String masked = AdminEmailPolicy.mask(email);
        User existing = adminAccess.findByEmailIgnoreCase(email);

        if (existing == null) {
            invitations.createPendingAdmin(email);
            log.info("Administrator {} provisioned; an invitation email was sent.", masked);
            return;
        }

        if (existing.getRole() == Role.ADMIN) {
            boolean pending = !existing.isVerified() && existing.getLastLoginAt() == null;
            if (pending && !actions.hasActiveInvitation(existing.getId())) {
                invitations.reinvite(existing);
                log.info("Administrator {} has not activated the account; a new invitation was sent.", masked);
            } else {
                log.info("Administrator {} already provisioned.", masked);
            }
            return;
        }

        if (adminAccess.grantIfEligible(existing, "STARTUP")) {
            log.info("Existing account {} promoted to ADMIN.", masked);
        } else {
            log.info("Account {} exists but is not verified or active yet; ADMIN is granted once it is.", masked);
        }
    }
}
