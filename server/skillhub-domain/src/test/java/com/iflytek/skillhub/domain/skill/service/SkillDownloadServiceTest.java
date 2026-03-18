package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.event.SkillDownloadedEvent;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;

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
    private SkillFileRepository skillFileRepository;
    @Mock
    private SkillTagRepository skillTagRepository;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private VisibilityChecker visibilityChecker;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SkillDownloadService service;
    private SkillSlugResolutionService skillSlugResolutionService;

    @BeforeEach
    void setUp() {
        skillSlugResolutionService = new SkillSlugResolutionService(skillRepository);
        service = new SkillDownloadService(
                namespaceRepository,
                skillRepository,
                skillVersionRepository,
                skillFileRepository,
                skillTagRepository,
                objectStorageService,
                visibilityChecker,
                eventPublisher,
                skillSlugResolutionService
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
        skill.setDisplayName("Test Skill");
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
        when(objectStorageService.generatePresignedUrl(eq(storageKey), any(), eq("Test Skill-1.0.0.zip"))).thenReturn(null);

        // Act
        SkillDownloadService.DownloadResult result = service.downloadLatest(namespaceSlug, skillSlug, userId, userNsRoles);

        // Assert
        assertNotNull(result);
        assertEquals("Test Skill-1.0.0.zip", result.filename());
        assertEquals(1000L, result.contentLength());
        assertNotNull(result.content());
        verify(skillRepository).incrementDownloadCount(1L);
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
        skill.setDisplayName("Test Skill");
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
        when(objectStorageService.generatePresignedUrl(eq(storageKey), any(), eq("Test Skill-1.0.0.zip"))).thenReturn(null);

        // Act
        SkillDownloadService.DownloadResult result = service.downloadByTag(namespaceSlug, skillSlug, tagName, userId, userNsRoles);

        // Assert
        assertNotNull(result);
        assertEquals("Test Skill-1.0.0.zip", result.filename());
        assertNotNull(result.content());
        verify(skillRepository).incrementDownloadCount(1L);
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
        skill.setDisplayName("Generate Commit Message");
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
        when(objectStorageService.generatePresignedUrl(eq(storageKey), any(), eq("Generate Commit Message-1.0.0.zip")))
                .thenReturn("http://minio.local/presigned");

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

    @Test
    void testDownloadVersion_ShouldFallbackToBundledFilesWhenBundleIsMissing() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String versionStr = "1.0.0";
        String userId = "user-100";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, userId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setDisplayName("Generate Commit Message");
        skill.setStatus(SkillStatus.ACTIVE);
        SkillVersion version = new SkillVersion(1L, versionStr, userId);
        setId(version, 10L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        SkillFile file = new SkillFile(10L, "SKILL.md", 4L, "text/markdown", "hash", "skills/1/10/SKILL.md");

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(visibilityChecker.canAccess(skill, userId, userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findBySkillIdAndVersion(1L, versionStr)).thenReturn(Optional.of(version));
        when(objectStorageService.exists("packages/1/10/bundle.zip")).thenReturn(false);
        when(skillFileRepository.findByVersionId(10L)).thenReturn(List.of(file));
        when(objectStorageService.exists("skills/1/10/SKILL.md")).thenReturn(true);
        when(objectStorageService.getObject("skills/1/10/SKILL.md")).thenReturn(new ByteArrayInputStream("test".getBytes()));

        SkillDownloadService.DownloadResult result = service.downloadVersion(namespaceSlug, skillSlug, versionStr, userId, userNsRoles);

        assertNull(result.presignedUrl());
        assertEquals("Generate Commit Message-1.0.0.zip", result.filename());
        assertEquals("application/zip", result.contentType());
        assertTrue(result.contentLength() > 0);

        try (ZipInputStream zipInputStream = new ZipInputStream(result.content())) {
            var entry = zipInputStream.getNextEntry();
            assertNotNull(entry);
            assertEquals("SKILL.md", entry.getName());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            zipInputStream.transferTo(output);
            assertEquals("test", output.toString());
        }

        verify(skillRepository).incrementDownloadCount(1L);
        verify(eventPublisher).publishEvent(any(SkillDownloadedEvent.class));
    }

    @Test
    void testDownloadVersion_AllowsAnonymousForGlobalPublicSkill() throws Exception {
        Namespace namespace = new Namespace("global", "Global", "system");
        setId(namespace, 1L);
        namespace.setType(NamespaceType.GLOBAL);

        Skill skill = new Skill(1L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setDisplayName("Demo Skill");
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);

        SkillVersion version = new SkillVersion(1L, "1.0.0", "owner-1");
        setId(version, 10L);
        version.setStatus(SkillVersionStatus.PUBLISHED);

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, "demo-skill")).thenReturn(List.of(skill));
        when(visibilityChecker.canAccess(skill, null, Map.of())).thenReturn(true);
        when(skillVersionRepository.findBySkillIdAndVersion(1L, "1.0.0")).thenReturn(Optional.of(version));
        when(objectStorageService.exists("packages/1/10/bundle.zip")).thenReturn(false);
        when(skillFileRepository.findByVersionId(10L)).thenReturn(List.of(
                new SkillFile(10L, "SKILL.md", 4L, "text/markdown", "hash", "skills/1/10/SKILL.md")));
        when(objectStorageService.exists("skills/1/10/SKILL.md")).thenReturn(true);
        when(objectStorageService.getObject("skills/1/10/SKILL.md")).thenReturn(new ByteArrayInputStream("test".getBytes()));

        SkillDownloadService.DownloadResult result = service.downloadVersion("global", "demo-skill", "1.0.0", null, Map.of());

        assertNotNull(result);
        assertEquals("Demo Skill-1.0.0.zip", result.filename());
    }

    @Test
    void testDownloadVersion_RejectsAnonymousForTeamNamespacePublicSkill() throws Exception {
        Namespace namespace = new Namespace("team-ai", "Team AI", "owner-1");
        setId(namespace, 2L);
        namespace.setType(NamespaceType.TEAM);

        Skill skill = new Skill(2L, "demo-skill", "owner-1", SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);

        when(namespaceRepository.findBySlug("team-ai")).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(2L, "demo-skill")).thenReturn(List.of(skill));

        assertThrows(DomainForbiddenException.class, () ->
                service.downloadVersion("team-ai", "demo-skill", "1.0.0", null, Map.of()));

        verify(visibilityChecker, never()).canAccess(any(), any(), anyMap());
    }

    private void setId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
