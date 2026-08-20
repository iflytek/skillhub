package com.iflytek.skillhub.notification.service;

import com.iflytek.skillhub.notification.domain.Notification;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.domain.NotificationChannel;
import com.iflytek.skillhub.notification.sse.SseEmitterManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock private NotificationService notificationService;
    @Mock private NotificationPreferenceService preferenceService;
    @Mock private SseEmitterManager sseEmitterManager;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(notificationService, preferenceService, sseEmitterManager);
    }

    private Notification buildNotificationMock() {
        Notification n = mock(Notification.class);
        lenient().when(n.getId()).thenReturn(1L);
        lenient().when(n.getCategory()).thenReturn(NotificationCategory.REVIEW);
        lenient().when(n.getEventType()).thenReturn("review.approved");
        lenient().when(n.getTitle()).thenReturn("Title");
        lenient().when(n.getBodyJson()).thenReturn("{}");
        lenient().when(n.getEntityType()).thenReturn("skill");
        lenient().when(n.getEntityId()).thenReturn(1L);
        lenient().when(n.getCreatedAt()).thenReturn(Instant.parse("2026-03-19T10:00:00Z"));
        return n;
    }

    @Test
    void dispatch_shouldPersistAndPushWhenEnabled() {
        Notification notification = buildNotificationMock();
        when(preferenceService.isEnabled("user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP))
                .thenReturn(true);
        when(notificationService.create(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(notification);

        dispatcher.dispatch("user-1", NotificationCategory.REVIEW,
                "review.approved", "Title", "{}", "skill", 1L);

        verify(notificationService).create("user-1", NotificationCategory.REVIEW,
                "review.approved", "Title", "{}", "skill", 1L);
        verify(sseEmitterManager).push(eq("user-1"), any());
    }

    @Test
    void dispatch_persistsExactSubscriberNotificationAndPushesSameRecipientVisiblePayload() {
        Notification notification = new Notification("subscriber-1", NotificationCategory.PUBLISH,
                "SUBSCRIPTION_NEW_VERSION", "Skill updated: Demo",
                "{\"skillId\":1,\"versionId\":10}", "SKILL", 1L,
                Instant.parse("2026-08-19T20:30:00Z"));
        try {
            var id = Notification.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(notification, 42L);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        when(preferenceService.isEnabled("subscriber-1", NotificationCategory.PUBLISH,
                NotificationChannel.IN_APP)).thenReturn(true);
        when(notificationService.create(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(notification);

        dispatcher.dispatch("subscriber-1", NotificationCategory.PUBLISH, "SUBSCRIPTION_NEW_VERSION",
                "Skill updated: Demo", "{\"skillId\":1,\"versionId\":10}", "SKILL", 1L);

        verify(notificationService).create("subscriber-1", NotificationCategory.PUBLISH,
                "SUBSCRIPTION_NEW_VERSION", "Skill updated: Demo",
                "{\"skillId\":1,\"versionId\":10}", "SKILL", 1L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(sseEmitterManager).push(eq("subscriber-1"), payload.capture());
        assertThat(payload.getValue()).containsEntry("id", 42L)
                .containsEntry("category", "PUBLISH")
                .containsEntry("eventType", "SUBSCRIPTION_NEW_VERSION")
                .containsEntry("bodyJson", "{\"skillId\":1,\"versionId\":10}")
                .containsEntry("entityType", "SKILL")
                .containsEntry("entityId", 1L);
    }

    @Test
    void dispatch_shouldSkipWhenPreferenceDisabled() {
        when(preferenceService.isEnabled("user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP))
                .thenReturn(false);

        dispatcher.dispatch("user-1", NotificationCategory.REVIEW,
                "review.approved", "Title", "{}", "skill", 1L);

        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any(), any());
        verify(sseEmitterManager, never()).push(any(), any());
    }

    @Test
    void dispatch_shouldStillPersistWhenSsePushFails() {
        Notification notification = buildNotificationMock();
        when(preferenceService.isEnabled("user-1", NotificationCategory.REVIEW, NotificationChannel.IN_APP))
                .thenReturn(true);
        when(notificationService.create(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(notification);
        doThrow(new RuntimeException("SSE failure")).when(sseEmitterManager).push(any(), any());

        dispatcher.dispatch("user-1", NotificationCategory.REVIEW,
                "review.approved", "Title", "{}", "skill", 1L);

        verify(notificationService).create("user-1", NotificationCategory.REVIEW,
                "review.approved", "Title", "{}", "skill", 1L);
    }
}
