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
import static org.mockito.ArgumentMatchers.eq;
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
    void freezeNamespace_rejectsWhenNotActive() {
        Namespace namespace = namespace(1L, "team-a", NamespaceType.TEAM, NamespaceStatus.FROZEN);
        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "admin-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "admin-1", NamespaceRole.ADMIN)));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> governanceService.freezeNamespace("team-a", "admin-1", null, null, null, null));
        assertEquals("error.namespace.state.transition.invalid", ex.messageCode());
    }

    @Test
    void freezeNamespace_rejectsWhenNoPermission() {
        Namespace namespace = namespace(1L, "team-a", NamespaceType.TEAM, NamespaceStatus.ACTIVE);
        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "admin-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "admin-1", NamespaceRole.ADMIN)));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.canFreeze(namespace, NamespaceRole.ADMIN)).thenReturn(false);

        DomainForbiddenException ex = assertThrows(DomainForbiddenException.class,
                () -> governanceService.freezeNamespace("team-a", "admin-1", null, null, null, null));
        assertEquals("error.namespace.lifecycle.forbidden", ex.messageCode());
    }

    @Test
    void unfreezeNamespace_movesFrozenBackToActive() {
        Namespace namespace = namespace(1L, "team-a", NamespaceType.TEAM, NamespaceStatus.FROZEN);
        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "owner-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "owner-1", NamespaceRole.OWNER)));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.canUnfreeze(namespace, NamespaceRole.OWNER)).thenReturn(true);
        when(namespaceRepository.save(namespace)).thenReturn(namespace);

        Namespace updated = governanceService.unfreezeNamespace("team-a", "owner-1", null, null, null);

        assertEquals(NamespaceStatus.ACTIVE, updated.getStatus());
    }

    @Test
    void unfreezeNamespace_rejectsWhenNotFrozen() {
        Namespace namespace = namespace(1L, "team-a", NamespaceType.TEAM, NamespaceStatus.ACTIVE);
        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "owner-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "owner-1", NamespaceRole.OWNER)));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> governanceService.unfreezeNamespace("team-a", "owner-1", null, null, null));
        assertEquals("error.namespace.state.transition.invalid", ex.messageCode());
    }

    @Test
    void unfreezeNamespace_rejectsWhenNoPermission() {
        Namespace namespace = namespace(1L, "team-a", NamespaceType.TEAM, NamespaceStatus.FROZEN);
        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "admin-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "admin-1", NamespaceRole.ADMIN)));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.canUnfreeze(namespace, NamespaceRole.ADMIN)).thenReturn(false);

        DomainForbiddenException ex = assertThrows(DomainForbiddenException.class,
                () -> governanceService.unfreezeNamespace("team-a", "admin-1", null, null, null));
        assertEquals("error.namespace.lifecycle.forbidden", ex.messageCode());
    }

    @Test
    void archiveNamespace_allowsOwnerOnActiveNamespace() {
        Namespace namespace = namespace(1L, "team-a", NamespaceType.TEAM, NamespaceStatus.ACTIVE);
        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "owner-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "owner-1", NamespaceRole.OWNER)));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.canArchive(namespace, NamespaceRole.OWNER)).thenReturn(true);
        when(namespaceRepository.save(namespace)).thenReturn(namespace);

        Namespace updated = governanceService.archiveNamespace("team-a", "owner-1", "cleanup", null, null, null);

        assertEquals(NamespaceStatus.ARCHIVED, updated.getStatus());
    }

    @Test
    void archiveNamespace_rejectsWhenAlreadyArchived() {
        Namespace namespace = namespace(1L, "team-a", NamespaceType.TEAM, NamespaceStatus.ARCHIVED);
        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "owner-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "owner-1", NamespaceRole.OWNER)));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> governanceService.archiveNamespace("team-a", "owner-1", "cleanup", null, null, null));
        assertEquals("error.namespace.state.transition.invalid", ex.messageCode());
    }

    @Test
    void archiveNamespace_rejectsWhenNoPermission() {
        Namespace namespace = namespace(1L, "team-a", NamespaceType.TEAM, NamespaceStatus.ACTIVE);
        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "admin-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "admin-1", NamespaceRole.ADMIN)));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.canArchive(namespace, NamespaceRole.ADMIN)).thenReturn(false);

        DomainForbiddenException ex = assertThrows(DomainForbiddenException.class,
                () -> governanceService.archiveNamespace("team-a", "admin-1", "cleanup", null, null, null));
        assertEquals("error.namespace.lifecycle.forbidden", ex.messageCode());
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
    void restoreNamespace_rejectsWhenNotArchived() {
        Namespace namespace = namespace(1L, "team-a", NamespaceType.TEAM, NamespaceStatus.ACTIVE);
        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "owner-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "owner-1", NamespaceRole.OWNER)));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> governanceService.restoreNamespace("team-a", "owner-1", null, null, null));
        assertEquals("error.namespace.state.transition.invalid", ex.messageCode());
    }

    @Test
    void restoreNamespace_rejectsWhenNoPermission() {
        Namespace namespace = namespace(1L, "team-a", NamespaceType.TEAM, NamespaceStatus.ARCHIVED);
        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "admin-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "admin-1", NamespaceRole.ADMIN)));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.canRestore(namespace, NamespaceRole.ADMIN)).thenReturn(false);

        DomainForbiddenException ex = assertThrows(DomainForbiddenException.class,
                () -> governanceService.restoreNamespace("team-a", "admin-1", null, null, null));
        assertEquals("error.namespace.lifecycle.forbidden", ex.messageCode());
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
    void recordShouldEscapeQuotesInReason() {
        Namespace namespace = namespace(1L, "team-a", NamespaceType.TEAM, NamespaceStatus.ACTIVE);
        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "owner-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "owner-1", NamespaceRole.OWNER)));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.canArchive(namespace, NamespaceRole.OWNER)).thenReturn(true);
        when(namespaceRepository.save(namespace)).thenReturn(namespace);

        governanceService.archiveNamespace("team-a", "owner-1", "say \"hello\"", "req-1", "127.0.0.1", "agent");

        verify(auditLogService).record(eq("owner-1"), eq("ARCHIVE_NAMESPACE"), eq("NAMESPACE"), eq(1L), eq("req-1"), eq("127.0.0.1"), eq("agent"), eq("{\"reason\":\"say \\\"hello\\\"\"}"));
    }

    @Test
    void recordShouldUseNullWhenReasonIsBlank() {
        Namespace namespace = namespace(1L, "team-a", NamespaceType.TEAM, NamespaceStatus.ACTIVE);
        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "owner-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "owner-1", NamespaceRole.OWNER)));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.canFreeze(namespace, NamespaceRole.OWNER)).thenReturn(true);
        when(namespaceRepository.save(namespace)).thenReturn(namespace);

        governanceService.freezeNamespace("team-a", "owner-1", "   ", "req-1", "127.0.0.1", "agent");

        verify(auditLogService).record(eq("owner-1"), eq("FREEZE_NAMESPACE"), eq("NAMESPACE"), eq(1L), eq("req-1"), eq("127.0.0.1"), eq("agent"), eq((String) null));
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
