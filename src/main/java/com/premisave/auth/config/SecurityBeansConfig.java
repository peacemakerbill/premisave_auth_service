package com.premisave.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Registers the app.security.* properties and a Clock that tests can replace. */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityBeansConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
