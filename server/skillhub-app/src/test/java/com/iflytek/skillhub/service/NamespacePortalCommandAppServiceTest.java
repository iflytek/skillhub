package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceGovernanceService;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberService;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.BatchMemberResult;
import com.iflytek.skillhub.dto.MemberRequest;
import com.iflytek.skillhub.dto.MemberResponse;
import com.iflytek.skillhub.dto.NamespaceLifecycleRequest;
import com.iflytek.skillhub.dto.NamespaceRequest;
import com.iflytek.skillhub.dto.UpdateMemberRoleRequest;
import com.iflytek.skillhub.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

class NamespacePortalCommandAppServiceTest {

    private final NamespaceService namespaceService = mock(NamespaceService.class);
    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final NamespaceGovernanceService namespaceGovernanceService = mock(NamespaceGovernanceService.class);
    private final NamespaceMemberService namespaceMemberService = mock(NamespaceMemberService.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final NamespacePortalCommandAppService service = new NamespacePortalCommandAppService(
            namespaceService,
            namespaceRepository,
            namespaceGovernanceService,
            namespaceMemberService,
            userAccountRepository
    );

    @Test
    void createNamespace_requiresPlatformAdminRole() {
        NamespaceRequest request = new NamespaceRequest("team-alpha", "Team Alpha", null);
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "user-1", "user-1@example.com", "", "github", Set.of("USER")
        );

        assertThrows(ForbiddenException.class, () -> service.createNamespace(request, principal));
    }

    @Test
    void createNamespace_throwsWhenPrincipalIsNull() {
        NamespaceRequest request = new NamespaceRequest("team-alpha", "Team Alpha", null);

        assertThrows(com.iflytek.skillhub.exception.UnauthorizedException.class,
                () -> service.createNamespace(request, null));
    }

    @Test
    void createNamespace_successForSkillAdmin() {
        NamespaceRequest request = new NamespaceRequest("team-alpha", "Team Alpha", null);
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "user-1", "user-1@example.com", "", "github", Set.of("SKILL_ADMIN")
        );
        Namespace namespace = namespace(1L, "team-alpha");

        when(namespaceService.createNamespace("team-alpha", "Team Alpha", null, "user-1"))
                .thenReturn(namespace);

        var result = service.createNamespace(request, principal);

