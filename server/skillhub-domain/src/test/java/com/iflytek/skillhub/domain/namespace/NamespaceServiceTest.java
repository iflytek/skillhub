package com.iflytek.skillhub.domain.namespace;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NamespaceServiceTest {

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private NamespaceMemberRepository namespaceMemberRepository;

    @Mock
    private NamespaceAccessPolicy namespaceAccessPolicy;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private ReviewTaskRepository reviewTaskRepository;

    @Mock
    private PromotionRequestRepository promotionRequestRepository;

    @InjectMocks
    private NamespaceService namespaceService;

    @Test
    void createNamespace_shouldCreateNamespaceAndOwnerMember() {
        String slug = "test-namespace";
        String displayName = "Test Namespace";
        String description = "Test description";
        String creatorUserId = "user-1";

        Namespace savedNamespace = new Namespace(slug, displayName, creatorUserId);
        when(namespaceRepository.findBySlug(slug)).thenReturn(Optional.empty());
        when(namespaceRepository.save(any(Namespace.class))).thenReturn(savedNamespace);
        when(namespaceMemberRepository.save(any(NamespaceMember.class))).thenReturn(new NamespaceMember());

        Namespace result = namespaceService.createNamespace(slug, displayName, description, creatorUserId);

        assertNotNull(result);
        assertEquals(slug, result.getSlug());
        assertEquals(displayName, result.getDisplayName());
        verify(namespaceRepository).save(any(Namespace.class));
        verify(namespaceMemberRepository).save(any(NamespaceMember.class));
    }

    @Test
    void createNamespace_shouldThrowExceptionWhenSlugExists() {
        String slug = "existing-slug";
        when(namespaceRepository.findBySlug(slug)).thenReturn(Optional.of(new Namespace()));

        assertThrows(DomainBadRequestException.class, () ->
                namespaceService.createNamespace(slug, "Name", "Desc", "user-1"));
    }

    @Test
    void createNamespace_shouldThrowExceptionForInvalidSlug() {
        assertThrows(DomainBadRequestException.class, () ->
                namespaceService.createNamespace("INVALID", "Name", "Desc", "user-1"));
    }

    @Test
    void updateNamespace_shouldUpdateFields() {
        Long namespaceId = 1L;
        String operatorUserId = "user-1";
        Namespace namespace = new Namespace("slug", "Old Name", "user-1");
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, operatorUserId))
                .thenReturn(Optional.of(new NamespaceMember(namespaceId, operatorUserId, NamespaceRole.OWNER)));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.canMutateSettings(namespace)).thenReturn(true);
        when(namespaceRepository.save(any(Namespace.class))).thenReturn(namespace);

        Namespace result = namespaceService.updateNamespace(
                namespaceId,
                "New Name",
                "New Desc",
                "http://avatar.url",
                operatorUserId
        );

        assertNotNull(result);
        verify(namespaceRepository).save(namespace);
    }

    @Test
    void updateNamespace_shouldThrowExceptionWhenNotFound() {
        when(namespaceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(DomainBadRequestException.class, () ->
                namespaceService.updateNamespace(1L, "Name", "Desc", null, "user-1"));
    }

    @Test
    void updateNamespace_shouldThrowExceptionWhenOperatorLacksPrivilege() {
        Long namespaceId = 1L;
        String operatorUserId = "user-2";
        Namespace namespace = new Namespace("slug", "Old Name", "user-1");
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, operatorUserId))
                .thenReturn(Optional.of(new NamespaceMember(namespaceId, operatorUserId, NamespaceRole.MEMBER)));

        assertThrows(DomainForbiddenException.class, () ->
                namespaceService.updateNamespace(namespaceId, "Name", "Desc", null, operatorUserId));
    }

    @Test
    void updateNamespace_shouldRejectFrozenNamespace() {
        Long namespaceId = 1L;
        String operatorUserId = "user-1";
        Namespace namespace = new Namespace("slug", "Old Name", "user-1");
        namespace.setStatus(NamespaceStatus.FROZEN);
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, operatorUserId))
                .thenReturn(Optional.of(new NamespaceMember(namespaceId, operatorUserId, NamespaceRole.OWNER)));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.canMutateSettings(namespace)).thenReturn(false);

        assertThrows(DomainBadRequestException.class, () ->
                namespaceService.updateNamespace(namespaceId, "Name", "Desc", null, operatorUserId));
    }

    @Test
    void updateNamespace_shouldRejectGlobalNamespaceMutation() {
        Long namespaceId = 1L;
        String operatorUserId = "user-1";
        Namespace namespace = new Namespace("global", "Global", "system");
        namespace.setType(NamespaceType.GLOBAL);
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(true);

        assertThrows(DomainBadRequestException.class, () ->
                namespaceService.updateNamespace(namespaceId, "Name", "Desc", null, operatorUserId));
    }

    @Test
    void updateNamespace_shouldRejectGlobalNamespaceMutationBeforeMembershipChecks() {
        Long namespaceId = 1L;
        String operatorUserId = "user-404";
        Namespace namespace = new Namespace("global", "Global", "system");
        namespace.setType(NamespaceType.GLOBAL);
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(true);

        DomainBadRequestException exception = assertThrows(DomainBadRequestException.class, () ->
                namespaceService.updateNamespace(namespaceId, "Name", "Desc", null, operatorUserId));

        assertEquals("error.namespace.system.immutable", exception.messageCode());
        verify(namespaceMemberRepository, never()).findByNamespaceIdAndUserId(namespaceId, operatorUserId);
    }

    @Test
    void getNamespaceBySlug_shouldReturnNamespace() {
        String slug = "test-slug";
        Namespace namespace = new Namespace(slug, "Name", "user-1");
        when(namespaceRepository.findBySlug(slug)).thenReturn(Optional.of(namespace));

        Namespace result = namespaceService.getNamespaceBySlug(slug);

        assertNotNull(result);
        assertEquals(slug, result.getSlug());
    }

    @Test
    void getNamespaceBySlug_shouldThrowExceptionWhenNotFound() {
        when(namespaceRepository.findBySlug("nonexistent")).thenReturn(Optional.empty());

        assertThrows(DomainBadRequestException.class, () ->
                namespaceService.getNamespaceBySlug("nonexistent"));
    }

    @Test
    void assertMember_shouldAllowExistingMember() {
        Long namespaceId = 1L;
        String userId = "user-1";
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId))
                .thenReturn(Optional.of(new NamespaceMember(namespaceId, userId, NamespaceRole.MEMBER)));

        assertDoesNotThrow(() -> namespaceService.assertMember(namespaceId, userId));
    }

    @Test
    void assertMember_shouldRejectNonMember() {
        Long namespaceId = 1L;
        String userId = "user-404";
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId))
                .thenReturn(Optional.empty());

        assertThrows(DomainForbiddenException.class, () ->
                namespaceService.assertMember(namespaceId, userId));
    }

    @Test
    void deleteNamespace_shouldDeleteMembersAndNamespaceForOwner() {
        Long namespaceId = 1L;
        String operatorUserId = "owner-1";
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.canDelete(namespace, NamespaceRole.OWNER)).thenReturn(true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, operatorUserId))
                .thenReturn(Optional.of(new NamespaceMember(namespaceId, operatorUserId, NamespaceRole.OWNER)));
        when(skillRepository.existsByNamespaceId(namespaceId)).thenReturn(false);
        when(reviewTaskRepository.existsByNamespaceId(namespaceId)).thenReturn(false);
        when(promotionRequestRepository.existsByTargetNamespaceId(namespaceId)).thenReturn(false);

        namespaceService.deleteNamespace(namespaceId, operatorUserId);

        verify(namespaceMemberRepository).deleteByNamespaceId(namespaceId);
        verify(namespaceRepository).delete(namespace);
    }

    @Test
    void deleteNamespace_shouldRejectAdminUser() {
        Long namespaceId = 1L;
        String operatorUserId = "admin-1";
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.canDelete(namespace, NamespaceRole.ADMIN)).thenReturn(false);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, operatorUserId))
                .thenReturn(Optional.of(new NamespaceMember(namespaceId, operatorUserId, NamespaceRole.ADMIN)));

        DomainForbiddenException exception = assertThrows(DomainForbiddenException.class, () ->
                namespaceService.deleteNamespace(namespaceId, operatorUserId));

        assertEquals("error.namespace.owner.required", exception.messageCode());
        verify(namespaceRepository, never()).delete(any());
    }

    @Test
    void deleteNamespace_shouldRejectNamespaceWithDependentData() {
        Long namespaceId = 1L;
        String operatorUserId = "owner-1";
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.canDelete(namespace, NamespaceRole.OWNER)).thenReturn(true);
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, operatorUserId))
                .thenReturn(Optional.of(new NamespaceMember(namespaceId, operatorUserId, NamespaceRole.OWNER)));
        when(skillRepository.existsByNamespaceId(namespaceId)).thenReturn(true);

        DomainBadRequestException exception = assertThrows(DomainBadRequestException.class, () ->
                namespaceService.deleteNamespace(namespaceId, operatorUserId));

        assertEquals("error.namespace.delete.hasDependencies", exception.messageCode());
        verify(namespaceMemberRepository, never()).deleteByNamespaceId(namespaceId);
        verify(namespaceRepository, never()).delete(any());
    }

    @Test
    void canDelete_shouldReturnFalseWhenRoleCannotDelete() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");

        assertFalse(namespaceService.canDelete(namespace, NamespaceRole.ADMIN));
        verify(skillRepository, never()).existsByNamespaceId(any());
    }

    @Test
    void canDelete_shouldReflectDependencyChecksForOwner() {
        Long namespaceId = 1L;
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        when(namespaceAccessPolicy.canDelete(namespace, NamespaceRole.OWNER)).thenReturn(true);
        setField(namespace, "id", namespaceId);
        when(skillRepository.existsByNamespaceId(namespaceId)).thenReturn(false);
        when(reviewTaskRepository.existsByNamespaceId(namespaceId)).thenReturn(false);
        when(promotionRequestRepository.existsByTargetNamespaceId(namespaceId)).thenReturn(true);

        assertFalse(namespaceService.canDelete(namespace, NamespaceRole.OWNER));
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            fail(e);
        }
    }
}
