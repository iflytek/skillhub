package com.iflytek.skillhub.domain.namespace;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, operatorUserId))
                .thenReturn(Optional.of(new NamespaceMember(namespaceId, operatorUserId, NamespaceRole.OWNER)));
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(true);

        assertThrows(DomainBadRequestException.class, () ->
                namespaceService.updateNamespace(namespaceId, "Name", "Desc", null, operatorUserId));
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
}
