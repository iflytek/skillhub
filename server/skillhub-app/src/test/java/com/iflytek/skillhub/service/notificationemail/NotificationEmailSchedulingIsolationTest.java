package com.iflytek.skillhub.service.notificationemail;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.iflytek.skillhub.config.AuthoringExecutorConfig;
import com.iflytek.skillhub.config.AuthoringProperties;
import com.iflytek.skillhub.config.NotificationEmailExecutorConfig;
import com.iflytek.skillhub.config.SchedulingConfig;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.email.*;
import com.iflytek.skillhub.notification.email.delivery.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class NotificationEmailSchedulingIsolationTest {
    public static class OrdinaryScheduledWork {
        final AtomicBoolean observe = new AtomicBoolean();
        final CountDownLatch tickDuringEmail = new CountDownLatch(1);
        final AtomicReference<String> thread = new AtomicReference<>();
        @Scheduled(fixedDelay = 20)
        public void tick() {
            if (observe.get()) {
                thread.set(Thread.currentThread().getName());
                tickDuringEmail.countDown();
            }
        }
    }

    @Test
    void blockedEmailCannotOccupyAuthoringTimerOrQueueDuplicateDispatchAndShutdownWaitsForCurrentAttempt()
            throws Exception {
        var repository = mock(NotificationEmailDeliveryRepository.class);
        var policy = mock(NotificationEmailPolicyProvider.class);
        var recipients = mock(NotificationEmailRecipientProvider.class);
        var preferences = mock(NotificationEmailPreferenceService.class);
        var eligibility = mock(NotificationEmailEligibility.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
        Instant now = Instant.parse("2026-10-22T00:00:00Z");
        var batch = new NotificationEmailDelivery(
                1, "user", "IMMEDIATE", "one", 0, "CLAIMED", 0, now, "token", now.plusSeconds(120), null, now, null);
        when(repository.claim(any(), any(), any(), any(), anySet())).thenReturn(Optional.of(batch), Optional.empty());
        when(repository.lock(1)).thenReturn(batch);
        when(repository.lockRecipient("user")).thenReturn(true);
        when(repository.authorize(eq(1L), eq("token"), any(), any())).thenReturn(true);
        when(repository.itemPage(1, 0, 50))
                .thenReturn(List.of(new NotificationEmailItem(1, 1, "user", "event", NotificationCategory.REVIEW,
                        "REVIEW_APPROVED", 1L, 1L, now, now, "ACTIVE")));
        when(policy.lockCurrent()).thenReturn(new NotificationEmailPolicy(true, 15, "https://example.test", 0));
        when(recipients.lockCurrent("user"))
                .thenReturn(Optional.of(new NotificationEmailRecipient("user", "u@example.test", 0, "en")));
        when(preferences.lockCurrent("user"))
                .thenReturn(Map.of(NotificationCategory.REVIEW, NotificationEmailMode.IMMEDIATE));
        when(eligibility.eligible(any())).thenReturn(true);
        CountDownLatch smtpStarted = new CountDownLatch(1), cleanupStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1), closing = new CountDownLatch(1);
        AtomicReference<String> smtpThread = new AtomicReference<>(), cleanupThread = new AtomicReference<>();
        AtomicBoolean releaseTimedOut = new AtomicBoolean();
        NotificationEmailSender sender = (a, b, c, d, e) -> {
            smtpThread.set(Thread.currentThread().getName());
            smtpStarted.countDown();
            try {
                if (!release.await(15, TimeUnit.SECONDS))
                    releaseTimedOut.set(true);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return NotificationEmailSender.Result.UNKNOWN;
            }
            return NotificationEmailSender.Result.ACCEPTED;
        };
        when(repository.cleanup(any(), any())).thenAnswer(inv -> {
            cleanupThread.set(Thread.currentThread().getName());
            cleanupStarted.countDown();
            if (!release.await(15, TimeUnit.SECONDS))
                releaseTimedOut.set(true);
            return 0;
        });
        var dispatcher = new NotificationEmailDispatcher(repository, policy, recipients, preferences, eligibility,
                sender, new NotificationEmailDeliveryProperties(), Clock.fixed(now, ZoneOffset.UTC), manager);
        var ordinary = new OrdinaryScheduledWork();
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixture",
                Map.of("skillhub.notification.email.delivery.poll-delay-ms", "20",
                        "skillhub.notification.email.delivery.cleanup-delay-ms", "20")));
        context.registerBean(NotificationEmailDeliveryRepository.class, () -> repository);
        context.registerBean(NotificationEmailDispatcher.class, () -> dispatcher);
        context.registerBean(OrdinaryScheduledWork.class, () -> ordinary);
        context.register(AuthoringExecutorConfig.class, AuthoringProperties.class, SchedulingConfig.class,
                NotificationEmailExecutorConfig.class, NotificationEmailDeliveryScheduler.class);
        context.addApplicationListener(event -> {
            if (event instanceof ContextClosedEvent)
                closing.countDown();
        });
        try {
            context.refresh();
            var scheduler = context.getBean(NotificationEmailDeliveryScheduler.class);
            var worker = context.getBean("notificationEmailDeliveryExecutor", ThreadPoolTaskExecutor.class);
            var authoringTimer = context.getBean("authoringValidationTimer", ScheduledExecutorService.class);
            assertThat(smtpStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(cleanupStarted.await(5, TimeUnit.SECONDS)).isTrue();
            ordinary.observe.set(true);
            assertThat(ordinary.tickDuringEmail.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(ordinary.thread.get()).isEqualTo("authoring-validation-timeout");
            assertThat(authoringTimer.schedule(() -> Thread.currentThread().getName(), 0, TimeUnit.MILLISECONDS)
                               .get(3, TimeUnit.SECONDS))
                    .isEqualTo("authoring-validation-timeout");
            assertThat(smtpThread.get()).startsWith("notification-email-");
            assertThat(cleanupThread.get()).startsWith("notification-email-");
            for (int i = 0; i < 1000; i++) {
                scheduler.triggerDispatch();
                scheduler.triggerCleanup();
                dispatcher.dispatch();
            }
            assertThat(worker.getQueueSize()).isZero();
            assertThat(worker.getActiveCount()).isEqualTo(2);
            verify(repository, times(1)).claim(any(), any(), any(), any(), anySet());
            verify(repository, times(1)).cleanup(any(), any());
            try (var closer = Executors.newSingleThreadExecutor()) {
                var closed = closer.submit(context::close);
                assertThat(closing.await(3, TimeUnit.SECONDS)).isTrue();
                release.countDown();
                closed.get(5, TimeUnit.SECONDS);
            }
            assertThat(worker.getThreadPoolExecutor().isShutdown()).isTrue();
            assertThat(worker.getThreadPoolExecutor().isTerminated()).isTrue();
            assertThat(authoringTimer.isShutdown()).isTrue();
            assertThat(releaseTimedOut).isFalse();
            verify(repository, times(1)).claim(any(), any(), any(), any(), anySet());
        } finally {
            release.countDown();
            context.close();
        }
    }
}
