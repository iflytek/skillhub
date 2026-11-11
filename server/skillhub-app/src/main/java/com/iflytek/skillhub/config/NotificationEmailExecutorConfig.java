package com.iflytek.skillhub.config;

import com.iflytek.skillhub.notification.email.delivery.NotificationEmailDeliveryRepository;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Email network waits never occupy the application's shared scheduler or authoring timeout timer. */
@Configuration
public class NotificationEmailExecutorConfig {
    @Bean(name = "notificationEmailDeliveryExecutor")
    public ThreadPoolTaskExecutor notificationEmailDeliveryExecutor(NotificationEmailDeliveryRepository repository) {
        // The dependency keeps persistence alive until this executor's in-flight acknowledgment has finished.
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("notification-email-");
        // Allow JVM exit after the bounded shutdown grace; unfinished leases are recovered on restart.
        executor.setDaemon(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
