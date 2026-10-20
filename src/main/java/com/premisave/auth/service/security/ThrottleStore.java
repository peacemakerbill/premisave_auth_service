package com.premisave.auth.service.security;

import java.time.Duration;
import java.util.Optional;

/** The few counter operations the login throttle needs. Redis in production, a map in tests. */
public interface ThrottleStore {

    /** Adds one to the counter and starts its expiry when the counter is new. Returns the new value. */
    long increment(String key, Duration ttlIfNew);

    /** Adds a member to a set (expiry starts when the set is new) and returns the set size. */
    long addToSet(String key, String member, Duration ttlIfNew);

    /** Stores a marker that expires after ttl. */
    void mark(String key, Duration ttl);

    /** Time left on a marker, or empty if it is not set. */
    Optional<Duration> remaining(String key);

    void delete(String key);
}
