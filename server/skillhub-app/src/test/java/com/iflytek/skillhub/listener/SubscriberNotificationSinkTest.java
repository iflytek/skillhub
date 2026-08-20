package com.iflytek.skillhub.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.event.SkillVersionYankedEvent;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.social.SkillSubscriptionService;
import com.iflytek.skillhub.domain.social.SubscriptionMetadataAccessPolicy;
import com.iflytek.skillhub.domain.social.SubscriptionRecipientEligibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.notification.domain.Notification;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.domain.NotificationChannel;
import com.iflytek.skillhub.notification.service.NotificationDispatcher;
import com.iflytek.skillhub.notification.service.NotificationPreferenceService;
import com.iflytek.skillhub.notification.service.NotificationService;
import com.iflytek.skillhub.notification.sse.SseEmitterManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriberNotificationSinkTest {

    private static final Long SKILL_ID = 1L;
    private static final Long NAMESPACE_ID = 5L;
    private static final Long VERSION_ID = 10L;
    private static final Instant CREATED_AT = Instant.parse("2026-08-19T20:30:00Z");

    @Mock SkillRepository skillRepository;
    @Mock SkillVersionRepository skillVersionRepository;
    @Mock NamespaceRepository namespaceRepository;
    @Mock RecipientResolver recipientResolver;
    @Mock SkillSubscriptionService subscriptionService;
    @Mock UserAccountRepository accountRepository;
    @Mock NamespaceMemberRepository memberRepository;
    @Mock NotificationService notificationService;
    @Mock NotificationPreferenceService preferenceService;
    @Mock SseEmitterManager sseEmitterManager;

    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        SubscriptionRecipientEligibility eligibility = new SubscriptionRecipientEligibility(
                accountRepository, memberRepository, new SubscriptionMetadataAccessPolicy());
        NotificationDispatcher dispatcher = new NotificationDispatcher(
                notificationService, preferenceService, sseEmitterManager);
        listener = new NotificationEventListener(skillRepository, skillVersionRepository, namespaceRepository,
                recipientResolver, dispatcher, subscriptionService, new ObjectMapper(), eligibility);
    }

    @Test
    void publishPersistsAndPushesOnlyCurrentEligibleNonPublisherAcrossAuthorizationMatrix() {
        Skill skill = skill(SkillVisibility.PRIVATE, false, VERSION_ID);
        Namespace namespace = namespace(NamespaceStatus.ACTIVE);
        List<String> candidates = List.of("publisher", "current-admin", "stale-removed", "inactive",
                "missing", "private-member", "cross-namespace", "platform-super-admin");
        arrangeEvent(skill, namespace, candidates);
        when(accountRepository.findByIdIn(candidates)).thenReturn(List.of(
                account("publisher"), account("current-admin"), account("stale-removed"),
                inactiveAccount("inactive"), account("private-member"), account("cross-namespace"),
                account("platform-super-admin")));
        when(memberRepository.findByNamespaceIdAndUserIdIn(NAMESPACE_ID, candidates)).thenReturn(List.of(
                member("current-admin", NamespaceRole.ADMIN),
                member("private-member", NamespaceRole.MEMBER)));
        enablePersistenceFor("current-admin", "SUBSCRIPTION_NEW_VERSION");

        listener.onSkillPublishedForSubscribers(new SkillPublishedEvent(SKILL_ID, VERSION_ID, "publisher"));

        String body = "{\"skillId\":1,\"skillName\":\"Test Skill\",\"slug\":\"test-skill\",\"namespace\":\"demo\"}";
        verify(notificationService).create("current-admin", NotificationCategory.PUBLISH,
                "SUBSCRIPTION_NEW_VERSION", "Skill updated: Test Skill", body, "SKILL", SKILL_ID);
        assertSingleSse("current-admin", "SUBSCRIPTION_NEW_VERSION", body);
        verify(accountRepository).findByIdIn(candidates);
        verify(memberRepository).findByNamespaceIdAndUserIdIn(NAMESPACE_ID, candidates);
    }

    @Test
    void hiddenPublishPersistsAndPushesOnlyManagerWhileOrdinaryAndPlatformOnlyCandidatesStayAtZero() {
        Skill skill = skill(SkillVisibility.PUBLIC, true, VERSION_ID);
        Namespace namespace = namespace(NamespaceStatus.ACTIVE);
        List<String> candidates = List.of("manager", "ordinary-member", "platform-super-admin");
        arrangeEvent(skill, namespace, candidates);
        when(accountRepository.findByIdIn(candidates)).thenReturn(List.of(
                account("manager"), account("ordinary-member"), account("platform-super-admin")));
        when(memberRepository.findByNamespaceIdAndUserIdIn(NAMESPACE_ID, candidates)).thenReturn(List.of(
                member("manager", NamespaceRole.ADMIN), member("ordinary-member", NamespaceRole.MEMBER)));
        enablePersistenceFor("manager", "SUBSCRIPTION_NEW_VERSION");

        listener.onSkillPublishedForSubscribers(new SkillPublishedEvent(SKILL_ID, VERSION_ID, "publisher"));

        String body = "{\"skillId\":1,\"skillName\":\"Test Skill\",\"slug\":\"test-skill\",\"namespace\":\"demo\"}";
        verify(notificationService).create("manager", NotificationCategory.PUBLISH,
                "SUBSCRIPTION_NEW_VERSION", "Skill updated: Test Skill", body, "SKILL", SKILL_ID);
        assertSingleSse("manager", "SUBSCRIPTION_NEW_VERSION", body);
        verify(accountRepository).findByIdIn(candidates);
        verify(memberRepository).findByNamespaceIdAndUserIdIn(NAMESPACE_ID, candidates);
    }

    @ParameterizedTest(name = "yank wasPublished with fallback={0} reaches only current archived-namespace member")
    @ValueSource(booleans = {true, false})
    void yankPersistsAndPushesOnlyCurrentMemberForFallbackAndNoFallback(boolean hasFallback) {
        Skill skill = skill(SkillVisibility.PUBLIC, false, hasFallback ? 9L : null);
        Namespace namespace = namespace(NamespaceStatus.ARCHIVED);
        List<String> candidates = List.of("actor", "current", "removed", "inactive", "missing");
        arrangeEvent(skill, namespace, candidates);
        when(accountRepository.findByIdIn(candidates)).thenReturn(List.of(
                account("actor"), account("current"), account("removed"), inactiveAccount("inactive")));
        when(memberRepository.findByNamespaceIdAndUserIdIn(NAMESPACE_ID, candidates)).thenReturn(List.of(
                member("actor", NamespaceRole.ADMIN), member("current", NamespaceRole.MEMBER)));
        enablePersistenceFor("current", "SUBSCRIPTION_VERSION_YANKED");

        listener.onSkillVersionYankedForSubscribers(
                new SkillVersionYankedEvent(SKILL_ID, VERSION_ID, "actor", true));

        String body = "{\"skillId\":1,\"skillName\":\"Test Skill\",\"slug\":\"test-skill\",\"namespace\":\"demo\"}";
        verify(notificationService).create("current", NotificationCategory.PUBLISH,
                "SUBSCRIPTION_VERSION_YANKED", "Skill version yanked: Test Skill", body, "SKILL", SKILL_ID);
        assertSingleSse("current", "SUBSCRIPTION_VERSION_YANKED", body);
        verify(accountRepository).findByIdIn(candidates);
        verify(memberRepository).findByNamespaceIdAndUserIdIn(NAMESPACE_ID, candidates);
    }

    @Test
    void yankWithoutVerifiedPublishedPreStateProducesNoPersistenceOrSse() {
        Skill skill = skill(SkillVisibility.PUBLIC, false, null);
        Namespace namespace = namespace(NamespaceStatus.ACTIVE);
        List<String> candidates = List.of("current");
        arrangeEvent(skill, namespace, candidates);
        when(accountRepository.findByIdIn(candidates)).thenReturn(List.of(account("current")));
        when(memberRepository.findByNamespaceIdAndUserIdIn(NAMESPACE_ID, candidates)).thenReturn(List.of());

        listener.onSkillVersionYankedForSubscribers(
                new SkillVersionYankedEvent(SKILL_ID, VERSION_ID, "actor", false));

        verifyNoInteractions(notificationService, preferenceService, sseEmitterManager);
        verify(accountRepository).findByIdIn(candidates);
        verify(memberRepository).findByNamespaceIdAndUserIdIn(NAMESPACE_ID, candidates);
    }

    @ParameterizedTest(name = "{0} batch failure happens before every final sink")
    @EnumSource(BatchFailure.class)
    void authoritativeBatchFailureProducesNoPartialPersistenceOrSse(BatchFailure failure) {
        Skill skill = skill(SkillVisibility.PUBLIC, false, VERSION_ID);
        Namespace namespace = namespace(NamespaceStatus.ACTIVE);
        List<String> candidates = List.of("first", "second");
        when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));
        when(subscriptionService.findSubscribersBySkillId(SKILL_ID)).thenReturn(candidates);
        if (failure == BatchFailure.NAMESPACE) {
            when(namespaceRepository.findById(NAMESPACE_ID)).thenThrow(new IllegalStateException("namespace batch"));
        } else {
            when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(namespace));
            if (failure == BatchFailure.ACCOUNT) {
                when(accountRepository.findByIdIn(candidates)).thenThrow(new IllegalStateException("account batch"));
            } else {
                when(accountRepository.findByIdIn(candidates)).thenReturn(List.of(account("first"), account("second")));
                when(memberRepository.findByNamespaceIdAndUserIdIn(NAMESPACE_ID, candidates))
                        .thenThrow(new IllegalStateException("membership batch"));
            }
        }

        assertThatThrownBy(() -> listener.onSkillPublishedForSubscribers(
                new SkillPublishedEvent(SKILL_ID, VERSION_ID, "publisher")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(failure.name().toLowerCase());

        verifyNoInteractions(notificationService, preferenceService, sseEmitterManager);
        verify(namespaceRepository, times(1)).findById(NAMESPACE_ID);
        if (failure == BatchFailure.NAMESPACE) {
            verify(accountRepository, never()).findByIdIn(anyList());
            verify(memberRepository, never()).findByNamespaceIdAndUserIdIn(any(), anyCollection());
        } else {
            verify(accountRepository, times(1)).findByIdIn(candidates);
            verify(memberRepository, failure == BatchFailure.MEMBERSHIP ? times(1) : never())
                    .findByNamespaceIdAndUserIdIn(eq(NAMESPACE_ID), anyCollection());
        }
    }

    private void arrangeEvent(Skill skill, Namespace namespace, List<String> candidates) {
        when(skillRepository.findById(SKILL_ID)).thenReturn(Optional.of(skill));
        when(subscriptionService.findSubscribersBySkillId(SKILL_ID)).thenReturn(candidates);
        when(namespaceRepository.findById(NAMESPACE_ID)).thenReturn(Optional.of(namespace));
    }

    private void enablePersistenceFor(String recipient, String eventType) {
        when(preferenceService.isEnabled(recipient, NotificationCategory.PUBLISH, NotificationChannel.IN_APP))
                .thenReturn(true);
        when(notificationService.create(eq(recipient), eq(NotificationCategory.PUBLISH), eq(eventType),
                any(String.class), any(String.class), eq("SKILL"), eq(SKILL_ID)))
                .thenAnswer(invocation -> notification(recipient, eventType,
                        invocation.getArgument(3), invocation.getArgument(4)));
    }

    private void assertSingleSse(String recipient, String eventType, String body) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(sseEmitterManager).push(eq(recipient), payload.capture());
        assertThat(payload.getValue())
                .containsEntry("id", 42L)
                .containsEntry("category", "PUBLISH")
                .containsEntry("eventType", eventType)
                .containsEntry("bodyJson", body)
                .containsEntry("entityType", "SKILL")
                .containsEntry("entityId", SKILL_ID);
    }

    private Skill skill(SkillVisibility visibility, boolean hidden, Long latestVersionId) {
        Skill skill = new Skill(NAMESPACE_ID, "test-skill", "publisher", visibility);
        skill.setDisplayName("Test Skill");
        skill.setHidden(hidden);
        skill.setLatestVersionId(latestVersionId);
        setId(skill, SKILL_ID);
        return skill;
    }

    private Namespace namespace(NamespaceStatus status) {
        Namespace namespace = new Namespace("demo", "Demo", "publisher");
        namespace.setStatus(status);
        return namespace;
    }

    private UserAccount account(String id) {
        return new UserAccount(id, id, null, null);
    }

    private UserAccount inactiveAccount(String id) {
        UserAccount account = account(id);
        account.setStatus(UserStatus.DISABLED);
        return account;
    }

    private NamespaceMember member(String userId, NamespaceRole role) {
        return new NamespaceMember(NAMESPACE_ID, userId, role);
    }

    private Notification notification(String recipient, String eventType, String title, String body) {
        Notification notification = new Notification(recipient, NotificationCategory.PUBLISH, eventType,
                title, body, "SKILL", SKILL_ID, CREATED_AT);
        setId(notification, 42L);
        return notification;
    }

    private void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private enum BatchFailure {
        ACCOUNT,
        NAMESPACE,
        MEMBERSHIP
    }
}
