package com.iflytek.skillhub.domain.collection;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillCollectionMembershipServiceTest {

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
    @DisplayName("COL-04 / INT-01: port false rejects add skill")
    void portFalse_rejectsAdd() {
        SkillCollection c = new SkillCollection("owner-1", "col", "T", SkillVisibility.PUBLIC);
        when(skillCollectionRepository.findById(1L)).thenReturn(Optional.of(c));
        when(skillReadableForActorPort.canActingUserReadSkill("owner-1", 99L)).thenReturn(false);

        assertThrows(DomainBadRequestException.class, () -> service.addSkill(1L, "owner-1", 99L, false));
    }

    @Test
    @DisplayName("Port true allows add under cap")
    void portTrue_addsSkill() {
        SkillCollection c = new SkillCollection("owner-1", "col", "T", SkillVisibility.PUBLIC);
        when(skillCollectionRepository.findById(1L)).thenReturn(Optional.of(c));
        when(skillReadableForActorPort.canActingUserReadSkill("owner-1", 99L)).thenReturn(true);
        when(memberRepository.countByCollectionId(1L)).thenReturn(0L);
        when(memberRepository.existsByCollectionIdAndSkillId(1L, 99L)).thenReturn(false);
        when(memberRepository.findByCollectionIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of());
        SkillCollectionMember saved = new SkillCollectionMember(1L, 99L, 0);
        when(memberRepository.save(any(SkillCollectionMember.class))).thenReturn(saved);

        SkillCollectionMember result = service.addSkill(1L, "owner-1", 99L, false);

        assertEquals(99L, result.getSkillId());
        verify(memberRepository).save(any(SkillCollectionMember.class));
    }

    @Test
    @DisplayName("101st skill throws")
    void capSkillsPerCollection() {
        SkillCollection c = new SkillCollection("owner-1", "col", "T", SkillVisibility.PUBLIC);
        when(skillCollectionRepository.findById(1L)).thenReturn(Optional.of(c));
        when(skillReadableForActorPort.canActingUserReadSkill("owner-1", 99L)).thenReturn(true);
        when(memberRepository.countByCollectionId(1L)).thenReturn(100L);

        assertThrows(DomainBadRequestException.class, () -> service.addSkill(1L, "owner-1", 99L, false));
    }

    @Test
    @DisplayName("Duplicate membership throws")
    void duplicateMember() {
        SkillCollection c = new SkillCollection("owner-1", "col", "T", SkillVisibility.PUBLIC);
        when(skillCollectionRepository.findById(1L)).thenReturn(Optional.of(c));
        when(skillReadableForActorPort.canActingUserReadSkill("owner-1", 99L)).thenReturn(true);
        when(memberRepository.countByCollectionId(1L)).thenReturn(5L);
        when(memberRepository.existsByCollectionIdAndSkillId(1L, 99L)).thenReturn(true);

        assertThrows(DomainBadRequestException.class, () -> service.addSkill(1L, "owner-1", 99L, false));
    }

    @Test
    @DisplayName("ROL-07: contributor cannot set visibility via membership service path N/A — use domain service")
    void reorder_updatesSortOrder() {
        SkillCollection c = new SkillCollection("owner-1", "col", "T", SkillVisibility.PUBLIC);
        when(skillCollectionRepository.findById(1L)).thenReturn(Optional.of(c));
        SkillCollectionMember m1 = new SkillCollectionMember(1L, 10L, 0);
        SkillCollectionMember m2 = new SkillCollectionMember(1L, 20L, 1);
        when(memberRepository.findByCollectionIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(m1, m2));

        service.reorderSkills(1L, "owner-1", List.of(20L, 10L), false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<SkillCollectionMember>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(memberRepository).saveAll(captor.capture());
        List<SkillCollectionMember> updated = new ArrayList<>();
        captor.getValue().forEach(updated::add);
        assertEquals(20L, updated.get(0).getSkillId());
        assertEquals(0, updated.get(0).getSortOrder());
        assertEquals(10L, updated.get(1).getSkillId());
        assertEquals(1, updated.get(1).getSortOrder());
    }

    @Test
    @DisplayName("Reorder with null skill id is rejected as bad request")
    void reorder_withNullSkillId_rejected() {
        SkillCollection c = new SkillCollection("owner-1", "col", "T", SkillVisibility.PUBLIC);
        when(skillCollectionRepository.findById(1L)).thenReturn(Optional.of(c));
        SkillCollectionMember m1 = new SkillCollectionMember(1L, 10L, 0);
        SkillCollectionMember m2 = new SkillCollectionMember(1L, 20L, 1);
        when(memberRepository.findByCollectionIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(m1, m2));

        DomainBadRequestException ex = assertThrows(
                DomainBadRequestException.class,
                () -> service.reorderSkills(1L, "owner-1", Arrays.asList(20L, null), false)
        );

        assertEquals("error.skillCollection.reorder.nullId", ex.messageCode());
    }

    @Test
    @DisplayName("D-11: contributor reorder merges visible subset while keeping hidden order stable")
    void contributorReorder_mergesVisibleSubset() {
        SkillCollection c = new SkillCollection("owner-1", "col", "T", SkillVisibility.PUBLIC);
        when(skillCollectionRepository.findById(1L)).thenReturn(Optional.of(c));
        when(contributorRepository.existsByCollectionIdAndUserId(1L, "contributor-1")).thenReturn(true);

        SkillCollectionMember m1 = new SkillCollectionMember(1L, 10L, 0);
        SkillCollectionMember m2 = new SkillCollectionMember(1L, 20L, 1);
        SkillCollectionMember m3 = new SkillCollectionMember(1L, 30L, 2);
        SkillCollectionMember m4 = new SkillCollectionMember(1L, 40L, 3);
        when(memberRepository.findByCollectionIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(m1, m2, m3, m4));

        when(skillReadableForActorPort.canActingUserReadSkill(eq("contributor-1"), eq(10L))).thenReturn(true);
        when(skillReadableForActorPort.canActingUserReadSkill(eq("contributor-1"), eq(20L))).thenReturn(false);
        when(skillReadableForActorPort.canActingUserReadSkill(eq("contributor-1"), eq(30L))).thenReturn(true);
        when(skillReadableForActorPort.canActingUserReadSkill(eq("contributor-1"), eq(40L))).thenReturn(false);

        service.reorderSkills(1L, "contributor-1", List.of(30L, 10L), false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<SkillCollectionMember>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(memberRepository).saveAll(captor.capture());
        List<SkillCollectionMember> updated = new ArrayList<>();
        captor.getValue().forEach(updated::add);

        assertEquals(30L, updated.get(0).getSkillId());
        assertEquals(0, updated.get(0).getSortOrder());
        assertEquals(20L, updated.get(1).getSkillId());
        assertEquals(1, updated.get(1).getSortOrder());
        assertEquals(10L, updated.get(2).getSkillId());
        assertEquals(2, updated.get(2).getSortOrder());
        assertEquals(40L, updated.get(3).getSkillId());
        assertEquals(3, updated.get(3).getSortOrder());
    }

    @Test
    @DisplayName("Contributor reorder rejects request when visible subset is incomplete")
    void contributorReorder_incompleteVisibleSubset_rejected() {
        SkillCollection c = new SkillCollection("owner-1", "col", "T", SkillVisibility.PUBLIC);
        when(skillCollectionRepository.findById(1L)).thenReturn(Optional.of(c));
        when(contributorRepository.existsByCollectionIdAndUserId(1L, "contributor-1")).thenReturn(true);
        SkillCollectionMember m1 = new SkillCollectionMember(1L, 10L, 0);
        SkillCollectionMember m2 = new SkillCollectionMember(1L, 20L, 1);
        SkillCollectionMember m3 = new SkillCollectionMember(1L, 30L, 2);
        when(memberRepository.findByCollectionIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(m1, m2, m3));

        when(skillReadableForActorPort.canActingUserReadSkill(eq("contributor-1"), eq(10L))).thenReturn(true);
        when(skillReadableForActorPort.canActingUserReadSkill(eq("contributor-1"), eq(20L))).thenReturn(false);
        when(skillReadableForActorPort.canActingUserReadSkill(eq("contributor-1"), eq(30L))).thenReturn(true);

        DomainBadRequestException ex = assertThrows(
                DomainBadRequestException.class,
                () -> service.reorderSkills(1L, "contributor-1", List.of(30L), false)
        );

        assertEquals("error.skillCollection.reorder.setMismatch", ex.messageCode());
    }
}
