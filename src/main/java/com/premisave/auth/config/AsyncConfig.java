package com.premisave.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(10);      // Normal threads
        executor.setMaxPoolSize(50);       // Max threads during high load
        executor.setQueueCapacity(100);    // Queue size for waiting tasks
        executor.setThreadNamePrefix("AsyncEmail-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        
        return executor;
    }
}