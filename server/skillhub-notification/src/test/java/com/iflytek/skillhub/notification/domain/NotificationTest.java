package com.iflytek.skillhub.notification.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-03-19T10:00:00Z");

    @Test
    void newNotification_shouldHaveUnreadStatus() {
        Notification notification = new Notification("user-1", NotificationCategory.REVIEW,
                "review.approved", "Title", "{}", "skill", 1L, CREATED_AT);

        assertEquals(NotificationStatus.UNREAD, notification.getStatus());
    }

    @Test
    void markRead_shouldSetStatusAndReadAt() {
        Notification notification = new Notification("user-1", NotificationCategory.REVIEW,
                "review.approved", "Title", "{}", "skill", 1L, CREATED_AT);
        Instant readAt = Instant.parse("2026-03-19T11:00:00Z");

        notification.markRead(readAt);

        assertEquals(NotificationStatus.READ, notification.getStatus());
        assertEquals(readAt, notification.getReadAt());
    }

    @Test
    void constructor_shouldSetAllFields() {
        Notification notification = new Notification("user-1", NotificationCategory.PUBLISH,
                "skill.published", "Skill Published", "{\"skillName\":\"test\"}", "skill", 42L, CREATED_AT);

        assertEquals("user-1", notification.getRecipientId());
        assertEquals(NotificationCategory.PUBLISH, notification.getCategory());
        assertEquals("skill.published", notification.getEventType());
        assertEquals("Skill Published", notification.getTitle());
        assertEquals("{\"skillName\":\"test\"}", notification.getBodyJson());
        assertEquals("skill", notification.getEntityType());
        assertEquals(42L, notification.getEntityId());
        assertEquals(CREATED_AT, notification.getCreatedAt());
        assertNull(notification.getReadAt());
    }
}
