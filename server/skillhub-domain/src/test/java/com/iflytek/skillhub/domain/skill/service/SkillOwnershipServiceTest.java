package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.event.SkillOwnerChangedEvent;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillOwnershipServiceTest {
    private final SkillRepository skills = mock(SkillRepository.class);
    private final NamespaceRepository namespaces = mock(NamespaceRepository.class);
    private final NamespaceMemberRepository members = mock(NamespaceMemberRepository.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final PromotionRequestRepository promotions = mock(PromotionRequestRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final SkillOwnershipService service =
            new SkillOwnershipService(skills, namespaces, members, users, promotions, events);
    private final Namespace namespace = mock(Namespace.class);
    private final UserAccount successor = new UserAccount("successor", "Successor", null, null);
    private Skill skill;

    @BeforeEach
    void setup() {
        skill = skill(1L, 2L, "leaver");
        when(skills.findByNamespaceIdAndSlugAndOwnerId(2L, "foo", "leaver")).thenReturn(Optional.of(skill));
        when(skills.findLockedById(1L)).thenReturn(Optional.of(skill));
        when(namespace.getStatus()).thenReturn(NamespaceStatus.ACTIVE);
        when(namespaces.findById(2L)).thenReturn(Optional.of(namespace));
        when(users.findById("leaver")).thenReturn(Optional.of(new UserAccount("leaver", "Leaver", null, null)));
        when(users.findById("successor")).thenReturn(Optional.of(successor));
        when(members.findByNamespaceIdAndUserId(2L, "successor")).thenReturn(Optional.of(mock(NamespaceMember.class)));
    }

    @Test
    void namespaceOwnerHandsSkillToActiveMember() {
        List<Skill> moved = transferAsOwner();

        assertThat(moved).containsExactly(skill);
        assertThat(skill.getOwnerId()).isEqualTo("successor");
        assertThat(skill.getUpdatedBy()).isEqualTo("ns-owner");
        verify(skills).save(skill);
        verify(skills).flush();
        verify(events).publishEvent(new SkillOwnerChangedEvent(1L));
    }

    @Test
    void superAdminNeedsNoNamespaceRole() {
        service.transfer(2L, "foo", "leaver", "successor", "root", Map.of(), Set.of("SUPER_ADMIN"));

        assertThat(skill.getOwnerId()).isEqualTo("successor");
    }

    @Test
    void namespaceAdminAndCurrentOwnerCannotTransfer() {
        assertRejected(() -> service.transfer(2L, "foo", "leaver", "successor", "ns-admin",
                Map.of(2L, NamespaceRole.ADMIN), Set.of()), "error.skill.owner.transfer.noPermission");
        assertRejected(() -> service.transfer(2L, "foo", "leaver", "successor", "leaver",
                Map.of(2L, NamespaceRole.MEMBER), Set.of()), "error.skill.owner.transfer.noPermission");
    }

    @Test
    void transfersOnlyTheNamedOwnersSkill() {
        // The admin's own skill with the same slug is never picked; a stale owner id finds nothing.
        assertRejected(() -> service.transfer(2L, "foo", "ns-owner", "successor", "ns-owner",
                Map.of(2L, NamespaceRole.OWNER), Set.of()), "error.skill.notFound");
    }

    @Test
    void newOwnerMustBeActiveNamespaceMember() {
        when(members.findByNamespaceIdAndUserId(2L, "successor")).thenReturn(Optional.empty());
        assertRejected(this::transferAsOwner, "error.skill.owner.transfer.targetIneligible");

        when(members.findByNamespaceIdAndUserId(2L, "successor")).thenReturn(Optional.of(mock(NamespaceMember.class)));
        successor.setStatus(UserStatus.DISABLED);
        assertRejected(this::transferAsOwner, "error.skill.owner.transfer.targetIneligible");
    }

    @Test
    void newOwnerMayNotAlreadyOwnTheSameSlug() {
        when(skills.findByNamespaceIdAndSlugAndOwnerId(2L, "foo", "successor"))
                .thenReturn(Optional.of(skill(3L, 2L, "successor")));

        assertRejected(this::transferAsOwner, "error.skill.owner.transfer.slugTaken");
    }

    @Test
    void concurrentSameSlugInsertIsAConflict() {
        doThrow(new DataIntegrityViolationException("uk_skill_namespace_slug_owner")).when(skills).flush();

        assertThatThrownBy(this::transferAsOwner).hasMessageContaining("error.skill.owner.transfer.slugTaken");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void inactiveNamespaceIsRejected() {
        when(namespace.getStatus()).thenReturn(NamespaceStatus.FROZEN);

        assertRejected(this::transferAsOwner, "error.skill.owner.transfer.namespaceInactive");
    }

    @Test
    void systemOwnedSkillsAreNotTransferred() {
        skill.setGithubSyncManaged(true);
        assertRejected(this::transferAsOwner, "error.skill.owner.transfer.systemOwned");

        skill.setGithubSyncManaged(false);
        when(users.findById("leaver")).thenReturn(Optional.of(UserAccount.systemAccount("leaver", "Bot", null, null)));
        assertRejected(this::transferAsOwner, "error.skill.owner.transfer.systemOwned");
    }

    @Test
    void promotedGlobalCopyMovesWithItsSource() {
        Skill copy = skill(5L, 9L, "leaver");
        copy.setSourceSkillId(1L);
        PromotionRequest initial = mock(PromotionRequest.class);
        when(initial.getTargetSkillId()).thenReturn(5L);
        when(promotions.findActiveInitialBySourceSkillId(1L)).thenReturn(Optional.of(initial));
        when(skills.findLockedById(5L)).thenReturn(Optional.of(copy));

        assertThat(transferAsOwner()).containsExactly(skill, copy);
        assertThat(copy.getOwnerId()).isEqualTo("successor");
        verify(events).publishEvent(new SkillOwnerChangedEvent(5L));
    }

    @Test
    void promotedCopyCannotBeTransferredOnItsOwn() {
        skill.setSourceSkillId(7L);
        PromotionRequest initial = mock(PromotionRequest.class);
        when(initial.getTargetSkillId()).thenReturn(1L);
        when(promotions.findActiveInitialBySourceSkillId(7L)).thenReturn(Optional.of(initial));

        assertRejected(this::transferAsOwner, "error.skill.owner.transfer.promotedCopy");
    }

    private List<Skill> transferAsOwner() {
        return service.transfer(2L, "foo", "leaver", "successor", "ns-owner", Map.of(2L, NamespaceRole.OWNER), Set.of());
    }

    private static Skill skill(Long id, Long namespaceId, String ownerId) {
        Skill skill = new Skill(namespaceId, "foo", ownerId, SkillVisibility.PUBLIC);
        try {
            var field = Skill.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(skill, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return skill;
    }

    private void assertRejected(Runnable transfer, String messageKey) {
        assertThatThrownBy(transfer::run).hasMessageContaining(messageKey);
        assertThat(skill.getOwnerId()).isEqualTo("leaver");
        verify(skills, never()).save(any());
        verify(events, never()).publishEvent(any());
    }
}
