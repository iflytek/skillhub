package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.repository.SkillSuiteReferenceQueryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillSuiteMembershipProjectionServiceTest {
    @Mock private SkillSuiteReferenceQueryRepository references;
    @Mock private NamespaceMemberRepository namespaceMembers;
    @Mock private RbacService rbacService;
    @InjectMocks private SkillSuiteMembershipProjectionService projection;

    @Test
    void batchesDistinctSkillIdsAndKeepsPageOrderUsingTheCurrentViewer() {
        when(namespaceMembers.findByUserId("viewer"))
                .thenReturn(List.of(new NamespaceMember(7L, "viewer", NamespaceRole.MEMBER)));
        when(rbacService.getUserRoleCodes("viewer")).thenReturn(Set.of("USER"));
        when(references.findSkillIdsWithVisibleMemberships(
                List.of(2L, 1L), "viewer", Map.of(7L, NamespaceRole.MEMBER), Set.of("USER")))
                .thenReturn(Set.of(1L));

        var result = projection.enrich(List.of(summary(2L), summary(1L), summary(2L)), "viewer");

        assertThat(result).extracting(SkillSummaryResponse::id).containsExactly(2L, 1L, 2L);
        assertThat(result).extracting(SkillSummaryResponse::hasVisibleSuites).containsExactly(false, true, false);
        verify(references).findSkillIdsWithVisibleMemberships(
                List.of(2L, 1L), "viewer", Map.of(7L, NamespaceRole.MEMBER), Set.of("USER"));
        verify(namespaceMembers).findByUserId("viewer");
        verify(rbacService).getUserRoleCodes("viewer");
    }

    @Test
    void anonymousProjectionDoesNotLoadAnyIdentityOrMemberships() {
        when(references.findSkillIdsWithVisibleMemberships(List.of(1L), null, Map.of(), Set.of()))
                .thenReturn(Set.of(1L));
        assertThat(projection.enrich(List.of(summary(1L)), null)).singleElement()
                .extracting(SkillSummaryResponse::hasVisibleSuites).isEqualTo(true);
        verifyNoInteractions(namespaceMembers, rbacService);
    }

    @Test
    void emptyPageDoesNotQueryAnyDependency() {
        assertThat(projection.enrich(List.of(), "viewer")).isEmpty();
        verifyNoInteractions(references, namespaceMembers, rbacService);
    }

    @Test
    void visibilityQueryFailureIsNotConvertedToACachedFalseFlag() {
        when(references.findSkillIdsWithVisibleMemberships(any(), eq(null), any(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> projection.enrich(List.of(summary(1L)), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void labelAndManagementCopiesPreserveTheViewerSpecificFlag() {
        SkillSummaryResponse source = summary(1L).withVisibleSuites(true);
        SkillSummaryResponse copied = source.withLabels(List.of()).withManagementState(
                new SkillSummaryResponse.ManagementState(null, null, null, null, null, true));
        assertThat(copied.hasVisibleSuites()).isTrue();
        assertThat(copied.managementState().ownerReadOnly()).isTrue();
        assertThat(source.withVisibleSuites(false).hasVisibleSuites()).isFalse();
    }

    private SkillSummaryResponse summary(Long id) {
        return new SkillSummaryResponse(id, "skill-" + id, "Skill", "Summary", "PUBLIC", "ACTIVE",
                0L, 0, BigDecimal.ZERO, 0, "global", Instant.EPOCH, false,
                null, null, null, "PUBLISHED", null);
    }
}
