package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.social.event.SkillSubscribedEvent;
import com.iflytek.skillhub.domain.social.event.SkillUnsubscribedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(MockitoExtension.class)
class SkillSubscriptionServiceTest {

    @Mock private SkillSubscriptionRepository subscriptionRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private NamespaceRepository namespaceRepository;
    @Mock private NamespaceMemberRepository namespaceMemberRepository;
    @Mock private UserAccountRepository userAccountRepository;

    private SkillSubscriptionService service;

    private void allowPublicSubscription(Skill skill) {
        skill.setLatestVersionId(10L);
        when(userAccountRepository.findById("user-1"))
                .thenReturn(Optional.of(new UserAccount("user-1", "User", null, null)));
        Namespace namespace = new Namespace("demo", "Demo", "owner");
        when(namespaceRepository.findById(skill.getNamespaceId())).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(skill.getNamespaceId(), "user-1"))
                .thenReturn(Optional.empty());
    }

    @BeforeEach
    void setUp() {
        service = new SkillSubscriptionService(subscriptionRepository, skillRepository, eventPublisher,
                namespaceRepository, namespaceMemberRepository, userAccountRepository,
                new SubscriptionMetadataAccessPolicy());
    }

    @Test
    void subscribe_createsSubscriptionAndPublishesEvent() {
        Skill skill = new Skill(5L, "public-skill", "owner", com.iflytek.skillhub.domain.skill.SkillVisibility.PUBLIC);
        allowPublicSubscription(skill);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(subscriptionRepository.findBySkillIdAndUserId(1L, "user-1")).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.subscribe(1L, "user-1");

        verify(subscriptionRepository).save(any(SkillSubscription.class));
        verify(skillRepository).incrementSubscriptionCount(1L);
        ArgumentCaptor<SkillSubscribedEvent> captor = ArgumentCaptor.forClass(SkillSubscribedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().skillId()).isEqualTo(1L);
        assertThat(captor.getValue().userId()).isEqualTo("user-1");
    }

    @Test
    void subscribe_idempotent_doesNotDuplicate() {
        Skill skill = new Skill(5L, "public-skill", "owner", com.iflytek.skillhub.domain.skill.SkillVisibility.PUBLIC);
        allowPublicSubscription(skill);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(subscriptionRepository.findBySkillIdAndUserId(1L, "user-1"))
                .thenReturn(Optional.of(mock(SkillSubscription.class)));

        service.subscribe(1L, "user-1");

        verify(subscriptionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void unsubscribe_deletesSubscriptionAndPublishesEvent() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(mock(Skill.class)));
        SkillSubscription existing = mock(SkillSubscription.class);
        when(subscriptionRepository.findBySkillIdAndUserId(1L, "user-1")).thenReturn(Optional.of(existing));

        service.unsubscribe(1L, "user-1");

        verify(subscriptionRepository).delete(existing);
        verify(skillRepository).decrementSubscriptionCount(1L);
        ArgumentCaptor<SkillUnsubscribedEvent> captor = ArgumentCaptor.forClass(SkillUnsubscribedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().skillId()).isEqualTo(1L);
    }

    @Test
    void unsubscribe_noOp_whenNotSubscribed() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(mock(Skill.class)));
        when(subscriptionRepository.findBySkillIdAndUserId(1L, "user-1")).thenReturn(Optional.empty());

        service.unsubscribe(1L, "user-1");

        verify(subscriptionRepository, never()).delete(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void isSubscribed_returnsTrue_whenSubscriptionExists() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(mock(Skill.class)));
        when(subscriptionRepository.findBySkillIdAndUserId(1L, "user-1"))
                .thenReturn(Optional.of(mock(SkillSubscription.class)));

        assertThat(service.isSubscribed(1L, "user-1")).isTrue();
    }

    @Test
    void isSubscribed_returnsFalse_whenNoSubscription() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(mock(Skill.class)));
        when(subscriptionRepository.findBySkillIdAndUserId(1L, "user-1")).thenReturn(Optional.empty());

        assertThat(service.isSubscribed(1L, "user-1")).isFalse();
    }

    @Test
    void subscribe_rejectsInactiveAccountWithoutMutation() {
        Skill skill = new Skill(5L, "private-skill", "owner", com.iflytek.skillhub.domain.skill.SkillVisibility.PRIVATE);
        skill.setLatestVersionId(10L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        UserAccount account = new UserAccount("user-1", "User", null, null);
        account.setStatus(UserStatus.DISABLED);
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(account));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.subscribe(1L, "user-1"))
                .isInstanceOf(DomainForbiddenException.class);

        verifyNoInteractions(subscriptionRepository);
        verify(skillRepository, never()).incrementSubscriptionCount(anyLong());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void subscribe_rejectsRemovedMemberOfArchivedNamespaceWithoutMutation() {
        Skill skill = new Skill(5L, "public-skill", "owner", com.iflytek.skillhub.domain.skill.SkillVisibility.PUBLIC);
        skill.setLatestVersionId(10L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(userAccountRepository.findById("user-1"))
                .thenReturn(Optional.of(new UserAccount("user-1", "User", null, null)));
        Namespace namespace = new Namespace("archived", "Archived", "owner");
        namespace.setStatus(NamespaceStatus.ARCHIVED);
        when(namespaceRepository.findById(5L)).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(5L, "user-1")).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.subscribe(1L, "user-1"))
                .isInstanceOf(DomainForbiddenException.class);

        verifyNoInteractions(subscriptionRepository);
        verify(skillRepository, never()).incrementSubscriptionCount(anyLong());
        verifyNoInteractions(eventPublisher);
    }

    static Stream<DeniedSubscription> deniedSubscriptions() {
        return Stream.of(
                new DeniedSubscription("private member", SkillVisibility.PRIVATE, false, NamespaceStatus.ACTIVE,
                        NamespaceRole.MEMBER),
                new DeniedSubscription("private nonmember", SkillVisibility.PRIVATE, false, NamespaceStatus.ACTIVE,
                        null),
                new DeniedSubscription("private cross namespace member", SkillVisibility.PRIVATE, false,
                        NamespaceStatus.ACTIVE, null),
                new DeniedSubscription("hidden public", SkillVisibility.PUBLIC, true, NamespaceStatus.ACTIVE,
                        NamespaceRole.MEMBER)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("deniedSubscriptions")
    void subscribe_rejectsUnauthorizedMetadataWithoutAnyMutation(DeniedSubscription scenario) {
        Skill skill = new Skill(5L, "restricted", "owner", scenario.visibility());
        skill.setLatestVersionId(10L);
        skill.setHidden(scenario.hidden());
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(userAccountRepository.findById("user-1"))
                .thenReturn(Optional.of(new UserAccount("user-1", "User", null, null)));
        Namespace namespace = new Namespace("team", "Team", "owner");
        namespace.setStatus(scenario.namespaceStatus());
        when(namespaceRepository.findById(5L)).thenReturn(Optional.of(namespace));
        if (scenario.role() == null) {
            when(namespaceMemberRepository.findByNamespaceIdAndUserId(5L, "user-1"))
                    .thenReturn(Optional.empty());
        } else {
            when(namespaceMemberRepository.findByNamespaceIdAndUserId(5L, "user-1"))
                    .thenReturn(Optional.of(new NamespaceMember(5L, "user-1", scenario.role())));
        }

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.subscribe(1L, "user-1"))
                .isInstanceOf(DomainForbiddenException.class);

        verifyNoInteractions(subscriptionRepository);
        verify(skillRepository, never()).incrementSubscriptionCount(anyLong());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void subscribe_allowsPublicArchivedSkillBecauseMetadataPurposeDoesNotRequireActiveSkill() {
        Skill skill = new Skill(5L, "archived-skill", "owner", SkillVisibility.PUBLIC);
        skill.setStatus(com.iflytek.skillhub.domain.skill.SkillStatus.ARCHIVED);
        skill.setLatestVersionId(10L);
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(userAccountRepository.findById("user-1"))
                .thenReturn(Optional.of(new UserAccount("user-1", "User", null, null)));
        when(namespaceRepository.findById(5L)).thenReturn(Optional.of(new Namespace("team", "Team", "owner")));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(5L, "user-1")).thenReturn(Optional.empty());
        when(subscriptionRepository.findBySkillIdAndUserId(1L, "user-1")).thenReturn(Optional.empty());

        service.subscribe(1L, "user-1");

        verify(subscriptionRepository).save(any(SkillSubscription.class));
        verify(skillRepository).incrementSubscriptionCount(1L);
        verify(eventPublisher).publishEvent(any(SkillSubscribedEvent.class));
    }

    @Test
    void getAndDeleteDoNotConsultMetadataAuthorizationDependencies() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(mock(Skill.class)));
        SkillSubscription existing = mock(SkillSubscription.class);
        when(subscriptionRepository.findBySkillIdAndUserId(1L, "user-1"))
                .thenReturn(Optional.of(existing));

        assertThat(service.isSubscribed(1L, "user-1")).isTrue();
        service.unsubscribe(1L, "user-1");

        verifyNoInteractions(namespaceRepository, namespaceMemberRepository, userAccountRepository);
        verify(subscriptionRepository).delete(existing);
    }

    record DeniedSubscription(String label, SkillVisibility visibility, boolean hidden,
                              NamespaceStatus namespaceStatus, NamespaceRole role) {
        @Override public String toString() { return label; }
    }
}
