package com.iflytek.skillhub.domain.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GovernanceNotificationServiceTest {

    @Mock
    private UserNotificationRepository userNotificationRepository;

    private GovernanceNotificationService service;

    @BeforeEach
    void setUp() {
        service = new GovernanceNotificationService(userNotificationRepository);
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
    }

    @Test
    void markRead_requiresOwner() {
        UserNotification notification = new UserNotification(
                "user-1",
                "REVIEW",
                "REVIEW_TASK",
                99L,
                "Review completed",
                "{\"status\":\"APPROVED\"}"
        );
        setField(notification, "id", 10L);
        when(userNotificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        assertThrows(DomainForbiddenException.class, () -> service.markRead(10L, "user-2"));
    }

    @Test
    void listNotifications_returnsNewestFirst() {
        UserNotification unread = new UserNotification("user-1", "REVIEW", "REVIEW_TASK", 99L, "A", "{}");
        UserNotification read = new UserNotification("user-1", "REPORT", "SKILL_REPORT", 88L, "B", "{}");
        when(userNotificationRepository.findByUserIdOrderByCreatedAtDesc("user-1")).thenReturn(List.of(unread, read));

        List<UserNotification> result = service.listNotifications("user-1");

        assertThat(result).hasSize(2);
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
