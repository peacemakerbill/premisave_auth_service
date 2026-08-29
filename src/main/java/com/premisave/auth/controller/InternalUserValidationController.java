package com.premisave.auth.controller;

import com.premisave.auth.dto.UserDetailsInternalResponse;
import com.premisave.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Internal-only endpoints for cross-service user validation/lookup.
 *
 * Protected by ApiKeyFilter (X-API-Key header) — not exposed to end
 * users. Called by the wallet service: validate-email during M-Pesa C2B
 * validation, and {email}/details to resolve a real name for transfer/
 * payment notification emails (see AuthServiceClient in the wallet
 * service).
 *
 * SECURITY NOTE on why {email}/details lives here specifically, under
 * /internal/users, rather than at /auth/users/{email}/details (where the
 * wallet service's AuthServiceClient interface originally — and
 * incorrectly — pointed before this fix): AuthController's own /auth/**
 * path space has to allow unauthenticated requests (signup, signin,
 * etc.), so anything placed there risks being reachable with no auth at
 * all. Every genuinely internal, service-to-service endpoint in this
 * controller shares the same ApiKeyFilter protection and the same
 * /internal/users prefix — deliberately, so there's one consistent place
 * cross-service lookups live, not one path convention per endpoint.
 */
@Slf4j
@RestController
@RequestMapping("/internal/users")
public class InternalUserValidationController {

    private final UserRepository userRepository;

    public InternalUserValidationController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Validates whether an email belongs to an active, verified, non-archived user.
     *
     * GET /internal/users/validate-email/{email}
     * Header: X-API-Key: <secret>
     *
     * Response:
     *   200 { "valid": true,  "reason": null }          — account exists and is active
     *   200 { "valid": false, "reason": "NOT_FOUND" }   — no account with this email
     *   200 { "valid": false, "reason": "INACTIVE" }    — account deactivated
     *   200 { "valid": false, "reason": "UNVERIFIED" }  — account not email-verified
     *   200 { "valid": false, "reason": "ARCHIVED" }    — account archived
     *
     * Always returns 200 — callers check the "valid" field.
     * This keeps Feign error handling simple and avoids 404/403 noise in logs.
     */
    @GetMapping("/validate-email/{email}")
    public ResponseEntity<Map<String, Object>> validateEmail(@PathVariable String email) {
        log.debug("Internal email validation requested for: {}", email);

        if (email == null || email.isBlank() || !email.contains("@")) {
            return validationResponse(false, "INVALID_FORMAT");
        }

        return userRepository.findByEmail(email.trim().toLowerCase())
                .map(user -> {
                    if (user.isArchived()) {
                        log.warn("C2B validation: archived account — {}", email);
                        return validationResponse(false, "ARCHIVED");
                    }
                    if (!user.isActive()) {
                        log.warn("C2B validation: inactive account — {}", email);
                        return validationResponse(false, "INACTIVE");
                    }
                    if (!user.isVerified()) {
                        log.warn("C2B validation: unverified account — {}", email);
                        return validationResponse(false, "UNVERIFIED");
                    }
                    log.debug("C2B validation: valid account — {}", email);
                    return validationResponse(true, "");
                })
                .orElseGet(() -> {
                    log.warn("C2B validation: no account found for — {}", email);
                    return validationResponse(false, "NOT_FOUND");
                });
    }

    /**
     * Returns full user details for cross-service consumption — id,
     * email, role, active/verified status, and name fields (firstName/
     * middleName/lastName plus a computed fullName, same pattern as
     * UserDto.getFullName()). Currently used by the wallet service to
     * resolve a real name for transfer/payment notification emails.
     *
     * GET /internal/users/{email}/details
     * Header: X-API-Key: <secret>
     *
     * Returns 404 if no user exists with this email — the wallet
     * service's own AuthServiceClient declares this call as
     * Optional<UserDetailsDto>, so a 404 here correctly resolves to an
     * empty Optional on the caller's side via Feign's standard handling,
     * rather than needing a wrapped "found" boolean the way
     * validate-email above does.
     */
    @GetMapping("/{email}/details")
    public ResponseEntity<UserDetailsInternalResponse> getUserDetails(@PathVariable String email) {
        log.debug("Internal user-details lookup requested for: {}", email);

        if (email == null || email.isBlank() || !email.contains("@")) {
            return ResponseEntity.badRequest().build();
        }

        return userRepository.findByEmail(email.trim().toLowerCase())
                .map(user -> ResponseEntity.ok(new UserDetailsInternalResponse(
                        user.getId(), user.getEmail(), user.getRole(), user.isActive(), user.isVerified(),
                        user.getFirstName(), user.getMiddleName(), user.getLastName())))
                .orElseGet(() -> {
                    log.debug("User-details lookup: no account found for — {}", email);
                    return ResponseEntity.notFound().build();
                });
    }

    private ResponseEntity<Map<String, Object>> validationResponse(boolean valid, String reason) {
        return ResponseEntity.ok(Map.<String, Object>of("valid", valid, "reason", reason));
    }
}