package com.iflytek.skillhub.domain.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class GovernanceNotificationServiceTest {

    @Mock
    private UserNotificationRepository userNotificationRepository;

    private GovernanceNotificationService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-03-18T01:02:03Z"), ZoneOffset.UTC);
        service = new GovernanceNotificationService(userNotificationRepository, clock);
    }

    @Test
    void notifyUser_createsUnreadNotification() {
        when(userNotificationRepository.save(any(UserNotification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserNotification notification = service.notifyUser(
                "user-1",
                "REVIEW",
                "REVIEW_TASK",
                99L,
                "Review completed",
                "{\"status\":\"APPROVED\"}"
        );

        assertThat(notification.getUserId()).isEqualTo("user-1");
        assertThat(notification.getStatus()).isEqualTo(UserNotificationStatus.UNREAD);
        assertThat(notification.getCategory()).isEqualTo("REVIEW");
        assertThat(notification.getCreatedAt()).isEqualTo(Instant.now(clock));
    }

    @Test
    void markRead_requiresOwner() {
        UserNotification notification = new UserNotification(
                "user-1",
                "REVIEW",
                "REVIEW_TASK",
                99L,
                "Review completed",
                "{\"status\":\"APPROVED\"}",
                Instant.parse("2026-03-18T00:00:00Z")
        );
        setField(notification, "id", 10L);
        when(userNotificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        assertThrows(DomainForbiddenException.class, () -> service.markRead(10L, "user-2"));
    }

    @Test
    void listNotifications_returnsNewestFirst() {
        UserNotification unread = new UserNotification("user-1", "REVIEW", "REVIEW_TASK", 99L, "A", "{}", Instant.parse("2026-03-18T00:00:00Z"));
        UserNotification read = new UserNotification("user-1", "REPORT", "SKILL_REPORT", 88L, "B", "{}", Instant.parse("2026-03-18T00:01:00Z"));
        when(userNotificationRepository.findByUserIdOrderByCreatedAtDesc("user-1")).thenReturn(List.of(unread, read));

        List<UserNotification> result = service.listNotifications("user-1");

        assertThat(result).hasSize(2);
    }

    @Test
    void listNotificationsPage_returnsPageMetadata() {
        UserNotification unread = new UserNotification("user-1", "REVIEW", "REVIEW_TASK", 99L, "A", "{}", Instant.parse("2026-03-18T00:00:00Z"));
        when(userNotificationRepository.findByUserIdOrderByCreatedAtDesc("user-1", PageRequest.of(1, 10)))
                .thenReturn(new PageImpl<>(List.of(unread), PageRequest.of(1, 10), 21));

        var result = service.listNotifications("user-1", 1, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(21);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
    }

    @Test
    void markRead_setsReadTimestampFromClock() {
        UserNotification notification = new UserNotification(
                "user-1",
                "REVIEW",
                "REVIEW_TASK",
                99L,
                "Review completed",
                "{\"status\":\"APPROVED\"}",
                Instant.parse("2026-03-18T00:00:00Z")
        );
        setField(notification, "id", 10L);
        when(userNotificationRepository.findById(10L)).thenReturn(Optional.of(notification));
        when(userNotificationRepository.save(any(UserNotification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserNotification result = service.markRead(10L, "user-1");

        assertThat(result.getStatus()).isEqualTo(UserNotificationStatus.READ);
        assertThat(result.getReadAt()).isEqualTo(Instant.now(clock));
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
