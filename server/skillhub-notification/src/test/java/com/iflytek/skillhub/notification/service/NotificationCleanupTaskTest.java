package com.iflytek.skillhub.notification.service;

import com.iflytek.skillhub.notification.domain.NotificationRepository;
import com.iflytek.skillhub.notification.domain.NotificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationCleanupTaskTest {

    @Mock private NotificationRepository notificationRepository;

    private Clock clock;
    private NotificationCleanupTask cleanupTask;

    private static final Instant NOW = Instant.parse("2026-03-19T02:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        cleanupTask = new NotificationCleanupTask(notificationRepository, clock, 30, 90);
    }

    @Test
    void cleanup_shouldDeleteReadNotificationsOlderThanRetention() {
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);

        cleanupTask.cleanup();

        verify(notificationRepository).deleteByStatusAndCreatedAtBefore(
                eq(NotificationStatus.READ), cutoffCaptor.capture());

        Instant expectedCutoff = NOW.minusSeconds(30L * 24 * 60 * 60);
        assertTrue(cutoffCaptor.getValue().equals(expectedCutoff),
                "Cutoff should be 30 days before now");
    }

    @Test
    void cleanup_shouldDeleteUnreadNotificationsOlderThanRetention() {
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);

        cleanupTask.cleanup();

        verify(notificationRepository).deleteByStatusAndCreatedAtBefore(
                eq(NotificationStatus.UNREAD), cutoffCaptor.capture());

        Instant expectedCutoff = NOW.minusSeconds(90L * 24 * 60 * 60);
        assertTrue(cutoffCaptor.getValue().equals(expectedCutoff),
                "Cutoff should be 90 days before now");
    }
}