        assertThat(result.slug()).isEqualTo("team-alpha");
    }

    @Test
    void updateNamespace_returnsResponse() {
        Namespace ns = namespace(1L, "team-a");
        Namespace updated = namespace(1L, "team-a");
        when(namespaceService.getNamespaceBySlug("team-a")).thenReturn(ns);
        when(namespaceService.updateNamespace(1L, "New Name", "desc", null, "owner-1"))
                .thenReturn(updated);

        var result = service.updateNamespace("team-a", new NamespaceRequest("team-a", "New Name", "desc"), "owner-1");

        assertThat(result.slug()).isEqualTo("team-a");
    }

    @Test
    void unfreezeNamespace_mapsAuditContextAndReturnsResponse() {
        Namespace namespace = namespace(7L, "team-alpha");
        when(namespaceGovernanceService.unfreezeNamespace("team-alpha", "owner-1", null, "127.0.0.1", "JUnit"))
                .thenReturn(namespace);

        var response = service.unfreezeNamespace("team-alpha", "owner-1", new AuditRequestContext("127.0.0.1", "JUnit"));

        assertThat(response.slug()).isEqualTo("team-alpha");
    }

    @Test
    void archiveNamespace_withNullRequest() {
        Namespace namespace = namespace(7L, "team-alpha");
        when(namespaceGovernanceService.archiveNamespace("team-alpha", "owner-1", null, null, "127.0.0.1", "JUnit"))
                .thenReturn(namespace);

        var response = service.archiveNamespace("team-alpha", null, "owner-1", new AuditRequestContext("127.0.0.1", "JUnit"));

        assertThat(response.slug()).isEqualTo("team-alpha");
    }

    @Test
    void archiveNamespace_withReason() {
        Namespace namespace = namespace(7L, "team-alpha");
        when(namespaceGovernanceService.archiveNamespace("team-alpha", "owner-1", "cleanup", null, "127.0.0.1", "JUnit"))
                .thenReturn(namespace);

        var response = service.archiveNamespace("team-alpha", new NamespaceLifecycleRequest("cleanup"), "owner-1", new AuditRequestContext("127.0.0.1", "JUnit"));

        assertThat(response.slug()).isEqualTo("team-alpha");
    }

    @Test
    void restoreNamespace_mapsAuditContextAndReturnsResponse() {
        Namespace namespace = namespace(7L, "team-alpha");
        when(namespaceGovernanceService.restoreNamespace("team-alpha", "owner-1", null, "127.0.0.1", "JUnit"))
                .thenReturn(namespace);

        var response = service.restoreNamespace("team-alpha", "owner-1", new AuditRequestContext("127.0.0.1", "JUnit"));

        assertThat(response.slug()).isEqualTo("team-alpha");
    }

    @Test
    void removeMember_returnsSuccessMessage() {
        Namespace ns = namespace(1L, "team-a");
        when(namespaceService.getNamespaceBySlug("team-a")).thenReturn(ns);

        var result = service.removeMember("team-a", "user-2", "owner-1");

        assertThat(result.message()).isEqualTo("Member removed successfully");
        verify(namespaceMemberService).removeMember(1L, "user-2", "owner-1");
    }

    @Test
    void batchAddMembers_withAllSuccess() {
        Namespace ns = namespace(1L, "team-a");
        when(namespaceService.getNamespaceBySlug("team-a")).thenReturn(ns);
        NamespaceMember member = new NamespaceMember(1L, "user-2", NamespaceRole.MEMBER);
        when(namespaceMemberService.addMember(1L, "user-2", NamespaceRole.MEMBER, "owner-1"))
                .thenReturn(member);

        var result = service.batchAddMembers("team-a", List.of(new MemberRequest("user-2", NamespaceRole.MEMBER)), "owner-1");

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(0);
    }

    @Test
    void batchAddMembers_withFailureMapsErrorCodes() {
        Namespace ns = namespace(1L, "team-a");
        when(namespaceService.getNamespaceBySlug("team-a")).thenReturn(ns);
        doThrow(new RuntimeException("alreadyExists")).when(namespaceMemberService).addMember(1L, "user-2", NamespaceRole.MEMBER, "owner-1");
        doThrow(new RuntimeException("owner.assignDirect")).when(namespaceMemberService).addMember(1L, "user-3", NamespaceRole.OWNER, "owner-1");
        doThrow(new RuntimeException("not found")).when(namespaceMemberService).addMember(1L, "user-4", NamespaceRole.MEMBER, "owner-1");
        doThrow(new RuntimeException("immutable readonly")).when(namespaceMemberService).addMember(1L, "user-5", NamespaceRole.MEMBER, "owner-1");
        doThrow(new RuntimeException("unknown problem")).when(namespaceMemberService).addMember(1L, "user-6", NamespaceRole.MEMBER, "owner-1");
        doThrow(new RuntimeException()).when(namespaceMemberService).addMember(1L, "user-7", NamespaceRole.MEMBER, "owner-1");

        var result = service.batchAddMembers("team-a", List.of(
                new MemberRequest("user-2", NamespaceRole.MEMBER),
                new MemberRequest("user-3", NamespaceRole.OWNER),
                new MemberRequest("user-4", NamespaceRole.MEMBER),
                new MemberRequest("user-5", NamespaceRole.MEMBER),
                new MemberRequest("user-6", NamespaceRole.MEMBER),
                new MemberRequest("user-7", NamespaceRole.MEMBER)
        ), "owner-1");

        assertThat(result.failureCount()).isEqualTo(6);
        var errors = result.results().stream().filter(r -> !r.success()).map(BatchMemberResult::error).toList();
        assertThat(errors).containsExactly("ALREADY_MEMBER", "INVALID_ROLE", "USER_NOT_FOUND", "NAMESPACE_READONLY", "UNKNOWN_ERROR", "UNKNOWN_ERROR");
    }

    @Test
    void freezeNamespace_mapsAuditContextAndReturnsResponse() {
        Namespace namespace = namespace(7L, "team-alpha");
        namespace.setStatus(NamespaceStatus.FROZEN);
        when(namespaceGovernanceService.freezeNamespace("team-alpha", "owner-1", "cleanup", null, "127.0.0.1", "JUnit"))
                .thenReturn(namespace);

        var response = service.freezeNamespace(
                "team-alpha",
                new NamespaceLifecycleRequest("cleanup"),
                "owner-1",
                new AuditRequestContext("127.0.0.1", "JUnit")
        );

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.slug()).isEqualTo("team-alpha");
        assertThat(response.status()).isEqualTo(NamespaceStatus.FROZEN);
        verify(namespaceGovernanceService).freezeNamespace("team-alpha", "owner-1", "cleanup", null, "127.0.0.1", "JUnit");
    }

    private Namespace namespace(Long id, String slug) {
        Namespace namespace = new Namespace(slug, "Team Alpha", "owner-1");
        ReflectionTestUtils.setField(namespace, "id", id);
        namespace.setType(NamespaceType.TEAM);
        return namespace;
    }

    @Test
    void addMember_populatesDisplayNameAndEmail() {
        Namespace ns = namespace(1L, "team-a");
        NamespaceMember member = new NamespaceMember(1L, "user-2", NamespaceRole.ADMIN);
        ReflectionTestUtils.setField(member, "id", 10L);
        UserAccount user = new UserAccount("user-2", "Alice", "alice@example.com", null);

        when(namespaceService.getNamespaceBySlug("team-a")).thenReturn(ns);
        when(namespaceMemberService.addMember(1L, "user-2", NamespaceRole.ADMIN, "owner-1"))
                .thenReturn(member);
        when(userAccountRepository.findById("user-2"))
                .thenReturn(Optional.of(user));

        MemberResponse result = service.addMember("team-a", "user-2", NamespaceRole.ADMIN, "owner-1");

        assertThat(result.userId()).isEqualTo("user-2");
        assertThat(result.displayName()).isEqualTo("Alice");
        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(result.role()).isEqualTo(NamespaceRole.ADMIN);
    }

    @Test
    void addMember_withoutUserAccount_degradesGracefully() {
        Namespace ns = namespace(1L, "team-a");
        NamespaceMember member = new NamespaceMember(1L, "ghost", NamespaceRole.MEMBER);
        ReflectionTestUtils.setField(member, "id", 20L);

        when(namespaceService.getNamespaceBySlug("team-a")).thenReturn(ns);
        when(namespaceMemberService.addMember(1L, "ghost", NamespaceRole.MEMBER, "owner-1"))
                .thenReturn(member);
        when(userAccountRepository.findById("ghost"))
                .thenReturn(Optional.empty());

        MemberResponse result = service.addMember("team-a", "ghost", NamespaceRole.MEMBER, "owner-1");

        assertThat(result.userId()).isEqualTo("ghost");
        assertThat(result.displayName()).isNull();
        assertThat(result.email()).isNull();
    }

    @Test
    void updateMemberRole_populatesDisplayNameAndEmail() {
        Namespace ns = namespace(1L, "team-a");
        NamespaceMember member = new NamespaceMember(1L, "user-2", NamespaceRole.OWNER);
        ReflectionTestUtils.setField(member, "id", 10L);
        UserAccount user = new UserAccount("user-2", "Alice", "alice@example.com", null);

        when(namespaceService.getNamespaceBySlug("team-a")).thenReturn(ns);
        when(namespaceMemberService.updateMemberRole(1L, "user-2", NamespaceRole.OWNER, "owner-1"))
                .thenReturn(member);
        when(userAccountRepository.findById("user-2"))
                .thenReturn(Optional.of(user));

        MemberResponse result = service.updateMemberRole(
                "team-a", "user-2",
                new UpdateMemberRoleRequest(NamespaceRole.OWNER),
                "owner-1"
        );

        assertThat(result.userId()).isEqualTo("user-2");
        assertThat(result.displayName()).isEqualTo("Alice");
        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(result.role()).isEqualTo(NamespaceRole.OWNER);
    }

    @Test
    void updateMemberRole_withoutUserAccount_degradesGracefully() {
        Namespace ns = namespace(1L, "team-a");
        NamespaceMember member = new NamespaceMember(1L, "ghost", NamespaceRole.MEMBER);
        ReflectionTestUtils.setField(member, "id", 20L);

        when(namespaceService.getNamespaceBySlug("team-a")).thenReturn(ns);
        when(namespaceMemberService.updateMemberRole(1L, "ghost", NamespaceRole.ADMIN, "owner-1"))
                .thenReturn(member);
        when(userAccountRepository.findById("ghost"))
                .thenReturn(Optional.empty());

        MemberResponse result = service.updateMemberRole(
                "team-a", "ghost",
                new UpdateMemberRoleRequest(NamespaceRole.ADMIN),
                "owner-1"
        );

        assertThat(result.userId()).isEqualTo("ghost");
        assertThat(result.displayName()).isNull();
        assertThat(result.email()).isNull();
    }
}
