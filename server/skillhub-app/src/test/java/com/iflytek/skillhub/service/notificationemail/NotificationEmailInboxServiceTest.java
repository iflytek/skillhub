package com.iflytek.skillhub.service.notificationemail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.email.*;
import com.iflytek.skillhub.notification.email.delivery.*;
import java.time.*;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class NotificationEmailInboxServiceTest {
    Instant now = Instant.parse("2026-10-22T10:00:00Z");
    NotificationEmailPolicyProvider policy = mock(NotificationEmailPolicyProvider.class);
    NotificationEmailRecipientProvider recipients = mock(NotificationEmailRecipientProvider.class);
    NotificationEmailPreferenceService preferences = mock(NotificationEmailPreferenceService.class);
    NotificationEmailDeliveryRepository repository = mock(NotificationEmailDeliveryRepository.class);
    NotificationEmailEligibility eligibility = mock(NotificationEmailEligibility.class);
    NotificationEmailAdmissionProvider admission = mock(NotificationEmailAdmissionProvider.class);
    NotificationEmailInboxService inbox = new NotificationEmailInboxService(policy, recipients, preferences,
            repository, eligibility, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC), admission);

    @BeforeEach
    void setup() {
        when(policy.lockCurrent()).thenReturn(new NotificationEmailPolicy(true, 60, "https://example.test", 0));
        when(recipients.lockCurrent("reviewer"))
                .thenReturn(Optional.of(new NotificationEmailRecipient("reviewer", "reviewer@example.test", 0, "en")));
        when(preferences.lockCurrent("reviewer"))
                .thenReturn(Map.of(NotificationCategory.REVIEW, NotificationEmailMode.IMMEDIATE));
        when(admission.currentUnderLocks("reviewer", NotificationCategory.REVIEW))
                .thenReturn(Optional.of(now.minusSeconds(60)));
        when(eligibility.eligible(any())).thenReturn(true);
        when(repository.openBatch(anyString(), anyString(), anyString(), anyLong(), any(), any()))
                .thenReturn(new NotificationEmailDelivery(
                        1, "reviewer", "IMMEDIATE", "suite-event", 0, "PENDING", 0, now, null, null, null, now, null));
    }

    NotificationEmailCommand command(String targetType) {
        return command(targetType, "SUITE_REVIEW_SUBMITTED");
    }

    NotificationEmailCommand command(String targetType, String eventType) {
        return new NotificationEmailCommand("reviewer", NotificationCategory.REVIEW, eventType,
                "suite-event", now, "{\"suiteId\":8,\"reviewId\":101,\"skillId\":999}", targetType, 101L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUITE_REVIEW_SUBMITTED", "SUITE_REVIEW_APPROVED", "SUITE_REVIEW_REJECTED"})
    void queuesTheReviewIdentityWithoutTrustingAnySkillIdInTheBody(String eventType) {
        var command = command("REVIEW", eventType);
        inbox.enqueue(command);

        var item = ArgumentCaptor.forClass(NotificationEmailItem.class);
        verify(eligibility).eligible(item.capture());
        assertThat(item.getValue().entityId()).isEqualTo(101L);
        assertThat(item.getValue().skillId()).isNull();
        assertThat(item.getValue().category()).isEqualTo(NotificationCategory.REVIEW);
        verify(repository).addItem(1, command, null, now);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUITE_REVIEW_SUBMITTED", "SUITE_REVIEW_APPROVED", "SUITE_REVIEW_REJECTED"})
    void rejectsASuiteEventWhoseEntityIsNotAReviewTask(String eventType) {
        inbox.enqueue(command("SKILL", eventType));
        verifyNoInteractions(policy, recipients, preferences, repository, eligibility, admission);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUITE_REVIEW_SUBMITTED", "SUITE_REVIEW_APPROVED", "SUITE_REVIEW_REJECTED"})
    void rejectsASuiteEventRelabeledToBypassTheReviewPreference(String eventType) {
        var original = command("REVIEW", eventType);
        inbox.enqueue(new NotificationEmailCommand(original.recipientId(), NotificationCategory.PUBLISH,
                original.eventType(), original.eventKey(), original.occurredAt(), original.bodyJson(),
                original.entityType(), original.entityId()));
        verifyNoInteractions(policy, recipients, preferences, repository, eligibility, admission);
    }

    @Test
    void suppressesAnItemThatNoLongerHasReviewPermission() {
        when(eligibility.eligible(any())).thenReturn(false);
        inbox.enqueue(command("REVIEW"));
        verifyNoInteractions(repository);
    }

    @Test
    void suiteReviewRespectsTheExistingReviewMailPreference() {
        when(preferences.lockCurrent("reviewer"))
                .thenReturn(Map.of(NotificationCategory.REVIEW, NotificationEmailMode.OFF));
        inbox.enqueue(command("REVIEW"));
        verifyNoInteractions(repository, eligibility);
    }
}
