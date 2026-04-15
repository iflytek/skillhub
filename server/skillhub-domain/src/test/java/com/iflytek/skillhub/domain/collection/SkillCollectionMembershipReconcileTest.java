package com.iflytek.skillhub.domain.collection;

import com.iflytek.skillhub.domain.skill.SkillVisibility;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillCollectionMembershipReconcileTest {

    @Mock
    private SkillCollectionRepository skillCollectionRepository;
    @Mock
    private SkillCollectionMemberRepository memberRepository;
    @Mock
    private SkillCollectionContributorRepository contributorRepository;
    @Mock
    private SkillReadableForActorPort skillReadableForActorPort;

    private SkillCollectionMembershipService service;

    @BeforeEach
    void setUp() {
        service = new SkillCollectionMembershipService(skillCollectionRepository, memberRepository, contributorRepository,
                new SkillCollectionAuthorizationPolicy(), skillReadableForActorPort);
    }

    @Test
    void reconcileDeletesMemberWhenOwnerCannotReadSkill() {
        SkillCollection collection = new SkillCollection("owner-1", "collection", "Title", SkillVisibility.PUBLIC);
        SkillCollectionMember member = new SkillCollectionMember(1L, 42L, 0);
        when(skillCollectionRepository.findById(1L)).thenReturn(Optional.of(collection));
        when(memberRepository.findByCollectionIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(member));
        when(skillReadableForActorPort.canActingUserReadSkill("owner-1", 42L)).thenReturn(false);

        int deleted = service.reconcileInvisibleSkillsForCollection(1L);

        assertEquals(1, deleted);
        verify(memberRepository).deleteByCollectionIdAndSkillId(1L, 42L);
    }

    @Test
    void reconcileKeepsMemberWhenOwnerCanReadSkill() {
        SkillCollection collection = new SkillCollection("owner-1", "collection", "Title", SkillVisibility.PUBLIC);
        SkillCollectionMember member = new SkillCollectionMember(1L, 42L, 0);
        when(skillCollectionRepository.findById(1L)).thenReturn(Optional.of(collection));
        when(memberRepository.findByCollectionIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(member));
        when(skillReadableForActorPort.canActingUserReadSkill("owner-1", 42L)).thenReturn(true);

        int deleted = service.reconcileInvisibleSkillsForCollection(1L);

        assertEquals(0, deleted);
        verify(memberRepository, never()).deleteByCollectionIdAndSkillId(1L, 42L);
    }
}
