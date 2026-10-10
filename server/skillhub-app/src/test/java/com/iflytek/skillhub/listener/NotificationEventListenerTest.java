package com.iflytek.skillhub.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.event.*;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.social.SkillSubscriptionService;
import com.iflytek.skillhub.domain.social.SubscriptionRecipientEligibility;
import com.iflytek.skillhub.domain.social.SubscriptionMetadataAccessPolicy;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock SkillRepository skillRepository;
    @Mock SkillVersionRepository skillVersionRepository;
    @Mock NamespaceRepository namespaceRepository;
    @Mock RecipientResolver recipientResolver;
    @Mock NotificationService notificationService;
    @Mock ObjectMapper objectMapper;
    @Mock SkillSubscriptionService skillSubscriptionService;
    @Mock UserAccountRepository userAccountRepository;
    @Mock NamespaceMemberRepository namespaceMemberRepository;

    @InjectMocks
    NotificationEventListener listener;

    @org.junit.jupiter.api.BeforeEach
    void setUpListener() {
        listener = new NotificationEventListener(skillRepository, skillVersionRepository, namespaceRepository,
                recipientResolver, notificationService, skillSubscriptionService, objectMapper,
                new SubscriptionRecipientEligibility(userAccountRepository, namespaceMemberRepository,
                        new SubscriptionMetadataAccessPolicy()));
    }

    private Skill mockSkill(Long id) {
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(id);
        when(skill.getNamespaceId()).thenReturn(5L);
        when(skill.getDisplayName()).thenReturn("Test Skill");
        when(skill.getSlug()).thenReturn("test-skill");
        return skill;
    }

    private Skill skill(Long id, String ownerId, String createdBy) {
        Skill skill = new Skill(5L, "test-skill", ownerId, SkillVisibility.PUBLIC);
        skill.setCreatedBy(createdBy);
        skill.setDisplayName("Test Skill");
        setId(skill, id);
        return skill;
    }

    private void setId(Skill skill, Long id) {
        try {
            java.lang.reflect.Field field = Skill.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(skill, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void mockNamespace() {
        Namespace namespace = mock(Namespace.class);
        when(namespace.getSlug()).thenReturn("demo");
        when(namespaceRepository.findById(5L)).thenReturn(Optional.of(namespace));
    }

    @Test
    void onSkillPublished_shouldDispatchToPublisher() throws Exception {
        Skill skill = skill(1L, "publisher-1", "publisher-1");
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        mockNamespace();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        listener.onSkillPublished(new SkillPublishedEvent(1L, 10L, "publisher-1"));

        verify(notificationService).create(eq("publisher-1"), eq(NotificationCategory.PUBLISH),
                eq("SKILL_PUBLISHED"), anyString(), anyString(), eq("SKILL"), eq(1L));
    }

    @Test
    void onSkillPublished_shouldSkipWhenPublisherIsNotSkillOwner() throws Exception {
        Skill skill = skill(1L, "submitter-1", "submitter-1");
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));

        listener.onSkillPublished(new SkillPublishedEvent(1L, 10L, "reviewer-1"));

        verifyNoInteractions(notificationService);
    }

    @Test
    void onSkillPublished_shouldSkipPromotedSkillCopyCreatedByReviewer() throws Exception {
        Skill skill = skill(1L, "submitter-1", "reviewer-1");
        skill.setSourceSkillId(99L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));

        listener.onSkillPublished(new SkillPublishedEvent(1L, 10L, "reviewer-1"));

        verifyNoInteractions(notificationService);
    }

    @Test
    void onSkillPublished_shouldSkipWhenSkillNotFound() {
        when(skillRepository.findById(99L)).thenReturn(Optional.empty());

        listener.onSkillPublished(new SkillPublishedEvent(99L, 10L, "publisher-1"));

        verifyNoInteractions(notificationService);
    }

    @Test
    void onReviewSubmitted_shouldDispatchToNamespaceAdmins() throws Exception {
        Skill skill = mockSkill(1L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        mockNamespace();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(recipientResolver.resolveNamespaceAdmins(5L)).thenReturn(List.of("admin-1", "admin-2"));

        listener.onReviewSubmitted(new ReviewSubmittedEvent(100L, 1L, 10L, "submitter-1", 5L));

        verify(notificationService, times(2)).create(anyString(), eq(NotificationCategory.REVIEW),
                eq("REVIEW_SUBMITTED"), anyString(), anyString(), eq("REVIEW"), eq(100L));
        verify(notificationService).create(eq("admin-1"), any(), any(), any(), any(), any(), any());
        verify(notificationService).create(eq("admin-2"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void onProfileReviewSubmitted_shouldDispatchToPlatformUserAdmins() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(recipientResolver.resolvePlatformUserAdmins())
                .thenReturn(List.of("user-admin-1", "super-admin-1", "user-admin-1"));

        listener.onProfileReviewSubmitted(
                new ProfileReviewSubmittedEvent(77L, "submitter-1", List.of("displayName")));

        verify(notificationService, times(2)).create(anyString(), eq(NotificationCategory.REVIEW),
                eq("PROFILE_REVIEW_SUBMITTED"), anyString(), anyString(), eq("PROFILE_REVIEW"), eq(77L));
        verify(notificationService).create(eq("user-admin-1"), any(), any(), any(), any(), any(), any());
        verify(notificationService).create(eq("super-admin-1"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void onReviewApproved_shouldDispatchToSubmitter() throws Exception {
        Skill skill = mockSkill(1L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        mockNamespace();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        listener.onReviewApproved(new ReviewApprovedEvent(100L, 1L, 10L, "reviewer-1", "submitter-1"));

        verify(notificationService).create(eq("submitter-1"), eq(NotificationCategory.REVIEW),
                eq("REVIEW_APPROVED"), anyString(), anyString(), eq("SKILL"), eq(1L));
    }

    @Test
    void onPromotionSubmitted_shouldDispatchToPlatformAdmins() throws Exception {
        Skill skill = mockSkill(1L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        mockNamespace();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(recipientResolver.resolvePlatformSkillAdmins())
                .thenReturn(List.of("platform-admin-1", "super-admin-1"));

        listener.onPromotionSubmitted(new PromotionSubmittedEvent(200L, 1L, 10L, "submitter-1"));

        verify(notificationService, times(2)).create(anyString(), eq(NotificationCategory.PROMOTION),
                eq("PROMOTION_SUBMITTED"), anyString(), anyString(), eq("PROMOTION"), eq(200L));
        verify(notificationService).create(eq("platform-admin-1"), any(), any(), any(), any(), any(), any());
        verify(notificationService).create(eq("super-admin-1"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void onPromotionSubmitted_shouldDispatchOncePerUniqueRecipient() throws Exception {
        Skill skill = mockSkill(1L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        mockNamespace();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(recipientResolver.resolvePlatformSkillAdmins())
                .thenReturn(List.of("platform-admin-1", "platform-admin-1"));

        listener.onPromotionSubmitted(new PromotionSubmittedEvent(200L, 1L, 10L, "submitter-1"));

        verify(notificationService, times(1)).create(eq("platform-admin-1"), eq(NotificationCategory.PROMOTION),
                eq("PROMOTION_SUBMITTED"), anyString(), anyString(), eq("PROMOTION"), eq(200L));
    }

    @Test
    void onPromotionApproved_shouldDispatchToSubmitterWhenReviewerIsSubmitter() throws Exception {
        Skill skill = mockSkill(1L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        mockNamespace();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        listener.onPromotionApproved(new PromotionApprovedEvent(200L, 1L, "self-admin", "self-admin"));

        verify(notificationService).create(eq("self-admin"), eq(NotificationCategory.PROMOTION),
                eq("PROMOTION_APPROVED"), anyString(), anyString(), eq("SKILL"), eq(1L));
    }

    @Test
    void onPromotionRejected_shouldDispatchToSubmitterWhenReviewerIsSubmitter() throws Exception {
        Skill skill = mockSkill(1L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        mockNamespace();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        listener.onPromotionRejected(new PromotionRejectedEvent(200L, 1L, "self-admin", "self-admin", "not ready"));

        verify(notificationService).create(eq("self-admin"), eq(NotificationCategory.PROMOTION),
                eq("PROMOTION_REJECTED"), anyString(), anyString(), eq("SKILL"), eq(1L));
    }

    @Test
    void onReportResolved_shouldDispatchToReporter() throws Exception {
        Skill skill = mockSkill(1L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        mockNamespace();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        listener.onReportResolved(new ReportResolvedEvent(300L, 1L, "handler-1", "reporter-1", "DISMISSED"));

        verify(notificationService).create(eq("reporter-1"), eq(NotificationCategory.REPORT),
                eq("REPORT_RESOLVED"), anyString(), anyString(), eq("SKILL"), eq(1L));
    }

    @Test
    void publishSubscriberFanoutExcludesInactiveAccount() {
        Skill skill = skill(1L, "owner", "owner");
        skill.setLatestVersionId(10L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(skillSubscriptionService.findSubscribersBySkillId(1L)).thenReturn(List.of("inactive"));
        UserAccount inactive = new UserAccount("inactive", "Inactive", null, null);
        inactive.setStatus(UserStatus.DISABLED);
        when(userAccountRepository.findByIdIn(List.of("inactive"))).thenReturn(List.of(inactive));
        mockNamespace();

        listener.onSkillPublishedForSubscribers(new SkillPublishedEvent(1L, 10L, "owner"));

        verifyNoInteractions(notificationService);
    }

    @Test
    void publishSubscriberFanoutFailsClosedBeforeDispatchWhenAccountBatchFails() {
        Skill skill = skill(1L, "owner", "owner");
        skill.setLatestVersionId(10L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(skillSubscriptionService.findSubscribersBySkillId(1L)).thenReturn(List.of("user-1", "user-2"));
        when(userAccountRepository.findByIdIn(anyList())).thenThrow(new IllegalStateException("account batch unavailable"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                listener.onSkillPublishedForSubscribers(new SkillPublishedEvent(1L, 10L, "owner")))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(notificationService);
    }

    @Test
    void publishSubscriberFanoutDispatchesOnlyEligibleNonPublisherWithExactPayload() throws Exception {
        Skill skill = skill(1L, "publisher", "publisher");
        skill.setLatestVersionId(10L);
        skill.setVisibility(SkillVisibility.PRIVATE);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(skillSubscriptionService.findSubscribersBySkillId(1L))
                .thenReturn(List.of("publisher", "admin", "member", "inactive", "missing"));
        UserAccount publisher = new UserAccount("publisher", "Publisher", null, null);
        UserAccount admin = new UserAccount("admin", "Admin", null, null);
        UserAccount member = new UserAccount("member", "Member", null, null);
        UserAccount inactive = new UserAccount("inactive", "Inactive", null, null);
        inactive.setStatus(UserStatus.DISABLED);
        when(userAccountRepository.findByIdIn(anyList())).thenReturn(List.of(publisher, admin, member, inactive));
        when(namespaceMemberRepository.findByNamespaceIdAndUserIdIn(eq(5L), anyCollection()))
                .thenReturn(List.of(new NamespaceMember(5L, "admin", NamespaceRole.ADMIN),
                        new NamespaceMember(5L, "member", NamespaceRole.MEMBER)));
        mockNamespace();
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"skillId\":1,\"version\":\"1.0.0\"}");

        listener.onSkillPublishedForSubscribers(new SkillPublishedEvent(1L, 10L, "publisher"));

        verify(notificationService).create("admin", NotificationCategory.PUBLISH, "SUBSCRIPTION_NEW_VERSION",
                "Skill updated: Test Skill", "{\"skillId\":1,\"version\":\"1.0.0\"}", "SKILL", 1L);
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void yankWithoutFallbackUsesVerifiedPreYankPublicationAndExcludesActor() throws Exception {
        Skill skill = skill(1L, "owner", "owner");
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(skillSubscriptionService.findSubscribersBySkillId(1L)).thenReturn(List.of("actor", "subscriber"));
        when(userAccountRepository.findByIdIn(anyList())).thenReturn(List.of(
                new UserAccount("actor", "Actor", null, null),
                new UserAccount("subscriber", "Subscriber", null, null)));
        mockNamespace();
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"skillId\":1,\"versionId\":10}");

        listener.onSkillVersionYankedForSubscribers(new SkillVersionYankedEvent(1L, 10L, "actor", true));

        verify(notificationService).create("subscriber", NotificationCategory.PUBLISH, "SUBSCRIPTION_VERSION_YANKED",
                "Skill version yanked: Test Skill", "{\"skillId\":1,\"versionId\":10}", "SKILL", 1L);
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void yankDoesNotDispatchWhenEventDoesNotVerifyPublishedPreState() {
        Skill skill = skill(1L, "owner", "owner");
        skill.setLatestVersionId(9L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(skillSubscriptionService.findSubscribersBySkillId(1L)).thenReturn(List.of("subscriber"));
        when(userAccountRepository.findByIdIn(anyList())).thenReturn(List.of(
                new UserAccount("subscriber", "Subscriber", null, null)));
        mockNamespace();

        listener.onSkillVersionYankedForSubscribers(new SkillVersionYankedEvent(1L, 10L, "actor", false));

        verifyNoInteractions(notificationService);
    }

    @Test
    void publishFanoutFailsClosedBeforeDispatchWhenNamespaceReadFails() {
        Skill skill = skill(1L, "owner", "owner");
        skill.setLatestVersionId(10L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(skillSubscriptionService.findSubscribersBySkillId(1L)).thenReturn(List.of("user-1"));
        when(namespaceRepository.findById(5L)).thenThrow(new IllegalStateException("namespace unavailable"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                listener.onSkillPublishedForSubscribers(new SkillPublishedEvent(1L, 10L, "owner")))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(notificationService);
    }

    @Test
    void yankFanoutFailsClosedBeforeDispatchWhenMembershipBatchFails() {
        Skill skill = skill(1L, "owner", "owner");
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(skillSubscriptionService.findSubscribersBySkillId(1L)).thenReturn(List.of("user-1", "user-2"));
        when(userAccountRepository.findByIdIn(anyList())).thenReturn(List.of(
                new UserAccount("user-1", "One", null, null),
                new UserAccount("user-2", "Two", null, null)));
        when(namespaceRepository.findById(5L))
                .thenReturn(Optional.of(new Namespace("demo", "Demo", "owner")));
        when(namespaceMemberRepository.findByNamespaceIdAndUserIdIn(eq(5L), anyCollection()))
                .thenThrow(new IllegalStateException("membership unavailable"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                listener.onSkillVersionYankedForSubscribers(new SkillVersionYankedEvent(1L, 10L, "actor", true)))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(notificationService);
    }

    @Test
    void archivedNamespaceRemovedSubscriberIsRejectedButCurrentMemberReceivesYank() throws Exception {
        Skill skill = skill(1L, "owner", "owner");
        skill.setStatus(com.iflytek.skillhub.domain.skill.SkillStatus.ARCHIVED);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(skillSubscriptionService.findSubscribersBySkillId(1L)).thenReturn(List.of("current", "removed"));
        when(userAccountRepository.findByIdIn(anyList())).thenReturn(List.of(
                new UserAccount("current", "Current", null, null),
                new UserAccount("removed", "Removed", null, null)));
        Namespace namespace = new Namespace("archived", "Archived", "owner");
        namespace.setStatus(NamespaceStatus.ARCHIVED);
        when(namespaceRepository.findById(5L)).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserIdIn(eq(5L), anyCollection()))
                .thenReturn(List.of(new NamespaceMember(5L, "current", NamespaceRole.MEMBER)));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        listener.onSkillVersionYankedForSubscribers(new SkillVersionYankedEvent(1L, 10L, "actor", true));

        verify(notificationService).create(eq("current"), eq(NotificationCategory.PUBLISH),
                eq("SUBSCRIPTION_VERSION_YANKED"), anyString(), eq("{}"), eq("SKILL"), eq(1L));
        verifyNoMoreInteractions(notificationService);
    }
}
