package com.premisave.auth.service.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed counters. If Redis cannot be reached the throttle fails open (and logs),
 * because refusing every sign-in during a cache outage would be a worse outcome.
 */
@Slf4j
@Component
public class RedisThrottleStore implements ThrottleStore {

    private final StringRedisTemplate redis;

    public RedisThrottleStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long increment(String key, Duration ttlIfNew) {
        try {
            Long value = redis.opsForValue().increment(key);
            if (value != null && value == 1L) {
                redis.expire(key, ttlIfNew);
            }
            return value == null ? 0 : value;
        } catch (RuntimeException e) {
            log.warn("Login throttle counter unavailable: {}", e.getClass().getSimpleName());
            return 0;
        }
    }

    @Override
    public long addToSet(String key, String member, Duration ttlIfNew) {
        try {
            redis.opsForSet().add(key, member);
            Long size = redis.opsForSet().size(key);
            if (size != null && size == 1L) {
                redis.expire(key, ttlIfNew);
            }
            return size == null ? 0 : size;
        } catch (RuntimeException e) {
            log.warn("Login throttle set unavailable: {}", e.getClass().getSimpleName());
            return 0;
        }
    }

    @Override
    public void mark(String key, Duration ttl) {
        try {
            redis.opsForValue().set(key, "1", ttl);
        } catch (RuntimeException e) {
            log.warn("Login throttle marker unavailable: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public Optional<Duration> remaining(String key) {
        try {
            Long seconds = redis.getExpire(key, TimeUnit.SECONDS);
            if (seconds == null || seconds <= 0) {
                return Optional.empty();
            }
            return Optional.of(Duration.ofSeconds(seconds));
        } catch (RuntimeException e) {
            log.warn("Login throttle check unavailable: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException e) {
            log.warn("Login throttle delete unavailable: {}", e.getClass().getSimpleName());
        }
    }
}
