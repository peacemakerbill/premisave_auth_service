package com.premisave.auth.service.security;

import com.premisave.auth.config.SecurityProperties;
import com.premisave.auth.entity.User;
import com.premisave.auth.entity.UserSession;
import com.premisave.auth.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Step-up check for sensitive security changes (for example trusting a device).
 *
 * A valid session is not enough on its own. The user proves themselves again with their
 * password; a session that was started with a social provider a few minutes ago counts as
 * freshly authenticated, because those accounts normally have no password to type.
 * Wrong passwords here feed the same throttle as sign-in, so this cannot be used to guess a password.
 */
@Service
public class ReauthService {

    private final PasswordEncoder passwordEncoder;
    private final LoginThrottleService throttle;
    private final SessionService sessions;
    private final SecurityProperties properties;

    public ReauthService(PasswordEncoder passwordEncoder, LoginThrottleService throttle,
                         SessionService sessions, SecurityProperties properties) {
        this.passwordEncoder = passwordEncoder;
        this.throttle = throttle;
        this.sessions = sessions;
        this.properties = properties;
    }

    public void require(User user, String sessionId, String currentPassword, ClientContext ctx) {
        if (currentPassword != null && !currentPassword.isBlank()) {
            throttle.assertAllowed(user.getEmail(), ctx.ip());
            if (passwordEncoder.matches(currentPassword, user.getPassword())) {
                return;
            }
            throttle.recordFailure(user.getEmail(), ctx.ip());
            throw new ApiException(HttpStatus.FORBIDDEN, "The password you entered is not correct.");
        }

        if (sessionId != null) {
            UserSession session = sessions.find(sessionId).orElse(null);
            if (session != null && session.getLoginMethod() != null && !session.getLoginMethod().isPasswordBased()
                    && session.getCreatedAt() != null
                    && session.getCreatedAt().isAfter(Instant.now().minus(Duration.ofMinutes(properties.getReauthWindowMinutes())))) {
                return;
            }
            if (session != null && session.getLoginMethod() != null && !session.getLoginMethod().isPasswordBased()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "For your security, please sign in again to continue.");
            }
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "Please confirm your password to continue.");
    }
}
