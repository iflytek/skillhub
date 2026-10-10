package com.iflytek.skillhub.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated executor for authoring validation runs. Kept separate from
 * {@code skillhubEventExecutor} because validation tasks run short-lived subprocesses
 * and blocking HTTP calls that would starve the lightweight event executor.
 */
@Configuration
public class AuthoringExecutorConfig {

    @Bean(name = "authoringValidationExecutor")
    public Executor authoringValidationExecutor(AuthoringProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, properties.getExecutorThreads()));
        executor.setMaxPoolSize(Math.max(1, properties.getExecutorThreads()));
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("authoring-validation-");
        // a full queue runs the task on the submitting thread instead of dropping runs
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
