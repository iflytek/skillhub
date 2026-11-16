package com.iflytek.skillhub.domain.namespace;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.review.ReviewAutoPublicationHandoff;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NamespaceMemberServiceTest {

    @Mock
    private NamespaceMemberRepository namespaceMemberRepository;
    @Mock
    private NamespaceService namespaceService;
    @Mock
    private NamespaceAccessPolicy namespaceAccessPolicy;
    @Mock
    private NamespaceRepository namespaceRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ReviewAutoPublicationHandoff autoPublicationHandoff;

    @InjectMocks
    private NamespaceMemberService namespaceMemberService;

    @Test
    void teamAutoPublicationIsAbsentByDefault() {
        NamespaceMember member = new NamespaceMember(1L, "publisher", NamespaceRole.MEMBER);

        assertFalse(member.isTeamAutoPublicationGranted());
        assertNull(member.getTeamAutoPublicationGrantId());
    }

    @Test
    void ownerOrAdminGrantCreatesAuditedGrantIdentityWithoutChangingRole() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner");
        NamespaceMember member = new NamespaceMember(1L, "publisher", NamespaceRole.MEMBER);
        when(namespaceService.getNamespace(1L)).thenReturn(namespace);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "publisher")).thenReturn(Optional.of(member));
        when(namespaceMemberRepository.save(member)).thenReturn(member);

        NamespaceMember result = namespaceMemberService.setTeamAutoPublication(1L, "publisher", true, "admin");

        assertTrue(result.isTeamAutoPublicationGranted());
        assertEquals(NamespaceRole.MEMBER, result.getRole());
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).record(eq("admin"), eq("TEAM_AUTO_PUBLICATION_CHANGE"), eq("NAMESPACE_MEMBER"),
                isNull(), isNull(), isNull(), isNull(), detail.capture());
        assertTrue(detail.getValue().contains("\"reason\":\"GRANT\""));
        assertTrue(detail.getValue().contains(result.getTeamAutoPublicationGrantId().toString()));
        assertFalse(detail.getValue().contains("beforeGrantId"));
        verifyNoInteractions(autoPublicationHandoff);
    }

    @Test
    void repeatedGrantKeepsIdentityAndDoesNotAuditAgain() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner");
        NamespaceMember member = new NamespaceMember(1L, "publisher", NamespaceRole.MEMBER);
        UUID existing = member.grantTeamAutoPublication();
        when(namespaceService.getNamespace(1L)).thenReturn(namespace);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "publisher")).thenReturn(Optional.of(member));

        namespaceMemberService.setTeamAutoPublication(1L, "publisher", true, "owner");

        assertEquals(existing, member.getTeamAutoPublicationGrantId());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void ordinaryMemberOrOtherNamespaceManagerCannotChangeGrant() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner");
        NamespaceMember member = new NamespaceMember(1L, "publisher", NamespaceRole.MEMBER);
        when(namespaceService.getNamespace(1L)).thenReturn(namespace);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(true);
        doThrow(new DomainForbiddenException("error.namespace.admin.required"))
                .when(namespaceService).assertAdminOrOwner(1L, "outsider");

        assertThrows(DomainForbiddenException.class, () ->
                namespaceMemberService.setTeamAutoPublication(1L, "publisher", true, "outsider"));

        assertFalse(member.isTeamAutoPublicationGranted());
        verify(namespaceMemberRepository, never()).save(any());
        verifyNoInteractions(auditLogService, autoPublicationHandoff);
    }

    @Test
    void grantIsRejectedForAdminsAndInactiveNamespaces() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner");
        NamespaceMember admin = new NamespaceMember(1L, "admin-2", NamespaceRole.ADMIN);
        when(namespaceService.getNamespace(1L)).thenReturn(namespace);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "admin-2")).thenReturn(Optional.of(admin));

        assertThrows(DomainBadRequestException.class, () ->
                namespaceMemberService.setTeamAutoPublication(1L, "admin-2", true, "owner"));
        assertFalse(admin.isTeamAutoPublicationGranted());

        Namespace frozen = new Namespace("team-b", "Team B", "owner");
        frozen.setStatus(NamespaceStatus.FROZEN);
        when(namespaceService.getNamespace(2L)).thenReturn(frozen);
        when(namespaceAccessPolicy.canManageMembers(frozen)).thenReturn(false);
        when(namespaceAccessPolicy.isImmutable(frozen)).thenReturn(false);
        assertThrows(DomainBadRequestException.class, () ->
                namespaceMemberService.setTeamAutoPublication(2L, "publisher", true, "owner"));
        verifyNoInteractions(auditLogService);
    }

    @Test
    void revokeClearsIdentityHandsOffPendingWorkAndAuditsBeforeValue() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner");
        NamespaceMember member = new NamespaceMember(1L, "publisher", NamespaceRole.MEMBER);
        UUID before = member.grantTeamAutoPublication();
        when(namespaceService.getNamespace(1L)).thenReturn(namespace);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "publisher")).thenReturn(Optional.of(member));
        when(namespaceMemberRepository.save(member)).thenReturn(member);
        when(autoPublicationHandoff.handOffMember(1L, "publisher")).thenReturn(2);

        namespaceMemberService.setTeamAutoPublication(1L, "publisher", false, "owner");

        assertFalse(member.isTeamAutoPublicationGranted());
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).record(eq("owner"), eq("TEAM_AUTO_PUBLICATION_CHANGE"), eq("NAMESPACE_MEMBER"),
                isNull(), isNull(), isNull(), isNull(), detail.capture());
        assertTrue(detail.getValue().contains(before.toString()));
        assertTrue(detail.getValue().contains("\"handedOffTasks\":2"));
    }

    @Test
    void regrantAfterRevocationUsesNewIdentity() {
        NamespaceMember member = new NamespaceMember(1L, "publisher", NamespaceRole.MEMBER);
        UUID first = member.grantTeamAutoPublication();
        member.revokeTeamAutoPublication();

        UUID second = member.grantTeamAutoPublication();

        assertNotEquals(first, second);
    }

    @Test
    void roleChangeAndRemovalRevokeGrantAndHandOffPendingWork() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner");
        NamespaceMember promoted = new NamespaceMember(1L, "publisher", NamespaceRole.MEMBER);
        NamespaceMember removed = new NamespaceMember(1L, "leaver", NamespaceRole.MEMBER);
        promoted.grantTeamAutoPublication();
        removed.grantTeamAutoPublication();
        when(namespaceService.getNamespace(1L)).thenReturn(namespace);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "publisher")).thenReturn(Optional.of(promoted));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "leaver")).thenReturn(Optional.of(removed));
        when(namespaceMemberRepository.save(any(NamespaceMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        namespaceMemberService.updateMemberRole(1L, "publisher", NamespaceRole.ADMIN, "owner");
        namespaceMemberService.removeMember(1L, "leaver", "owner");

        assertFalse(promoted.isTeamAutoPublicationGranted());
        assertFalse(removed.isTeamAutoPublicationGranted());
        verify(autoPublicationHandoff).handOffMember(1L, "publisher");
        verify(autoPublicationHandoff).handOffMember(1L, "leaver");
        verify(auditLogService, times(2)).record(eq("owner"), eq("TEAM_AUTO_PUBLICATION_CHANGE"),
                eq("NAMESPACE_MEMBER"), isNull(), isNull(), isNull(), isNull(), anyString());
    }

    @Test
    void addMember_shouldAddMemberSuccessfully() {
        Long namespaceId = 1L;
        String userId = "user-2";
        NamespaceRole role = NamespaceRole.MEMBER;
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");

        when(namespaceService.getNamespace(namespaceId)).thenReturn(namespace);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId))
                .thenReturn(Optional.empty());
        when(namespaceMemberRepository.save(any(NamespaceMember.class)))
                .thenReturn(new NamespaceMember(namespaceId, userId, role));

        NamespaceMember result = namespaceMemberService.addMember(namespaceId, userId, role, "user-99");

        assertNotNull(result);
        verify(namespaceMemberRepository).save(any(NamespaceMember.class));
    }

    @Test
    void addMember_shouldThrowExceptionForOwnerRole() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        when(namespaceService.getNamespace(1L)).thenReturn(namespace);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(true);

        assertThrows(DomainBadRequestException.class, () ->
                namespaceMemberService.addMember(1L, "user-2", NamespaceRole.OWNER, "user-99"));
    }

    @Test
    void addMember_shouldRequireAdminOrOwner() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        when(namespaceService.getNamespace(1L)).thenReturn(namespace);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(true);
        doThrow(new DomainForbiddenException("error.namespace.admin.required")).when(namespaceService).assertAdminOrOwner(1L, "user-99");

        assertThrows(DomainForbiddenException.class, () ->
                namespaceMemberService.addMember(1L, "user-2", NamespaceRole.MEMBER, "user-99"));
    }

    @Test
    void addMember_shouldThrowExceptionWhenMemberExists() {
        Long namespaceId = 1L;
        String userId = "user-2";
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        when(namespaceService.getNamespace(namespaceId)).thenReturn(namespace);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId))
                .thenReturn(Optional.of(new NamespaceMember()));

        assertThrows(DomainBadRequestException.class, () ->
                namespaceMemberService.addMember(namespaceId, userId, NamespaceRole.MEMBER, "user-99"));
    }

    @Test
    void addMember_shouldRejectFrozenNamespace() {
        Long namespaceId = 1L;
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        namespace.setStatus(NamespaceStatus.FROZEN);
        when(namespaceService.getNamespace(namespaceId)).thenReturn(namespace);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);

        assertThrows(DomainBadRequestException.class, () ->
                namespaceMemberService.addMember(namespaceId, "user-2", NamespaceRole.MEMBER, "user-99"));
    }

    @Test
    void removeMember_shouldThrowExceptionForOwner() {
        Long namespaceId = 1L;
        String userId = "user-2";
        NamespaceMember ownerMember = new NamespaceMember(namespaceId, userId, NamespaceRole.OWNER);
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        when(namespaceService.getNamespace(namespaceId)).thenReturn(namespace);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId))
                .thenReturn(Optional.of(ownerMember));

        assertThrows(DomainBadRequestException.class, () ->
                namespaceMemberService.removeMember(namespaceId, userId, "user-99"));
    }

    @Test
    void removeMember_shouldThrowExceptionWhenMemberNotFound() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        when(namespaceService.getNamespace(1L)).thenReturn(namespace);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "user-2"))
                .thenReturn(Optional.empty());

        assertThrows(DomainBadRequestException.class, () ->
                namespaceMemberService.removeMember(1L, "user-2", "user-99"));
    }

    @Test
    void updateMemberRole_shouldRejectArchivedNamespace() {
        Long namespaceId = 1L;
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        namespace.setStatus(NamespaceStatus.ARCHIVED);
        when(namespaceService.getNamespace(namespaceId)).thenReturn(namespace);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);

        assertThrows(DomainBadRequestException.class, () ->
                namespaceMemberService.updateMemberRole(namespaceId, "user-2", NamespaceRole.ADMIN, "user-99"));
    }

    @Test
    void updateMemberRole_shouldUpdateRoleSuccessfully() {
        Long namespaceId = 1L;
        String userId = "user-2";
        NamespaceMember member = new NamespaceMember(namespaceId, userId, NamespaceRole.MEMBER);
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        when(namespaceService.getNamespace(namespaceId)).thenReturn(namespace);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId))
                .thenReturn(Optional.of(member));
        when(namespaceMemberRepository.save(any(NamespaceMember.class))).thenReturn(member);

        NamespaceMember result = namespaceMemberService.updateMemberRole(namespaceId, userId, NamespaceRole.ADMIN, "user-99");

        assertNotNull(result);
        verify(namespaceMemberRepository).save(member);
    }

    @Test
    void updateMemberRole_shouldThrowExceptionForOwnerRole() {
        Long namespaceId = 1L;
        String userId = "user-2";
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        when(namespaceService.getNamespace(namespaceId)).thenReturn(namespace);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(true);

        assertThrows(DomainBadRequestException.class, () ->
                namespaceMemberService.updateMemberRole(namespaceId, userId, NamespaceRole.OWNER, "user-99"));
    }

    @Test
    void updateMemberRole_shouldThrowExceptionWhenMemberNotFound() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        when(namespaceService.getNamespace(1L)).thenReturn(namespace);
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "user-2"))
                .thenReturn(Optional.empty());

        assertThrows(DomainBadRequestException.class, () ->
                namespaceMemberService.updateMemberRole(1L, "user-2", NamespaceRole.ADMIN, "user-99"));
    }

    @Test
    void transferOwnership_shouldTransferOwnershipSuccessfully() {
        Long namespaceId = 1L;
        String currentOwnerId = "user-2";
        String newOwnerId = "user-3";

        NamespaceMember currentOwner = new NamespaceMember(namespaceId, currentOwnerId, NamespaceRole.OWNER);
        NamespaceMember newOwner = new NamespaceMember(namespaceId, newOwnerId, NamespaceRole.ADMIN);
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");

        when(namespaceService.getNamespace(namespaceId)).thenReturn(namespace);
        when(namespaceAccessPolicy.canTransferOwnership(namespace)).thenReturn(true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, currentOwnerId))
                .thenReturn(Optional.of(currentOwner));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, newOwnerId))
                .thenReturn(Optional.of(newOwner));
        when(namespaceMemberRepository.save(any(NamespaceMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        namespaceMemberService.transferOwnership(namespaceId, currentOwnerId, newOwnerId);

        verify(namespaceMemberRepository, times(2)).save(any(NamespaceMember.class));
    }

    @Test
    void transferOwnership_shouldThrowExceptionWhenCurrentOwnerNotFound() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        when(namespaceService.getNamespace(1L)).thenReturn(namespace);
        when(namespaceAccessPolicy.canTransferOwnership(namespace)).thenReturn(true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "user-2"))
                .thenReturn(Optional.empty());

        assertThrows(DomainBadRequestException.class, () ->
                namespaceMemberService.transferOwnership(1L, "user-2", "user-3"));
    }

    @Test
    void transferOwnership_shouldThrowExceptionWhenCurrentUserIsNotOwner() {
        Long namespaceId = 1L;
        String currentOwnerId = "user-2";
        NamespaceMember notOwner = new NamespaceMember(namespaceId, currentOwnerId, NamespaceRole.ADMIN);
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        when(namespaceService.getNamespace(namespaceId)).thenReturn(namespace);
        when(namespaceAccessPolicy.canTransferOwnership(namespace)).thenReturn(true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, currentOwnerId))
                .thenReturn(Optional.of(notOwner));

        assertThrows(DomainBadRequestException.class, () ->
                namespaceMemberService.transferOwnership(namespaceId, currentOwnerId, "user-3"));
    }

    @Test
    void transferOwnership_shouldThrowExceptionWhenNewOwnerNotFound() {
        Long namespaceId = 1L;
        String currentOwnerId = "user-2";
        String newOwnerId = "user-3";
        NamespaceMember currentOwner = new NamespaceMember(namespaceId, currentOwnerId, NamespaceRole.OWNER);
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");

        when(namespaceService.getNamespace(namespaceId)).thenReturn(namespace);
        when(namespaceAccessPolicy.canTransferOwnership(namespace)).thenReturn(true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, currentOwnerId))
                .thenReturn(Optional.of(currentOwner));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, newOwnerId))
                .thenReturn(Optional.empty());

        assertThrows(DomainBadRequestException.class, () ->
                namespaceMemberService.transferOwnership(namespaceId, currentOwnerId, newOwnerId));
    }

    @Test
    void transferOwnership_shouldRejectFrozenNamespace() {
        Long namespaceId = 1L;
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        namespace.setStatus(NamespaceStatus.FROZEN);
        when(namespaceService.getNamespace(namespaceId)).thenReturn(namespace);
        when(namespaceAccessPolicy.canTransferOwnership(namespace)).thenReturn(false);

        assertThrows(DomainBadRequestException.class, () ->
                namespaceMemberService.transferOwnership(namespaceId, "user-2", "user-3"));
    }

    @Test
    void getMemberRole_shouldReturnRole() {
        Long namespaceId = 1L;
        String userId = "user-2";
        NamespaceMember member = new NamespaceMember(namespaceId, userId, NamespaceRole.ADMIN);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId))
                .thenReturn(Optional.of(member));

        Optional<NamespaceRole> result = namespaceMemberService.getMemberRole(namespaceId, userId);

        assertTrue(result.isPresent());
        assertEquals(NamespaceRole.ADMIN, result.get());
    }

    @Test
    void getMemberRole_shouldReturnEmptyWhenMemberNotFound() {
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "user-2"))
                .thenReturn(Optional.empty());

        Optional<NamespaceRole> result = namespaceMemberService.getMemberRole(1L, "user-2");

        assertFalse(result.isPresent());
    }
}
