package com.premisave.auth.config;

import com.premisave.auth.util.RateLimiterInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimiterInterceptor rateLimiterInterceptor;

    public WebConfig(RateLimiterInterceptor rateLimiterInterceptor) {
        this.rateLimiterInterceptor = rateLimiterInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        
        // Define paths that SHOULD be rate limited
        List<String> rateLimitedPaths = List.of(
            "/auth/signup",
            "/auth/signin",
            "/auth/reset-password",
            "/auth/forgot-password",
            "/social/like",
            "/social/follow",
            "/social/review"
            // Add more sensitive endpoints here in the future
        );

        // Define paths that should be EXCLUDED from rate limiting
        List<String> excludedPaths = List.of(
            "/location/**",           // Location updates (GPS) - exclude as requested
            "/health",
            "/auth/verify/**",
            "/auth/resend-activation",
            "/social/stats/**",       // Stats endpoints - usually read-only
            "/profile/me",
            "/profile/location"       // If you have direct profile location
        );

        registry.addInterceptor(rateLimiterInterceptor)
                .addPathPatterns(rateLimitedPaths)
                .excludePathPatterns(excludedPaths);
    }
}