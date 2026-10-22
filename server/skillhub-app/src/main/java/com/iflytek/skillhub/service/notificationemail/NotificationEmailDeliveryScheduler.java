package com.iflytek.skillhub.service.notificationemail;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/** Scheduled triggers only submit bounded work: no database access or SMTP on the shared timer. */
@Component
public class NotificationEmailDeliveryScheduler {
    private final NotificationEmailDispatcher dispatcher;
    private final ThreadPoolTaskExecutor executor;
    private final AtomicBoolean dispatchSubmitted = new AtomicBoolean();
    private final AtomicBoolean cleanupSubmitted = new AtomicBoolean();
    private final AtomicBoolean stopping = new AtomicBoolean();

    public NotificationEmailDeliveryScheduler(NotificationEmailDispatcher dispatcher,
            @Qualifier("notificationEmailDeliveryExecutor") ThreadPoolTaskExecutor executor) {
        this.dispatcher = dispatcher;
        this.executor = executor;
    }

    @Scheduled(initialDelayString = "${skillhub.notification.email.delivery.poll-delay-ms:10000}",
            fixedDelayString = "${skillhub.notification.email.delivery.poll-delay-ms:10000}")
    public void
    triggerDispatch() {
        submit(dispatchSubmitted, dispatcher::dispatch);
    }

    @Scheduled(fixedDelayString = "${skillhub.notification.email.delivery.cleanup-delay-ms:86400000}")
    public void triggerCleanup() {
        submit(cleanupSubmitted, dispatcher::cleanup);
    }

    private void submit(AtomicBoolean submitted, Runnable work) {
        if (stopping.get() || !submitted.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    if (!stopping.get()) {
                        work.run();
                    }
                } finally {
                    submitted.set(false);
                }
            });
        } catch (RejectedExecutionException rejected) {
            // No caller-runs fallback and no queued claims: a later tick may retry submission.
            submitted.set(false);
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void stop() {
        stopping.set(true);
        dispatcher.stop();
    }
}
