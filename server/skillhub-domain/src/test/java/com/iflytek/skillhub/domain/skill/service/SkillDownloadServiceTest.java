package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.event.SkillDownloadedEvent;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.storage.ObjectMetadata;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillDownloadServiceTest {

    @Mock
    private NamespaceRepository namespaceRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private SkillVersionRepository skillVersionRepository;
    @Mock
    private SkillTagRepository skillTagRepository;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private VisibilityChecker visibilityChecker;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SkillDownloadService service;

    @BeforeEach
    void setUp() {
        service = new SkillDownloadService(
                namespaceRepository,
                skillRepository,
                skillVersionRepository,
                skillTagRepository,
                objectStorageService,
                visibilityChecker,
                eventPublisher
        );
    }

    @Test
    void testDownloadLatest_Success() throws Exception {
        // Arrange
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String userId = "user-100";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, userId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);

        SkillVersion version = new SkillVersion(1L, "1.0.0", userId);
        setId(version, 10L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        String storageKey = "packages/1/10/bundle.zip";
        InputStream content = new ByteArrayInputStream("test".getBytes());
        ObjectMetadata metadata = new ObjectMetadata(1000L, "application/zip", Instant.now());

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(visibilityChecker.canAccess(skill, userId, userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(objectStorageService.exists(storageKey)).thenReturn(true);
        when(objectStorageService.getMetadata(storageKey)).thenReturn(metadata);
        when(objectStorageService.getObject(storageKey)).thenReturn(content);
        when(objectStorageService.generatePresignedUrl(eq(storageKey), any())).thenReturn(null);

        // Act
        SkillDownloadService.DownloadResult result = service.downloadLatest(namespaceSlug, skillSlug, userId, userNsRoles);

        // Assert
        assertNotNull(result);
        assertEquals("test-skill-1.0.0.zip", result.filename());
        assertEquals(1000L, result.contentLength());
        assertNotNull(result.content());
        verify(eventPublisher).publishEvent(any(SkillDownloadedEvent.class));
    }

    @Test
    void testDownloadByTag_Success() throws Exception {
        // Arrange
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String tagName = "stable";
        String userId = "user-100";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, userId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        SkillTag tag = new SkillTag(1L, tagName, 10L, userId);
        SkillVersion version = new SkillVersion(1L, "1.0.0", userId);
        setId(version, 10L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        String storageKey = "packages/1/10/bundle.zip";
        InputStream content = new ByteArrayInputStream("test".getBytes());
        ObjectMetadata metadata = new ObjectMetadata(1000L, "application/zip", Instant.now());

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(visibilityChecker.canAccess(skill, userId, userNsRoles)).thenReturn(true);
        when(skillTagRepository.findBySkillIdAndTagName(1L, tagName)).thenReturn(Optional.of(tag));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(objectStorageService.exists(storageKey)).thenReturn(true);
        when(objectStorageService.getMetadata(storageKey)).thenReturn(metadata);
        when(objectStorageService.getObject(storageKey)).thenReturn(content);
        when(objectStorageService.generatePresignedUrl(eq(storageKey), any())).thenReturn(null);

        // Act
        SkillDownloadService.DownloadResult result = service.downloadByTag(namespaceSlug, skillSlug, tagName, userId, userNsRoles);

        // Assert
        assertNotNull(result);
        assertEquals("test-skill-1.0.0.zip", result.filename());
        assertNotNull(result.content());
        verify(eventPublisher).publishEvent(any(SkillDownloadedEvent.class));
    }

    @Test
    void testDownloadVersion_WithPresignedUrlStillProvidesStreamFallback() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String versionStr = "1.0.0";
        String userId = "user-100";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, userId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        SkillVersion version = new SkillVersion(1L, versionStr, userId);
        setId(version, 10L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        String storageKey = "packages/1/10/bundle.zip";
        InputStream content = new ByteArrayInputStream("test".getBytes());
        ObjectMetadata metadata = new ObjectMetadata(1000L, "application/zip", Instant.now());

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(visibilityChecker.canAccess(skill, userId, userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findBySkillIdAndVersion(1L, versionStr)).thenReturn(Optional.of(version));
        when(objectStorageService.exists(storageKey)).thenReturn(true);
        when(objectStorageService.getMetadata(storageKey)).thenReturn(metadata);
        when(objectStorageService.getObject(storageKey)).thenReturn(content);
        when(objectStorageService.generatePresignedUrl(eq(storageKey), any())).thenReturn("http://minio.local/presigned");

        SkillDownloadService.DownloadResult result = service.downloadVersion(namespaceSlug, skillSlug, versionStr, userId, userNsRoles);

        assertEquals("http://minio.local/presigned", result.presignedUrl());
        assertNotNull(result.content());
    }

    @Test
    void testDownloadVersion_ShouldRejectDraftVersion() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String versionStr = "1.0.0";
        String userId = "user-100";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, userId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        SkillVersion version = new SkillVersion(1L, versionStr, userId);
        setId(version, 10L);
        version.setStatus(SkillVersionStatus.DRAFT);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(visibilityChecker.canAccess(skill, userId, userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findBySkillIdAndVersion(1L, versionStr)).thenReturn(Optional.of(version));

        assertThrows(DomainBadRequestException.class, () ->
                service.downloadVersion(namespaceSlug, skillSlug, versionStr, userId, userNsRoles));
    }

    private void setId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
