package com.premisave.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled methods (used by the username sweep). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}