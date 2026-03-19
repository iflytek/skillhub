package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceGovernanceService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberService;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.dto.NamespaceLifecycleRequest;
import com.iflytek.skillhub.dto.NamespaceRequest;
import com.iflytek.skillhub.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

class NamespacePortalCommandAppServiceTest {

    private final NamespaceService namespaceService = mock(NamespaceService.class);
    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final NamespaceGovernanceService namespaceGovernanceService = mock(NamespaceGovernanceService.class);
    private final NamespaceMemberService namespaceMemberService = mock(NamespaceMemberService.class);
    private final NamespacePortalCommandAppService service = new NamespacePortalCommandAppService(
            namespaceService,
            namespaceRepository,
            namespaceGovernanceService,
            namespaceMemberService
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
}
