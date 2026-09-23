package com.premisave.auth.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.lettuce.core.RedisClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RateLimiterConfig {

    @Value("${rate-limit.requests-per-minute}")
    private int requestsPerMinute;

    @Bean
    Bucket rateLimiterBucket(RedisClient redisClient) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillIntervally(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(bandwidth).build();
    }

    @Bean
    RedisClient redisClient(@Value("${spring.data.redis.host}") String host,
                                   @Value("${spring.data.redis.port}") int port) {
        return RedisClient.create("redis://" + host + ":" + port);
    }
}