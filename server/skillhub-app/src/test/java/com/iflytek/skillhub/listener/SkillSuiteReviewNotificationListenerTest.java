package com.iflytek.skillhub.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.event.SkillSuiteReviewSubmittedEvent;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillSuiteReviewNotificationListenerTest {

    @Mock private SkillSuiteRepository suiteRepository;
    @Mock private SkillSuiteVersionRepository versionRepository;
    @Mock private NamespaceRepository namespaceRepository;
    @Mock private RecipientResolver recipientResolver;
    @Mock private NotificationService notificationService;

    private final ObjectMapper mapper = new ObjectMapper();
    private SkillSuiteReviewNotificationListener listener;
    private SkillSuite suite;
    private SkillSuiteVersion version;
    private Namespace namespace;
    private final SkillSuiteReviewSubmittedEvent event =
            new SkillSuiteReviewSubmittedEvent(100L, 10L, 20L, "author", 5L);

    @BeforeEach
    void setUp() {
        listener = listener(notificationService);
        suite = new SkillSuite(5L, "writers", "Previously published name", "author");
        ReflectionTestUtils.setField(suite, "id", 10L);
        version = new SkillSuiteVersion(10L, "2.0.0", "Writing suite", "New summary",
                SkillVisibility.PUBLIC, "author");
        ReflectionTestUtils.setField(version, "id", 20L);
        namespace = new Namespace("team", "Team", "owner");
    }

    @Test
    void teamSubmissionNotifiesOnlyResolvedNamespaceAdministratorsOnceWithTheBoundVersion() throws Exception {
        stubResources();
        when(recipientResolver.resolveNamespaceAdmins(5L))
                .thenReturn(List.of("owner", "admin", "owner"));

        listener.onReviewSubmitted(event);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(eq("owner"), eq(NotificationCategory.REVIEW),
                eq("SUITE_REVIEW_SUBMITTED"), eq("Suite review submitted"), json.capture(),
                eq("REVIEW"), eq(100L));
        verify(notificationService).create(eq("admin"), eq(NotificationCategory.REVIEW),
                eq("SUITE_REVIEW_SUBMITTED"), anyString(), anyString(), eq("REVIEW"), eq(100L));
        var body = mapper.readTree(json.getValue());
        assertThat(body.path("suiteId").asLong()).isEqualTo(10L);
        assertThat(body.path("suiteName").asText()).isEqualTo("Writing suite");
        assertThat(body.path("namespace").asText()).isEqualTo("team");
        assertThat(body.path("slug").asText()).isEqualTo("writers");
        assertThat(body.path("version").asText()).isEqualTo("2.0.0");
        assertThat(body.path("reviewId").asLong()).isEqualTo(100L);
        assertThat(body.path("submitterId").asText()).isEqualTo("author");
        assertThat(body.has("skillId")).isFalse();
        verify(recipientResolver, never()).resolvePlatformSkillAdmins();
        verify(notificationService, never()).create(eq("unrelated-member"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void globalSubmissionNotifiesPlatformSkillAdministratorsInsteadOfNamespaceMembers() {
        namespace.setType(NamespaceType.GLOBAL);
        stubResources();
        when(recipientResolver.resolvePlatformSkillAdmins()).thenReturn(List.of("skill-admin", "super-admin"));

        listener.onReviewSubmitted(event);

        verify(notificationService).create(eq("skill-admin"), eq(NotificationCategory.REVIEW),
                eq("SUITE_REVIEW_SUBMITTED"), anyString(), anyString(), eq("REVIEW"), eq(100L));
        verify(notificationService).create(eq("super-admin"), eq(NotificationCategory.REVIEW),
                eq("SUITE_REVIEW_SUBMITTED"), anyString(), anyString(), eq("REVIEW"), eq(100L));
        verify(recipientResolver, never()).resolveNamespaceAdmins(anyLong());
    }

    @Test
    void missingSuiteDoesNotCreateANotification() {
        when(suiteRepository.findById(10L)).thenReturn(Optional.empty());

        listener.onReviewSubmitted(event);

        verifyNoInteractions(notificationService, recipientResolver);
    }

    @Test
    void mismatchedNamespaceDoesNotExposeSuiteMetadataToAnotherSpace() {
        when(suiteRepository.findById(10L)).thenReturn(Optional.of(suite));

        listener.onReviewSubmitted(new SkillSuiteReviewSubmittedEvent(100L, 10L, 20L, "author", 6L));

        verifyNoInteractions(notificationService, recipientResolver, namespaceRepository, versionRepository);
    }

    @Test
    void mismatchedVersionDoesNotCreateANotificationForAnotherSuite() {
        when(suiteRepository.findById(10L)).thenReturn(Optional.of(suite));
        SkillSuiteVersion otherVersion = new SkillSuiteVersion(11L, "1.0.0", SkillVisibility.PUBLIC, "author");
        when(versionRepository.findById(20L)).thenReturn(Optional.of(otherVersion));

        listener.onReviewSubmitted(event);

        verifyNoInteractions(notificationService, recipientResolver, namespaceRepository);
    }

    @Test
    void oneRecipientFailureDoesNotStopOtherAdministratorsFromReceivingTheRequest() {
        stubResources();
        when(recipientResolver.resolveNamespaceAdmins(5L)).thenReturn(List.of("unavailable-admin", "owner"));
        doThrow(new IllegalStateException("Controlled notification storage failure"))
                .when(notificationService).create(eq("unavailable-admin"), any(), any(), any(), any(), any(), any());

        listener.onReviewSubmitted(event);

        verify(notificationService).create(eq("owner"), eq(NotificationCategory.REVIEW),
                eq("SUITE_REVIEW_SUBMITTED"), anyString(), anyString(), eq("REVIEW"), eq(100L));
    }

    @Test
    void suiteReviewUsesTheExistingReviewPreferenceRatherThanASeparateNotificationSetting() {
        stubResources();
        when(recipientResolver.resolveNamespaceAdmins(5L)).thenReturn(List.of("muted-owner", "admin"));
        NotificationRepository notifications = mock(NotificationRepository.class);
        NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
        when(preferences.isEnabled("muted-owner", NotificationCategory.REVIEW, NotificationChannel.IN_APP))
                .thenReturn(false);
        when(preferences.isEnabled("admin", NotificationCategory.REVIEW, NotificationChannel.IN_APP))
                .thenReturn(true);
        NotificationService service = new NotificationService(notifications, preferences, Clock.systemUTC());

        listener(service).onReviewSubmitted(event);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getRecipientId()).isEqualTo("admin");
        assertThat(saved.getValue().getCategory()).isEqualTo(NotificationCategory.REVIEW);
    }

    @Test
    void maximumLengthSuiteNameRemainsInTheBodyWithoutOverflowingTheNotificationTitle() throws Exception {
        String longName = "套".repeat(256);
        version.setDisplayName(longName);
        stubResources();
        when(recipientResolver.resolveNamespaceAdmins(5L)).thenReturn(List.of("owner"));

        listener.onReviewSubmitted(event);

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(eq("owner"), any(), anyString(), title.capture(), body.capture(),
                eq("REVIEW"), eq(100L));
        assertThat(title.getValue()).hasSizeLessThanOrEqualTo(200);
        assertThat(mapper.readTree(body.getValue()).path("suiteName").asText()).isEqualTo(longName);
    }

    private void stubResources() {
        when(suiteRepository.findById(10L)).thenReturn(Optional.of(suite));
        when(versionRepository.findById(20L)).thenReturn(Optional.of(version));
        when(namespaceRepository.findById(5L)).thenReturn(Optional.of(namespace));
    }

    private SkillSuiteReviewNotificationListener listener(NotificationService notifications) {
        return new SkillSuiteReviewNotificationListener(suiteRepository, versionRepository, namespaceRepository,
                recipientResolver, notifications, mapper);
    }
}
