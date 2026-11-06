package com.iflytek.skillhub.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.event.SkillSuiteReviewCompletedEvent;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.suite.SkillSuiteRepository;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersion;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionRepository;
import com.iflytek.skillhub.notification.domain.Notification;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.domain.NotificationChannel;
import com.iflytek.skillhub.notification.domain.NotificationRepository;
import com.iflytek.skillhub.notification.service.NotificationPreferenceService;
import com.iflytek.skillhub.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillSuiteReviewResultNotificationListenerTest {
    @Mock private ReviewTaskRepository reviews;
    @Mock private SkillSuiteRepository suites;
    @Mock private SkillSuiteVersionRepository versions;
    @Mock private NamespaceRepository namespaces;
    @Mock private NotificationService notifications;
    private final ObjectMapper mapper = new ObjectMapper();
    private SkillSuiteReviewResultNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new SkillSuiteReviewResultNotificationListener(reviews, suites, versions,
                namespaces, notifications, mapper);
    }

    @ParameterizedTest
    @EnumSource(value = ReviewTaskStatus.class, names = {"APPROVED", "REJECTED"})
    void resultNotifiesTheVersionSubmitterWithoutCopyingReviewerComments(ReviewTaskStatus status) throws Exception {
        stub(status, "submitter");
        var event = event(status, "submitter");
        listener.onReviewCompleted(event);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notifications).create(eq("submitter"), eq(NotificationCategory.REVIEW),
                eq("SUITE_REVIEW_" + status), anyString(), body.capture(), eq("REVIEW"), eq(30L));
        var json = mapper.readTree(body.getValue());
        assertThat(json.path("suiteName").asText()).isEqualTo("Current Suite");
        assertThat(json.path("version").asText()).isEqualTo("2.0.0");
        assertThat(json.path("eventId").asText()).isEqualTo(event.eventId());
        assertThat(json.path("occurredAt").asText()).isEqualTo(event.occurredAt().toString());
        assertThat(json.has("skillId")).isFalse();
        assertThat(body.getValue()).doesNotContain("Private review comment");
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void mismatchedSubmitterDoesNotReceiveAnotherAuthorsResult() {
        ReviewTask task = ReviewTask.forSuiteVersion(20L, 10L, 5L, "2.0.0", "real-submitter");
        task.setStatus(ReviewTaskStatus.APPROVED);
        when(reviews.findById(30L)).thenReturn(Optional.of(task));
        listener.onReviewCompleted(event(ReviewTaskStatus.APPROVED, "other-user"));
        verifyNoInteractions(notifications, suites, versions, namespaces);
    }

    @Test
    void staleTaskStatusDoesNotProduceAFalseOutcome() {
        ReviewTask task = ReviewTask.forSuiteVersion(20L, 10L, 5L, "2.0.0", "submitter");
        when(reviews.findById(30L)).thenReturn(Optional.of(task));
        listener.onReviewCompleted(event(ReviewTaskStatus.APPROVED, "submitter"));
        verifyNoInteractions(notifications, suites, versions, namespaces);
    }

    @Test
    void notificationFailureIsIsolatedFromTheAlreadyCommittedReview() {
        stub(ReviewTaskStatus.APPROVED, "submitter");
        doThrow(new IllegalStateException("Controlled notification failure"))
                .when(notifications).create(any(), any(), any(), any(), any(), any(), any());
        assertThatCode(() -> listener.onReviewCompleted(event(ReviewTaskStatus.APPROVED, "submitter")))
                .doesNotThrowAnyException();
    }

    @Test
    void resultUsesTheExistingReviewPreferenceAndDoesNotRequireAnEmailAddress() {
        stub(ReviewTaskStatus.REJECTED, "submitter");
        NotificationRepository stored = mock(NotificationRepository.class);
        NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
        when(preferences.isEnabled("submitter", NotificationCategory.REVIEW, NotificationChannel.IN_APP))
                .thenReturn(true);
        var realService = new NotificationService(stored, preferences, Clock.systemUTC());
        new SkillSuiteReviewResultNotificationListener(reviews, suites, versions, namespaces, realService, mapper)
                .onReviewCompleted(event(ReviewTaskStatus.REJECTED, "submitter"));
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(stored).save(saved.capture());
        assertThat(saved.getValue().getRecipientId()).isEqualTo("submitter");
    }

    private SkillSuiteReviewCompletedEvent event(ReviewTaskStatus status, String submitter) {
        return new SkillSuiteReviewCompletedEvent(30L, 10L, 20L, submitter, "reviewer", 5L,
                status, "00000000-0000-0000-0000-000000000030", Instant.parse("2026-11-06T00:00:00Z"));
    }

    private void stub(ReviewTaskStatus status, String submitter) {
        ReviewTask task = ReviewTask.forSuiteVersion(20L, 10L, 5L, "2.0.0", submitter);
        task.setStatus(status);
        task.setReviewComment("Private review comment");
        when(reviews.findById(30L)).thenReturn(Optional.of(task));
        SkillSuite suite = new SkillSuite(5L, "writers", "Old name", "original-creator");
        ReflectionTestUtils.setField(suite, "id", 10L);
        when(suites.findById(10L)).thenReturn(Optional.of(suite));
        SkillSuiteVersion version = new SkillSuiteVersion(10L, "2.0.0", "Current Suite", "Summary",
                SkillVisibility.PUBLIC, "submitter");
        when(versions.findById(20L)).thenReturn(Optional.of(version));
        when(namespaces.findById(5L)).thenReturn(Optional.of(new Namespace("team", "Team", "namespace-owner")));
    }
}
