package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceAccessPolicy;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberService;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

class NamespacePortalQueryAppServiceTest {

    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final NamespaceService namespaceService = mock(NamespaceService.class);
    private final NamespaceMemberService namespaceMemberService = mock(NamespaceMemberService.class);
    private final NamespaceAccessPolicy namespaceAccessPolicy = mock(NamespaceAccessPolicy.class);
    private final NamespacePortalQueryAppService service = new NamespacePortalQueryAppService(
            namespaceRepository,
            namespaceService,
            namespaceMemberService,
            namespaceAccessPolicy
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
        when(namespaceAccessPolicy.isImmutable(zeta)).thenReturn(false);
        when(namespaceAccessPolicy.canFreeze(zeta, NamespaceRole.ADMIN)).thenReturn(true);
        when(namespaceAccessPolicy.canUnfreeze(zeta, NamespaceRole.ADMIN)).thenReturn(false);
        when(namespaceAccessPolicy.canArchive(zeta, NamespaceRole.ADMIN)).thenReturn(true);
        when(namespaceAccessPolicy.canRestore(zeta, NamespaceRole.ADMIN)).thenReturn(false);

        var response = service.listMyNamespaces(Map.of(
                2L, NamespaceRole.ADMIN,
                1L, NamespaceRole.OWNER
        ));

        assertThat(response).hasSize(2);
        assertThat(response.get(0).slug()).isEqualTo("alpha");
        assertThat(response.get(0).currentUserRole()).isEqualTo(NamespaceRole.OWNER);
        assertThat(response.get(1).slug()).isEqualTo("zeta");
        assertThat(response.get(1).currentUserRole()).isEqualTo(NamespaceRole.ADMIN);
    }

    private Namespace namespace(Long id, String slug) {
        Namespace namespace = new Namespace(slug, slug, "owner-1");
        ReflectionTestUtils.setField(namespace, "id", id);
        namespace.setStatus(NamespaceStatus.ACTIVE);
        namespace.setType(NamespaceType.TEAM);
        return namespace;
    }
}
