package com.iflytek.skillhub.domain.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserNotificationTest {

    @Test
    void protectedConstructor_shouldInitializeEmpty() {
        UserNotification notification = new UserNotification();

        assertThat(notification.getId()).isNull();
        assertThat(notification.getUserId()).isNull();
        assertThat(notification.getCategory()).isNull();
        assertThat(notification.getEntityType()).isNull();
        assertThat(notification.getEntityId()).isNull();
        assertThat(notification.getTitle()).isNull();
        assertThat(notification.getBodyJson()).isNull();
        assertThat(notification.getStatus()).isEqualTo(UserNotificationStatus.UNREAD);
        assertThat(notification.getCreatedAt()).isNull();
        assertThat(notification.getReadAt()).isNull();
    }

    @Test
    void publicConstructor_shouldSetFields() {
        Instant createdAt = Instant.parse("2026-05-01T12:00:00Z");
        UserNotification notification = new UserNotification(
                "user-1", "SYSTEM", "SKILL", 1L, "Title", "{}", createdAt);

        assertThat(notification.getId()).isNull();
        assertThat(notification.getUserId()).isEqualTo("user-1");
        assertThat(notification.getCategory()).isEqualTo("SYSTEM");
        assertThat(notification.getEntityType()).isEqualTo("SKILL");
        assertThat(notification.getEntityId()).isEqualTo(1L);
        assertThat(notification.getTitle()).isEqualTo("Title");
        assertThat(notification.getBodyJson()).isEqualTo("{}");
        assertThat(notification.getStatus()).isEqualTo(UserNotificationStatus.UNREAD);
        assertThat(notification.getCreatedAt()).isEqualTo(createdAt);
        assertThat(notification.getReadAt()).isNull();
    }

    @Test
    void markRead_shouldUpdateStatusAndReadAt() {
        Instant createdAt = Instant.parse("2026-05-01T12:00:00Z");
        UserNotification notification = new UserNotification(
                "user-1", "SYSTEM", "SKILL", 1L, "Title", "{}", createdAt);

        Instant readAt = Instant.parse("2026-05-02T08:00:00Z");
        notification.markRead(readAt);

        assertThat(notification.getStatus()).isEqualTo(UserNotificationStatus.READ);
        assertThat(notification.getReadAt()).isEqualTo(readAt);
    }
}
