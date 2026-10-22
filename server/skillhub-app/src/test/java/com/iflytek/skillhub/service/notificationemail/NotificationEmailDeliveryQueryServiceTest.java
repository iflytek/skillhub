package com.iflytek.skillhub.service.notificationemail;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.email.delivery.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class NotificationEmailDeliveryQueryServiceTest {
    NotificationEmailDeliveryRepository repository = mock(NotificationEmailDeliveryRepository.class);
    NotificationEmailEligibility eligibility = mock(NotificationEmailEligibility.class);
    AuditLogService audit = mock(AuditLogService.class);
    Instant now = Instant.parse("2026-10-22T00:00:00Z");
    NotificationEmailDeliveryQueryService service =
            new NotificationEmailDeliveryQueryService(repository, eligibility, audit, Clock.fixed(now, ZoneOffset.UTC));
    NotificationEmailDelivery batch(String status) {
        return new NotificationEmailDelivery(
                11, "owner", "DIGEST", "window", 0, status, 1, now, null, null, null, now, null);
    }

    @Test
    void anotherAccountAndCleanedBatchCannotReadDetails() {
        when(repository.find(11)).thenReturn(Optional.of(batch("SENT")));
        assertThatThrownBy(() -> service.detail(11, "other", 0, 20)).isInstanceOf(DomainNotFoundException.class);
        verify(repository, never()).items(anyLong());
        assertThatThrownBy(() -> service.detail(12, "owner", 0, 20)).isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    void detailsAreIndependentlyPaginatedAndFilterCurrentPermission() {
        when(repository.find(11)).thenReturn(Optional.of(batch("SENT")));
        var items = new ArrayList<NotificationEmailItem>();
        for (int i = 1; i <= 55; i++)
            items.add(new NotificationEmailItem(i, 11, "owner", "e" + i, NotificationCategory.PUBLISH,
                    "SKILL_PUBLISHED", 1L, 1L, now, now, "ACTIVE"));
        when(repository.items(11)).thenReturn(items);
        when(eligibility.eligible(any())).thenAnswer(inv -> ((NotificationEmailItem) inv.getArgument(0)).id() != 55);
        when(eligibility.targetPath(any())).thenReturn("/dashboard/notifications");
        var details = service.detail(11, "owner", 2, 20);
        assertThat(details.totalItems()).isEqualTo(54);
        assertThat(details.unavailableItems()).isEqualTo(1);
        assertThat(details.items()).hasSize(14);
        assertThat(details.totalPages()).isEqualTo(3);
        assertThat(details.items().getFirst().id()).isEqualTo(41);
    }

    @Test
    void unknownRequiresExplicitDuplicateRiskAndRetryIsAuditedWithoutEmail() {
        when(repository.lock(11)).thenReturn(batch("UNKNOWN"));
        assertThatThrownBy(() -> service.retry(11, "admin", false)).isInstanceOf(DomainBadRequestException.class);
        verify(repository, never()).retry(anyLong(), any());
        service.retry(11, "admin", true);
        verify(repository).retry(11, now);
        verify(audit).record(eq("admin"), eq("NOTIFICATION_EMAIL_RETRY"), eq("NOTIFICATION_EMAIL_DELIVERY"), eq(11L),
                isNull(), isNull(), isNull(),
                eq("{\"previousStatus\":\"UNKNOWN\",\"duplicateRiskAcknowledged\":true}"));
        when(repository.lock(11)).thenReturn(batch("SENT"));
        assertThatThrownBy(() -> service.retry(11, "admin", true)).isInstanceOf(DomainBadRequestException.class);
    }

    @Test
    void suppressedItemsNeverReappearInSentDetailsAfterCategoryIsReenabled() {
        when(repository.find(11)).thenReturn(Optional.of(batch("SENT")));
        var cancelled = new NotificationEmailItem(1, 11, "owner", "publish", NotificationCategory.PUBLISH,
                "SKILL_PUBLISHED", 1L, 1L, now, now, "SUPPRESSED");
        var sent = new NotificationEmailItem(
                2, 11, "owner", "review", NotificationCategory.REVIEW, "REVIEW_APPROVED", 1L, 1L, now, now, "ACTIVE");
        when(repository.items(11)).thenReturn(List.of(cancelled, sent));
        // Both resources are currently accessible after re-enable; persisted suppression must still win.
        when(eligibility.eligible(any())).thenReturn(true);
        when(eligibility.targetPath(sent)).thenReturn("/dashboard/notifications");
        var details = service.detail(11, "owner", 0, 20);
        assertThat(details.items())
                .extracting(com.iflytek.skillhub.dto.NotificationEmailDeliveryItemResponse::id)
                .containsExactly(2L);
        assertThat(details.unavailableItems()).isEqualTo(1);
        assertThat(details.totalItems()).isEqualTo(1);
        verify(eligibility, never()).eligible(cancelled);
    }
}
