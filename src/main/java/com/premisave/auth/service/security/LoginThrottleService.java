package com.premisave.auth.service.security;

import com.premisave.auth.config.SecurityProperties;
import com.premisave.auth.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Brute force and credential stuffing protection.
 *
 * Counters are keyed by a hash of the typed email whether or not an account exists, and the
 * refusal message is identical in both cases, so throttling reveals nothing about accounts.
 * Locks are temporary and grow progressively (base, double, double ...) up to a cap; they
 * expire on their own, so a legitimate user is never blocked permanently.
 */
@Service
public class LoginThrottleService {

    private static final Duration LEVEL_MEMORY = Duration.ofHours(24);

    private final ThrottleStore store;
    private final SecurityProperties properties;

    public LoginThrottleService(ThrottleStore store, SecurityProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    /** Throws 429 when the account or the address is currently locked. */
    public void assertAllowed(String email, String ip) {
        Optional<Duration> account = store.remaining(accountLock(email));
        Optional<Duration> address = ip == null ? Optional.empty() : store.remaining(ipLock(ip));
        Duration wait = null;
        if (account.isPresent()) {
            wait = account.get();
        }
        if (address.isPresent() && (wait == null || address.get().compareTo(wait) > 0)) {
            wait = address.get();
        }
        if (wait != null) {
            long minutes = Math.max(1, (wait.getSeconds() + 59) / 60);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many sign-in attempts. Please try again in " + minutes + " minute" + (minutes == 1 ? "" : "s") + ".");
        }
    }

    /** Records a failed attempt. Returns the length of the lock when this failure started one on the account. */
    public Optional<Duration> recordFailure(String email, String ip) {
        Duration window = Duration.ofMinutes(properties.getLoginFailureWindowMinutes());
        Duration newLock = null;

        long failures = store.increment(accountFailures(email), window);
        if (failures >= properties.getLoginMaxFailures()) {
            long level = store.increment(accountLevel(email), LEVEL_MEMORY);
            newLock = lockDuration(level);
            store.mark(accountLock(email), newLock);
            store.delete(accountFailures(email));
        }

        if (ip != null) {
            Duration ipWindow = Duration.ofMinutes(properties.getIpWindowMinutes());
            long ipFailures = store.increment(ipFailures(ip), ipWindow);
            long accounts = store.addToSet(ipAccounts(ip), Tokens.sha256Hex(normalize(email)).substring(0, 16), ipWindow);
            if (ipFailures >= properties.getIpMaxFailures() || accounts >= properties.getIpMaxDistinctAccounts()) {
                store.mark(ipLock(ip), Duration.ofMinutes(properties.getLoginLockoutMinutes()));
                store.delete(ipFailures(ip));
                store.delete(ipAccounts(ip));
            }
        }
        return Optional.ofNullable(newLock);
    }

    /** A good sign-in clears the failure counter (the progressive level decays by itself). */
    public void recordSuccess(String email) {
        store.delete(accountFailures(email));
    }

    Duration lockDuration(long level) {
        long base = properties.getLoginLockoutMinutes();
        long cap = Math.max(base, properties.getLoginMaxLockoutMinutes());
        long minutes = base;
        for (long i = 1; i < level && minutes < cap; i++) {
            minutes = Math.min(cap, minutes * 2);
        }
        return Duration.ofMinutes(minutes);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String emailHash(String email) {
        return Tokens.sha256Hex(normalize(email)).substring(0, 32);
    }

    private static String accountFailures(String email) {
        return "sec:fail:acct:" + emailHash(email);
    }

    private static String accountLevel(String email) {
        return "sec:level:acct:" + emailHash(email);
    }

    private static String accountLock(String email) {
        return "sec:lock:acct:" + emailHash(email);
    }

    private static String ipFailures(String ip) {
        return "sec:fail:ip:" + ip;
    }

    private static String ipAccounts(String ip) {
        return "sec:accts:ip:" + ip;
    }

    private static String ipLock(String ip) {
        return "sec:lock:ip:" + ip;
    }
}
