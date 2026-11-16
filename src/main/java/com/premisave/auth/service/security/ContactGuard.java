package com.premisave.auth.service.security;

import com.premisave.auth.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Limits for the public Contact us endpoint, so it cannot be used to flood the team's inbox or to
 * send mail to someone else's address.
 *
 *  - a short pause between two messages from the same address (also stops double taps)
 *  - a few messages per address per hour
 *  - a larger number per client address per hour
 *
 * Counters live in the same store as the sign-in throttle, which fails open if Redis is down.
 */
@Service
public class ContactGuard {

    private static final Duration COOLDOWN = Duration.ofSeconds(20);
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final int MAX_PER_ADDRESS = 5;
    private static final int MAX_PER_CLIENT = 10;

    private final ThrottleStore store;

    public ContactGuard(ThrottleStore store) {
        this.store = store;
    }

    /** Throws 429 with a message the customer can act on. */
    public void assertAllowed(String email, String ip) {
        String hash = Tokens.sha256Hex(email.trim().toLowerCase(Locale.ROOT)).substring(0, 32);

        Optional<Duration> cooling = store.remaining("contact:wait:" + hash);
        if (cooling.isPresent()) {
            long seconds = Math.max(1, cooling.get().getSeconds());
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait " + seconds + " second" + (seconds == 1 ? "" : "s") + " before sending another message.");
        }
        if (store.increment("contact:addr:" + hash, WINDOW) > MAX_PER_ADDRESS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "You have sent several messages in a short time. Please try again in an hour, "
                            + "or reply to the confirmation email we sent you.");
        }
        if (ip != null && store.increment("contact:ip:" + Tokens.sha256Hex(ip).substring(0, 16), WINDOW) > MAX_PER_CLIENT) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many messages from this device. Please try again in an hour.");
        }
        store.mark("contact:wait:" + hash, COOLDOWN);
    }
}
