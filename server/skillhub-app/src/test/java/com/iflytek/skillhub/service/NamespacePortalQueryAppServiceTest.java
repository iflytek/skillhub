package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceAccessPolicy;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberService;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.MemberResponse;
import com.iflytek.skillhub.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

class NamespacePortalQueryAppServiceTest {

    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final NamespaceService namespaceService = mock(NamespaceService.class);
    private final NamespaceMemberService namespaceMemberService = mock(NamespaceMemberService.class);
    private final NamespaceAccessPolicy namespaceAccessPolicy = mock(NamespaceAccessPolicy.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final NamespacePortalQueryAppService service = new NamespacePortalQueryAppService(
            namespaceRepository,
            namespaceService,
            namespaceMemberService,
            namespaceAccessPolicy,
            userAccountRepository
    );

    @Test
    void listMyNamespaces_sortsBySlugAndProjectsRoleCapabilities() {
        Namespace zeta = namespace(2L, "zeta");
        Namespace alpha = namespace(1L, "alpha");
        when(namespaceRepository.findByIdIn(anyList())).thenReturn(List.of(zeta, alpha));
        when(namespaceAccessPolicy.isImmutable(alpha)).thenReturn(false);
        when(namespaceAccessPolicy.canFreeze(alpha, NamespaceRole.OWNER)).thenReturn(true);
        when(namespaceAccessPolicy.canUnfreeze(alpha, NamespaceRole.OWNER)).thenReturn(false);
        when(namespaceAccessPolicy.canArchive(alpha, NamespaceRole.OWNER)).thenReturn(true);
        when(namespaceAccessPolicy.canRestore(alpha, NamespaceRole.OWNER)).thenReturn(false);
        when(namespaceService.canDelete(alpha, NamespaceRole.OWNER)).thenReturn(true);
        when(namespaceAccessPolicy.isImmutable(zeta)).thenReturn(false);
        when(namespaceAccessPolicy.canFreeze(zeta, NamespaceRole.ADMIN)).thenReturn(true);
        when(namespaceAccessPolicy.canUnfreeze(zeta, NamespaceRole.ADMIN)).thenReturn(false);
        when(namespaceAccessPolicy.canArchive(zeta, NamespaceRole.ADMIN)).thenReturn(true);
        when(namespaceAccessPolicy.canRestore(zeta, NamespaceRole.ADMIN)).thenReturn(false);
        when(namespaceService.canDelete(zeta, NamespaceRole.ADMIN)).thenReturn(false);

        var response = service.listMyNamespaces(Map.of(
                2L, NamespaceRole.ADMIN,
                1L, NamespaceRole.OWNER
        ), Set.of());

        assertThat(response).hasSize(2);
        assertThat(response.get(0).slug()).isEqualTo("alpha");
        assertThat(response.get(0).currentUserRole()).isEqualTo(NamespaceRole.OWNER);
        assertThat(response.get(0).canDelete()).isTrue();
        assertThat(response.get(1).slug()).isEqualTo("zeta");
        assertThat(response.get(1).currentUserRole()).isEqualTo(NamespaceRole.ADMIN);
        assertThat(response.get(1).canDelete()).isFalse();
    }

    @Test
    void listNamespaces_returnsOnlyCurrentUsersActiveNamespaces() {
        Namespace teamA = namespace(1L, "team-a");
        Namespace teamB = namespace(2L, "team-b");
        Namespace archived = namespace(3L, "archived");
        archived.setStatus(NamespaceStatus.ARCHIVED);

        when(namespaceRepository.findByIdIn(anyList())).thenReturn(List.of(teamB, archived, teamA));

        var response = service.listNamespaces(
                PageRequest.of(0, 10),
                Map.of(
                        1L, NamespaceRole.MEMBER,
                        2L, NamespaceRole.ADMIN,
                        3L, NamespaceRole.OWNER
                ),
                Set.of()
        );

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).slug()).isEqualTo("team-a");
        assertThat(response.items().get(1).slug()).isEqualTo("team-b");
    }

    @Test
    void getNamespace_throwsWhenCurrentUserIsNotNamespaceMember() {
        Namespace namespace = namespace(1L, "team-a");
        when(namespaceService.getNamespaceBySlugForRead("team-a", "user-1", Map.of()))
                .thenReturn(namespace);

        assertThatThrownBy(() -> service.getNamespace("team-a", "user-1", Map.of(), Set.of()))
                .isInstanceOf(DomainForbiddenException.class);
    }

    @Test
    void listNamespaces_superAdminSeesAllActiveNamespacesWithoutMembership() {
        Namespace teamA = namespace(1L, "team-a");
        Namespace teamB = namespace(2L, "team-b");
        when(namespaceRepository.findByStatus(eq(NamespaceStatus.ACTIVE), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(teamA, teamB), PageRequest.of(0, 20), 2));

        var response = service.listNamespaces(PageRequest.of(0, 20), Map.of(), Set.of("SUPER_ADMIN"));

        assertThat(response.items()).extracting("slug").containsExactly("team-a", "team-b");
    }

    @Test
    void listMyNamespaces_superAdminSeesAllNamespacesWithoutSyntheticOwnerActions() {
        Namespace active = namespace(1L, "active-team");
        Namespace archived = namespace(2L, "archived-team");
        archived.setStatus(NamespaceStatus.ARCHIVED);
        when(namespaceRepository.findAll()).thenReturn(List.of(archived, active));
        when(namespaceAccessPolicy.isImmutable(active)).thenReturn(false);
        when(namespaceAccessPolicy.isImmutable(archived)).thenReturn(false);

        var response = service.listMyNamespaces(Map.of(), Set.of("SUPER_ADMIN"));

        assertThat(response).hasSize(2);
        assertThat(response.get(0).slug()).isEqualTo("active-team");
        assertThat(response.get(0).currentUserRole()).isNull();
        assertThat(response.get(0).canFreeze()).isFalse();
        assertThat(response.get(0).canArchive()).isFalse();
        assertThat(response.get(0).canDelete()).isFalse();
        assertThat(response.get(1).slug()).isEqualTo("archived-team");
    }

    @Test
    void getNamespace_superAdminReadsNamespaceWithoutMembership() {
        Namespace namespace = namespace(1L, "team-a");
        namespace.setStatus(NamespaceStatus.ARCHIVED);
        when(namespaceService.getNamespaceBySlug("team-a")).thenReturn(namespace);

        var response = service.getNamespace("team-a", "super-1", Map.of(), Set.of("SUPER_ADMIN"));

        assertThat(response.slug()).isEqualTo("team-a");
        assertThat(response.status()).isEqualTo(NamespaceStatus.ARCHIVED);
    }

    private Namespace namespace(Long id, String slug) {
        Namespace namespace = new Namespace(slug, slug, "owner-1");
        ReflectionTestUtils.setField(namespace, "id", id);
        namespace.setStatus(NamespaceStatus.ACTIVE);
        namespace.setType(NamespaceType.TEAM);
        return namespace;
    }

    @Test
    void listMembers_withUserAccount_returnsDisplayNameAndEmail() {
        Namespace ns = namespace(1L, "team-a");
        NamespaceMember member = new NamespaceMember(1L, "user-2", NamespaceRole.ADMIN);
        ReflectionTestUtils.setField(member, "id", 10L);
        UserAccount user = new UserAccount("user-2", "Alice", "alice@example.com", null);

        when(namespaceService.getNamespaceBySlug("team-a")).thenReturn(ns);
        when(namespaceMemberService.listMembers(eq(1L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(member), PageRequest.of(0, 20), 1));
        when(userAccountRepository.findByIdIn(List.of("user-2")))
                .thenReturn(List.of(user));

        PageResponse<MemberResponse> result = service.listMembers("team-a", PageRequest.of(0, 20), "owner-1", Set.of());

        assertThat(result.items()).hasSize(1);
        MemberResponse mr = result.items().get(0);
        assertThat(mr.userId()).isEqualTo("user-2");
        assertThat(mr.displayName()).isEqualTo("Alice");
        assertThat(mr.email()).isEqualTo("alice@example.com");
        assertThat(mr.role()).isEqualTo(NamespaceRole.ADMIN);
    }

    @Test
    void listMembers_withoutUserAccount_returnsNullFields() {
        Namespace ns = namespace(1L, "team-a");
        NamespaceMember member = new NamespaceMember(1L, "ghost-user", NamespaceRole.MEMBER);
        ReflectionTestUtils.setField(member, "id", 20L);

        when(namespaceService.getNamespaceBySlug("team-a")).thenReturn(ns);
        when(namespaceMemberService.listMembers(eq(1L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(member), PageRequest.of(0, 20), 1));
        when(userAccountRepository.findByIdIn(List.of("ghost-user")))
                .thenReturn(List.of());

        PageResponse<MemberResponse> result = service.listMembers("team-a", PageRequest.of(0, 20), "owner-1", Set.of());

        assertThat(result.items()).hasSize(1);
        MemberResponse mr = result.items().get(0);
        assertThat(mr.userId()).isEqualTo("ghost-user");
        assertThat(mr.displayName()).isNull();
        assertThat(mr.email()).isNull();
    }

    @Test
    void listMembers_globalNamespaceRejectsRegularUsersEvenWhenTheyAreGlobalMembers() {
        Namespace ns = namespace(1L, "global");
        ns.setType(NamespaceType.GLOBAL);
        when(namespaceService.getNamespaceBySlug("global")).thenReturn(ns);
        when(namespaceMemberService.listMembers(eq(1L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertThatThrownBy(() -> service.listMembers("global", PageRequest.of(0, 20), "user-1", Set.of()))
                .isInstanceOf(DomainForbiddenException.class)
                .hasMessageContaining("error.namespace.global.members.platformAdmin.required");
    }
}
