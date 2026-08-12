package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceAccessPolicy;
import com.iflytek.skillhub.domain.namespace.NamespaceGovernanceService;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberService;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.AdminNamespaceListStatsResponse;
import com.iflytek.skillhub.dto.MemberRequest;
import com.iflytek.skillhub.dto.UpdateMemberRoleRequest;
import com.iflytek.skillhub.repository.AdminNamespaceQueryRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class AdminNamespaceAppServiceTest {

    private final AdminNamespaceQueryRepository adminNamespaceQueryRepository = mock(AdminNamespaceQueryRepository.class);
    private final NamespaceService namespaceService = mock(NamespaceService.class);
    private final NamespaceGovernanceService namespaceGovernanceService = mock(NamespaceGovernanceService.class);
    private final NamespaceMemberService namespaceMemberService = mock(NamespaceMemberService.class);
    private final NamespaceMemberRepository namespaceMemberRepository = mock(NamespaceMemberRepository.class);
    private final NamespaceMemberCandidateService namespaceMemberCandidateService = mock(NamespaceMemberCandidateService.class);
    private final NamespaceAccessPolicy namespaceAccessPolicy = mock(NamespaceAccessPolicy.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final AdminNamespaceAppService service = new AdminNamespaceAppService(
            adminNamespaceQueryRepository,
            namespaceService,
            namespaceGovernanceService,
            namespaceMemberService,
            namespaceMemberRepository,
            namespaceMemberCandidateService,
            namespaceAccessPolicy,
            userAccountRepository
    );

    @Test
    void listProjectsPlatformAdminGovernancePermissionsWithoutMembership() {
        Namespace active = namespace(1L, "team-a", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        when(adminNamespaceQueryRepository.search(null, null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(active), PageRequest.of(0, 20), 1));
        when(adminNamespaceQueryRepository.countMembersByNamespaceId(List.of(1L))).thenReturn(Map.of(1L, 2L));
        when(adminNamespaceQueryRepository.countSkillsByNamespaceId(List.of(1L))).thenReturn(Map.of(1L, 5L));
        when(adminNamespaceQueryRepository.stats()).thenReturn(new AdminNamespaceListStatsResponse(1, 1, 0, 0));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "super-1")).thenReturn(Optional.empty());
        when(namespaceAccessPolicy.isImmutable(active)).thenReturn(false);

        var response = service.list(null, null, null, 0, 20, "super-1");

        assertThat(response.items()).hasSize(1);
        var item = response.items().getFirst();
        assertThat(item.slug()).isEqualTo("team-a");
        assertThat(item.stats().memberCount()).isEqualTo(2);
        assertThat(item.stats().skillCount()).isEqualTo(5);
        assertThat(item.permissions().currentUserRole()).isNull();
        assertThat(item.permissions().platformOverride()).isTrue();
        assertThat(item.permissions().canManageMembers()).isTrue();
        assertThat(item.permissions().canFreeze()).isTrue();
        assertThat(item.permissions().canArchive()).isTrue();
    }

    @Test
    void addMemberAllowsSuperAdminToManageNonMemberTeamNamespace() {
        Namespace namespace = namespace(1L, "team-a", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        NamespaceMember saved = member(10L, 1L, "user-2", NamespaceRole.ADMIN);
        when(namespaceService.getNamespaceBySlug("team-a")).thenReturn(namespace);
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "user-2")).thenReturn(Optional.empty());
        when(namespaceMemberRepository.save(any(NamespaceMember.class))).thenReturn(saved);

        var response = service.addMember("team-a", new MemberRequest("user-2", NamespaceRole.ADMIN), "super-1");

        assertThat(response.userId()).isEqualTo("user-2");
        assertThat(response.role()).isEqualTo(NamespaceRole.ADMIN);
        verify(namespaceMemberRepository).save(any(NamespaceMember.class));
    }

    @Test
    void updateMemberRoleRejectsDirectOwnerAssignment() {
        Namespace namespace = namespace(1L, "team-a", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        when(namespaceService.getNamespaceBySlug("team-a")).thenReturn(namespace);
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(true);

        assertThatThrownBy(() -> service.updateMemberRole(
                "team-a",
                "user-2",
                new UpdateMemberRoleRequest(NamespaceRole.OWNER),
                "super-1"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.namespace.member.owner.setDirect");
    }

    @Test
    void transferOwnershipDemotesCurrentOwnerAndPromotesExistingMember() {
        Namespace namespace = namespace(1L, "team-a", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        NamespaceMember currentOwner = member(10L, 1L, "owner-1", NamespaceRole.OWNER);
        NamespaceMember newOwner = member(11L, 1L, "admin-1", NamespaceRole.ADMIN);
        when(namespaceService.getNamespaceBySlug("team-a")).thenReturn(namespace);
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(true);
        when(namespaceMemberRepository.findByNamespaceIdAndRoleIn(1L, List.of(NamespaceRole.OWNER)))
                .thenReturn(List.of(currentOwner));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "admin-1"))
                .thenReturn(Optional.of(newOwner));

        var response = service.transferOwnership("team-a", "admin-1", "super-1");

        assertThat(response.message()).isEqualTo("Ownership transferred successfully");
        assertThat(currentOwner.getRole()).isEqualTo(NamespaceRole.ADMIN);
        assertThat(newOwner.getRole()).isEqualTo(NamespaceRole.OWNER);
        verify(namespaceMemberRepository).save(currentOwner);
        verify(namespaceMemberRepository).save(newOwner);
    }

    @Test
    void globalNamespaceCannotBeMutatedFromAdminMemberApi() {
        Namespace global = namespace(1L, "global", NamespaceStatus.ACTIVE, NamespaceType.GLOBAL);
        when(namespaceService.getNamespaceBySlug("global")).thenReturn(global);
        when(namespaceAccessPolicy.isImmutable(global)).thenReturn(true);

        assertThatThrownBy(() -> service.addMember(
                "global",
                new MemberRequest("user-2", NamespaceRole.MEMBER),
                "super-1"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.namespace.system.immutable");
    }

    @Test
    void freezeUsesPlatformAdminGovernancePath() {
        Namespace frozen = namespace(1L, "team-a", NamespaceStatus.FROZEN, NamespaceType.TEAM);
        when(namespaceGovernanceService.freezeNamespaceByPlatformAdmin(
                eq("team-a"),
                eq("super-1"),
                eq("reviewing abuse report"),
                any(),
                eq("127.0.0.1"),
                eq("JUnit")))
                .thenReturn(frozen);
        when(namespaceService.getNamespaceBySlug("team-a")).thenReturn(frozen);
        when(adminNamespaceQueryRepository.countMembersByNamespaceId(anyList())).thenReturn(Map.of());
        when(adminNamespaceQueryRepository.countSkillsByNamespaceId(anyList())).thenReturn(Map.of());
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "super-1")).thenReturn(Optional.empty());
        when(namespaceAccessPolicy.isImmutable(frozen)).thenReturn(false);

        var response = service.freeze(
                "team-a",
                new com.iflytek.skillhub.dto.NamespaceLifecycleRequest("reviewing abuse report"),
                "super-1",
                new AuditRequestContext("127.0.0.1", "JUnit"));

        assertThat(response.slug()).isEqualTo("team-a");
        assertThat(response.status()).isEqualTo("FROZEN");
        assertThat(response.permissions().platformOverride()).isTrue();
        verify(namespaceGovernanceService).freezeNamespaceByPlatformAdmin(
                "team-a",
                "super-1",
                "reviewing abuse report",
                null,
                "127.0.0.1",
                "JUnit");
    }

    private Namespace namespace(Long id, String slug, NamespaceStatus status, NamespaceType type) {
        Namespace namespace = new Namespace(slug, "Team " + slug, "owner-1");
        ReflectionTestUtils.setField(namespace, "id", id);
        ReflectionTestUtils.setField(namespace, "createdAt", java.time.Instant.parse("2026-08-12T00:00:00Z"));
        ReflectionTestUtils.setField(namespace, "updatedAt", java.time.Instant.parse("2026-08-12T00:00:00Z"));
        namespace.setStatus(status);
        namespace.setType(type);
        return namespace;
    }

    private NamespaceMember member(Long id, Long namespaceId, String userId, NamespaceRole role) {
        NamespaceMember member = new NamespaceMember(namespaceId, userId, role);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "createdAt", java.time.Instant.parse("2026-08-12T00:00:00Z"));
        ReflectionTestUtils.setField(member, "updatedAt", java.time.Instant.parse("2026-08-12T00:00:00Z"));
        return member;
    }
}
