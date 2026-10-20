package com.premisave.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers the app.storage.* properties. */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {
}