package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.data.domain.Pageable;
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
        ));

        assertThat(response).hasSize(2);
        assertThat(response.get(0).slug()).isEqualTo("alpha");
        assertThat(response.get(0).currentUserRole()).isEqualTo(NamespaceRole.OWNER);
        assertThat(response.get(0).canDelete()).isTrue();
        assertThat(response.get(1).slug()).isEqualTo("zeta");
        assertThat(response.get(1).currentUserRole()).isEqualTo(NamespaceRole.ADMIN);
        assertThat(response.get(1).canDelete()).isFalse();
    }

    @Test
    void listNamespaces_superAdminReturnsAllActiveNamespaces() {
        Namespace teamB = namespace(2L, "team-b");
        Namespace teamA = namespace(1L, "team-a");
        when(namespaceRepository.findByStatus(eq(NamespaceStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(teamA, teamB), PageRequest.of(0, 10), 2));

        var response = service.listNamespaces(
                PageRequest.of(0, 10),
                Map.of(),
                Set.of("SUPER_ADMIN")
        );

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).slug()).isEqualTo("team-a");
        assertThat(response.items().get(1).slug()).isEqualTo("team-b");
        assertThat(response.total()).isEqualTo(2);
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
                )
        );

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).slug()).isEqualTo("team-a");
        assertThat(response.items().get(1).slug()).isEqualTo("team-b");
    }

    @Test
    void listMyNamespaces_superAdminReturnsPagedNamespacesWithoutGrantingNamespaceRole() {
        Namespace active = namespace(1L, "active");
        Namespace archived = namespace(2L, "archived");
        archived.setStatus(NamespaceStatus.ARCHIVED);
        Namespace frozen = namespace(3L, "frozen");
        frozen.setStatus(NamespaceStatus.FROZEN);

        when(namespaceRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(archived, frozen), PageRequest.of(1, 2), 4));

        var response = service.listMyNamespaces(PageRequest.of(1, 2), Map.of(), Set.of("SUPER_ADMIN"));

        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).extracting("slug").containsExactly("archived", "frozen");
        assertThat(response.items()).extracting("currentUserRole").containsOnlyNulls();
        assertThat(response.items()).extracting("canFreeze").containsOnly(false);
        assertThat(response.items()).extracting("canDelete").containsOnly(false);
        assertThat(response.total()).isEqualTo(4);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
    }

    @Test
    void listMyNamespaces_superAdminWithRequestedRolesSearchesOnlyMatchingMembershipIds() {
        Namespace owned = namespace(1L, "team-ai");
        Pageable expectedPageable = PageRequest.of(0, 20);
        when(namespaceRepository.searchByIdIn(
                eq(List.of(1L)),
                eq(NamespaceStatus.ACTIVE),
                eq(NamespaceType.TEAM),
                eq("team"),
                eq("team-ai"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(owned), expectedPageable, 1));

        var response = service.listMyNamespaces(
                expectedPageable,
                Map.of(1L, NamespaceRole.OWNER, 2L, NamespaceRole.MEMBER),
                Set.of("SUPER_ADMIN"),
                NamespaceStatus.ACTIVE,
                NamespaceType.TEAM,
                " team ",
                " team-ai ",
                Set.of(NamespaceRole.OWNER, NamespaceRole.ADMIN)
        );

        assertThat(response.items()).extracting("slug").containsExactly("team-ai");
        assertThat(response.items()).extracting("currentUserRole").containsExactly(NamespaceRole.OWNER);
        verify(namespaceRepository).searchByIdIn(
                eq(List.of(1L)),
                eq(NamespaceStatus.ACTIVE),
                eq(NamespaceType.TEAM),
                eq("team"),
                eq("team-ai"),
                any(Pageable.class)
        );
        verify(namespaceRepository, never()).search(any(), any(), any(), any(), any());
    }

    @Test
    void listMyNamespaces_superAdminWithoutRequestedRolesUsesUnrestrictedFilteredSearch() {
        Namespace archived = namespace(2L, "ops-team");
        archived.setStatus(NamespaceStatus.ARCHIVED);
        Pageable expectedPageable = PageRequest.of(1, 10);
        when(namespaceRepository.search(
                eq(NamespaceStatus.ARCHIVED),
                eq(null),
                eq("ops"),
                eq("ops-team"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(archived), expectedPageable, 11));

        var response = service.listMyNamespaces(
                expectedPageable,
                Map.of(),
                Set.of("SUPER_ADMIN"),
                NamespaceStatus.ARCHIVED,
                null,
                " ops ",
                " ops-team ",
                Set.of()
        );

        assertThat(response.items()).extracting("slug").containsExactly("ops-team");
        assertThat(response.total()).isEqualTo(11);
        verify(namespaceRepository).search(
                eq(NamespaceStatus.ARCHIVED),
                eq(null),
                eq("ops"),
                eq("ops-team"),
                any(Pageable.class)
        );
        verify(namespaceRepository, never()).searchByIdIn(anyList(), any(), any(), any(), any(), any());
    }

    @Test
    void listMyNamespaces_escapesLikeWildcardsForLiteralSubstringSearch() {
        Pageable expectedPageable = PageRequest.of(0, 20);
        when(namespaceRepository.search(
                eq(null),
                eq(null),
                eq("50!%!_!!off"),
                eq(null),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(), expectedPageable, 0));

        service.listMyNamespaces(
                expectedPageable,
                Map.of(),
                Set.of("SUPER_ADMIN"),
                null,
                null,
                " 50%_!off ",
                null,
                Set.of()
        );

        verify(namespaceRepository).search(
                eq(null),
                eq(null),
                eq("50!%!_!!off"),
                eq(null),
                any(Pageable.class)
        );
    }

    @Test
    void listMyNamespaces_nonSuperAdminWithoutRequestedRolesSearchesAllMembershipIds() {
        Namespace member = namespace(1L, "member-team");
        Namespace administered = namespace(2L, "admin-team");
        when(namespaceRepository.searchByIdIn(
                eq(List.of(1L, 2L)),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(administered, member), PageRequest.of(0, 20), 2));

        var response = service.listMyNamespaces(
                PageRequest.of(0, 20),
                Map.of(2L, NamespaceRole.ADMIN, 1L, NamespaceRole.MEMBER),
                Set.of(),
                null,
                null,
                "   ",
                "\t",
                Set.of()
        );

        assertThat(response.items()).extracting("slug").containsExactly("admin-team", "member-team");
        assertThat(response.items()).extracting("currentUserRole")
                .containsExactly(NamespaceRole.ADMIN, NamespaceRole.MEMBER);
        verify(namespaceRepository).searchByIdIn(
                eq(List.of(1L, 2L)),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                any(Pageable.class)
        );
        verify(namespaceRepository, never()).search(any(), any(), any(), any(), any());
    }

    @Test
    void listMyNamespaces_emptyRoleRestrictedScopeReturnsEmptyPageWithoutRepositoryQuery() {
        var response = service.listMyNamespaces(
                PageRequest.of(2, 10),
                Map.of(1L, NamespaceRole.MEMBER),
                Set.of("SUPER_ADMIN"),
                NamespaceStatus.ACTIVE,
                NamespaceType.TEAM,
                " team ",
                null,
                Set.of(NamespaceRole.OWNER, NamespaceRole.ADMIN)
        );

        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isZero();
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(10);
        verifyNoInteractions(namespaceRepository);
    }

    @Test
    void listMyNamespaces_superAdminCompatibilityCollectsAllRepositoryPages() {
        Namespace first = namespace(1L, "first");
        Namespace second = namespace(2L, "second");
        Namespace third = namespace(3L, "third");

        when(namespaceRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 2), 3))
                .thenReturn(new PageImpl<>(List.of(third), PageRequest.of(1, 2), 3));

        var response = service.listMyNamespaces(Map.of(), Set.of("SUPER_ADMIN"));

        assertThat(response).extracting("slug").containsExactly("first", "second", "third");
        assertThat(response).extracting("currentUserRole").containsOnlyNulls();
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
    void getNamespace_superAdminReadsArchivedNamespaceWithoutMembership() {
        Namespace archived = namespace(1L, "archived-team");
        archived.setStatus(NamespaceStatus.ARCHIVED);
        when(namespaceService.getNamespaceBySlug("archived-team")).thenReturn(archived);

        var response = service.getNamespace("archived-team", "super-1", Map.of(), Set.of("SUPER_ADMIN"));

        assertThat(response.slug()).isEqualTo("archived-team");
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

    @Test
    void listMembers_teamNamespaceRejectsSuperAdminWithoutMembership() {
        Namespace ns = namespace(1L, "team-a");
        when(namespaceService.getNamespaceBySlug("team-a")).thenReturn(ns);
        doThrow(new DomainForbiddenException("error.namespace.membership.required"))
                .when(namespaceService).assertMember(1L, "super-1");

        assertThatThrownBy(() -> service.listMembers("team-a", PageRequest.of(0, 20), "super-1", Set.of("SUPER_ADMIN")))
                .isInstanceOf(DomainForbiddenException.class)
                .hasMessageContaining("error.namespace.membership.required");
    }
}
