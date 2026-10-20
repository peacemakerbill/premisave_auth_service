package com.premisave.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

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
        // When the queue is full the task runs on the calling thread instead of throwing, so a burst of
        // mail can never turn a sign-in or a role grant into an error.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        return executor;
    }
}
