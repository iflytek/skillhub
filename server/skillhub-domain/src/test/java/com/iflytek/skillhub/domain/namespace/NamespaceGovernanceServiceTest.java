package com.iflytek.skillhub.domain.namespace;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamespaceGovernanceServiceTest {

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private NamespaceMemberRepository namespaceMemberRepository;

    @Mock
    private NamespaceAccessPolicy namespaceAccessPolicy;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private NamespaceGovernanceService governanceService;

    @Test
    void freezeNamespace_allowsAdminOnActiveTeamNamespace() {
        Namespace namespace = namespace(1L, "team-a", NamespaceType.TEAM, NamespaceStatus.ACTIVE);
        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "admin-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "admin-1", NamespaceRole.ADMIN)));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.canFreeze(namespace, NamespaceRole.ADMIN)).thenReturn(true);
        when(namespaceRepository.save(namespace)).thenReturn(namespace);

        Namespace updated = governanceService.freezeNamespace("team-a", "admin-1", null, null, null, null);

        assertEquals(NamespaceStatus.FROZEN, updated.getStatus());
        verify(namespaceRepository).save(namespace);
    }

    @Test
    void archiveNamespace_rejectsAdminAndAllowsOnlyOwner() {
        Namespace namespace = namespace(1L, "team-a", NamespaceType.TEAM, NamespaceStatus.ACTIVE);
        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "admin-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "admin-1", NamespaceRole.ADMIN)));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.canArchive(namespace, NamespaceRole.ADMIN)).thenReturn(false);

        assertThrows(DomainForbiddenException.class,
                () -> governanceService.archiveNamespace("team-a", "admin-1", "cleanup", null, null, null));
    }

    @Test
    void restoreNamespace_movesArchivedNamespaceBackToActive() {
        Namespace namespace = namespace(1L, "team-a", NamespaceType.TEAM, NamespaceStatus.ARCHIVED);
        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "owner-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "owner-1", NamespaceRole.OWNER)));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.canRestore(namespace, NamespaceRole.OWNER)).thenReturn(true);
        when(namespaceRepository.save(namespace)).thenReturn(namespace);

        Namespace updated = governanceService.restoreNamespace("team-a", "owner-1", null, null, null);

        assertEquals(NamespaceStatus.ACTIVE, updated.getStatus());
    }

    @Test
    void freezeNamespace_rejectsGlobalNamespace() {
        Namespace namespace = namespace(1L, "global", NamespaceType.GLOBAL, NamespaceStatus.ACTIVE);
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(namespace));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(true);

        assertThrows(DomainBadRequestException.class,
                () -> governanceService.freezeNamespace("global", "admin-1", null, null, null, null));
    }

    @Test
    void unfreezeNamespace_rejectsIllegalTransition() {
        Namespace namespace = namespace(1L, "team-a", NamespaceType.TEAM, NamespaceStatus.ACTIVE);
        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "owner-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "owner-1", NamespaceRole.OWNER)));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);

        assertThrows(DomainBadRequestException.class,
                () -> governanceService.unfreezeNamespace("team-a", "owner-1", null, null, null));
    }

    private Namespace namespace(Long id, String slug, NamespaceType type, NamespaceStatus status) {
        Namespace namespace = new Namespace(slug, "Team A", "owner-1");
        setField(namespace, "id", id);
        namespace.setType(type);
        namespace.setStatus(status);
        return namespace;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
