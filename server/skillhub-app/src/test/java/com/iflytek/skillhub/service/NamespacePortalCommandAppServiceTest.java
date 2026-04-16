package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.iflytek.skillhub.dto.MemberResponse;
import com.iflytek.skillhub.dto.NamespaceLifecycleRequest;
import com.iflytek.skillhub.dto.NamespaceRequest;
import com.iflytek.skillhub.dto.UpdateMemberRoleRequest;
import com.iflytek.skillhub.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
