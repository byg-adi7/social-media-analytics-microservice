package com.platform.analytics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Dedicated executor for @Async work (currently: push-notification fan-out
 * in NotificationServiceImpl.notifyUser). Named explicitly and referenced
 * via @Async("notificationTaskExecutor") rather than relying on Spring's
 * default-bean-name lookup, so a future second @Async use case doesn't
 * silently share (or fight over) this pool's sizing.
 */
@Configuration
public class AsyncConfig {

    @Bean("notificationTaskExecutor")
    public Executor notificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("notify-async-");
        executor.initialize();
        return executor;
    }
}
