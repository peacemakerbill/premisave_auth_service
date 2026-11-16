package com.premisave.auth.service.security;

import com.premisave.auth.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Limits for "send me a new activation email".
 *
 * The endpoint is public, so without limits it could be used to fill someone's inbox. Every limit is
 * counted by a hash of the typed address whether or not an account exists, and the refusal is the same
 * in both cases, so a refusal reveals nothing about which addresses are registered.
 *
 *  - one request per address per minute
 *  - a few requests per address per hour
 *  - a larger number per client address per hour
 *
 * The counters live in the same store as the sign-in throttle, which fails open if Redis is down.
 */
@Service
public class ActivationResendGuard {

    private static final Duration COOLDOWN = Duration.ofSeconds(60);
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final int MAX_PER_ADDRESS = 5;
    private static final int MAX_PER_CLIENT = 20;

    private final ThrottleStore store;

    public ActivationResendGuard(ThrottleStore store) {
        this.store = store;
    }

    /** Throws 429 when this address or client has asked too often. Call before looking the account up. */
    public void assertAllowed(String email, String ip) {
        String hash = hash(email);

        Optional<Duration> cooling = store.remaining("resend-activation:wait:" + hash);
        if (cooling.isPresent()) {
            long seconds = Math.max(1, cooling.get().getSeconds());
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait " + seconds + " second" + (seconds == 1 ? "" : "s") + " before asking for another email.");
        }
        if (store.increment("resend-activation:addr:" + hash, WINDOW) > MAX_PER_ADDRESS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests for this address. Please try again in an hour.");
        }
        if (ip != null && store.increment("resend-activation:ip:" + Tokens.sha256Hex(ip).substring(0, 16), WINDOW) > MAX_PER_CLIENT) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests from this device. Please try again in an hour.");
        }
        store.mark("resend-activation:wait:" + hash, COOLDOWN);
    }

    /** True at most once an hour per account, so asking again and again cannot flood the owner with notices. */
    public boolean allowVerifiedNotice(String userId) {
        String key = "resend-activation:notice:" + userId;
        if (store.remaining(key).isPresent()) {
            return false;
        }
        store.mark(key, WINDOW);
        return true;
    }

    private static String hash(String email) {
        return Tokens.sha256Hex(email.trim().toLowerCase(Locale.ROOT)).substring(0, 32);
    }
}
