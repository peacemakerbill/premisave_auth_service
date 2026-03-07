package com.premisave.auth.config;

import com.premisave.auth.util.RateLimiterInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimiterInterceptor rateLimiterInterceptor;

    public WebConfig(RateLimiterInterceptor rateLimiterInterceptor) {
        this.rateLimiterInterceptor = rateLimiterInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Apply rate limiting to auth endpoints only
        registry.addInterceptor(rateLimiterInterceptor)
                .addPathPatterns("/auth/signup", "/auth/signin", "/auth/reset-password");
    }
}