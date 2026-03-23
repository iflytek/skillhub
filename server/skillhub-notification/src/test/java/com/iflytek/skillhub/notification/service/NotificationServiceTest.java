package com.iflytek.skillhub.notification.service;

import com.iflytek.skillhub.notification.domain.*;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    private Clock clock;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-03-19T10:00:00Z"), ZoneOffset.UTC);
        service = new NotificationService(notificationRepository, clock);
    }

    @Test
    void createNotification_shouldSaveAndReturn() {
        Notification notification = new Notification("user-1", NotificationCategory.REVIEW,
                "review.approved", "notification.review.approved",
                "{\"skillName\":\"test\"}", "skill", 1L, Instant.now(clock));
        when(notificationRepository.save(any())).thenReturn(notification);

        Notification result = service.create("user-1", NotificationCategory.REVIEW,
                "review.approved", "notification.review.approved",
                "{\"skillName\":\"test\"}", "skill", 1L);

        assertNotNull(result);
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void getUnreadCount_shouldReturnCount() {
        when(notificationRepository.countByRecipientIdAndStatus("user-1", NotificationStatus.UNREAD))
                .thenReturn(5L);
        long count = service.getUnreadCount("user-1");
        assertEquals(5L, count);
    }

    @Test
    void markRead_shouldUpdateNotification() {
        Notification notification = new Notification("user-1", NotificationCategory.REVIEW,
                "review.approved", "title", null, "skill", 1L, Instant.now(clock));
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenReturn(notification);

        service.markRead(1L, "user-1");

        assertEquals(NotificationStatus.READ, notification.getStatus());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markRead_shouldRejectWrongUser() {
        Notification notification = new Notification("user-1", NotificationCategory.REVIEW,
                "review.approved", "title", null, "skill", 1L, Instant.now(clock));
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        assertThrows(DomainForbiddenException.class, () -> service.markRead(1L, "user-2"));
    }

    @Test
    void markAllRead_shouldDelegateToRepository() {
        when(notificationRepository.markAllReadByRecipientId(eq("user-1"), any())).thenReturn(3);
        int count = service.markAllRead("user-1");
        assertEquals(3, count);
    }

    @Test
    void deleteRead_shouldDeleteOwnedReadNotification() {
        when(notificationRepository.deleteByIdAndRecipientIdAndStatus(1L, "user-1", NotificationStatus.READ))
                .thenReturn(1);

        assertDoesNotThrow(() -> service.deleteRead(1L, "user-1"));
        verify(notificationRepository).deleteByIdAndRecipientIdAndStatus(1L, "user-1", NotificationStatus.READ);
    }

    @Test
    void deleteRead_shouldRejectUnreadOrForeignNotification() {
        when(notificationRepository.deleteByIdAndRecipientIdAndStatus(1L, "user-1", NotificationStatus.READ))
                .thenReturn(0);

        assertThrows(DomainBadRequestException.class, () -> service.deleteRead(1L, "user-1"));
    }

    @Test
    void markRead_shouldRejectMissingNotification() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () -> service.markRead(99L, "user-1"));
    }

    @Test
    void list_shouldReturnPagedResults() {
        Notification n = new Notification("user-1", NotificationCategory.REVIEW,
                "review.approved", "title", null, "skill", 1L, Instant.now(clock));
        Page<Notification> page = new PageImpl<>(List.of(n));
        when(notificationRepository.findByRecipientId(eq("user-1"), any())).thenReturn(page);

        Page<Notification> result = service.list("user-1", null, PageRequest.of(0, 20));
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void list_withCategory_shouldFilterByCategory() {
        Page<Notification> page = new PageImpl<>(List.of());
        when(notificationRepository.findByRecipientIdAndCategory(eq("user-1"), eq(NotificationCategory.REVIEW), any()))
                .thenReturn(page);

        service.list("user-1", NotificationCategory.REVIEW, PageRequest.of(0, 20));
        verify(notificationRepository).findByRecipientIdAndCategory(eq("user-1"), eq(NotificationCategory.REVIEW), any());
    }
}
