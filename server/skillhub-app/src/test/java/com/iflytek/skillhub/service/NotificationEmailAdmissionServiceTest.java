package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.email.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class NotificationEmailAdmissionServiceTest {
    final Instant now = Instant.parse("2026-10-22T12:00:00Z");
    final NotificationEmailPolicyProvider policies = mock(NotificationEmailPolicyProvider.class);
    final NotificationEmailRecipientProvider recipients = mock(NotificationEmailRecipientProvider.class);
    final NotificationEmailPreferenceRepository preferences = mock(NotificationEmailPreferenceRepository.class);
    final NotificationEmailAdmissionService service = new NotificationEmailAdmissionService(policies, recipients, preferences);

    @Test
    void missingAnyProofOrWatermarkFailsClosedWithoutManufacturingTime() {
        var row = new NotificationEmailPreference("user", NotificationCategory.PUBLISH);
        row.setMode(NotificationEmailMode.DIGEST, now);
        when(preferences.findByUserIdOrderByCategoryAsc("user")).thenReturn(List.of(row));
        when(policies.current()).thenReturn(new NotificationEmailPolicy(true, 60, "https://example.test", 0));
        when(recipients.current("user")).thenReturn(Optional.of(
                new NotificationEmailRecipient("user", "a@example.test", 0, "en", now)));
        assertThat(service.currentUnderLocks("user", NotificationCategory.PUBLISH)).isEmpty();
        when(policies.current()).thenReturn(new NotificationEmailPolicy(true, 60, "https://example.test", 0, now));
        when(recipients.current("user")).thenReturn(Optional.of(
                new NotificationEmailRecipient("user", "a@example.test", 0, "en")));
        assertThat(service.currentUnderLocks("user", NotificationCategory.PUBLISH)).isEmpty();
        when(recipients.current("user")).thenReturn(Optional.empty());
        assertThat(service.currentUnderLocks("user", NotificationCategory.PUBLISH)).isEmpty();
    }

    @Test
    void mergesLatestBaselineAndOnlyReadsAlreadyGuardedRows() {
        when(policies.current()).thenReturn(new NotificationEmailPolicy(true, 60, "https://example.test", 0, now));
        when(recipients.current("user")).thenReturn(Optional.of(
                new NotificationEmailRecipient("user", "a@example.test", 0, "en", now.plusSeconds(60))));
        var row = new NotificationEmailPreference("user", NotificationCategory.PUBLISH);
        row.setMode(NotificationEmailMode.DIGEST, now.plusSeconds(120));
        when(preferences.findByUserIdOrderByCategoryAsc("user")).thenReturn(List.of(row));
        assertThat(service.currentUnderLocks("user", NotificationCategory.PUBLISH)).contains(now.plusSeconds(120));
        row.setMode(NotificationEmailMode.OFF, now.plusSeconds(180));
        assertThat(service.currentUnderLocks("user", NotificationCategory.PUBLISH)).isEmpty();
        verify(policies, never()).lockCurrent();
        verify(recipients, never()).lockCurrent(anyString());
        verify(preferences, never()).lockAll(anyString());
    }
}
