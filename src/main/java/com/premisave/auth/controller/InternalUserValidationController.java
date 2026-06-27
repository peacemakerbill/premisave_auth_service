package com.premisave.auth.controller;

import com.premisave.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Internal-only endpoint for cross-service user validation.
 *
 * Protected by ApiKeyFilter (X-API-Key header) — not exposed to end users.
 * Called by the wallet service during M-Pesa C2B validation to confirm
 * that the email typed at the M-Pesa prompt belongs to a real, active account.
 *
 * Safaricom requires a response within 8 seconds — this endpoint is designed
 * to be as fast as possible (single indexed DB lookup, no heavy computation).
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

    private ResponseEntity<Map<String, Object>> validationResponse(boolean valid, String reason) {
        return ResponseEntity.ok(Map.<String, Object>of("valid", valid, "reason", reason));
    }
}