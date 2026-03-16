package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillQueryServiceTest {

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

    private SkillQueryService service;

    @BeforeEach
    void setUp() {
        service = new SkillQueryService(
                namespaceRepository,
                skillRepository,
                skillVersionRepository,
                skillFileRepository,
                skillTagRepository,
                objectStorageService,
                visibilityChecker
        );
    }

    @Test
    void testGetSkillDetail_Success() throws Exception {
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
        skill.setSummary("Test Summary");
        skill.setLatestVersionId(10L);

        SkillVersion version = new SkillVersion(1L, "1.0.0", userId);
        setId(version, 10L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, userId, userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));

        // Act
        SkillQueryService.SkillDetailDTO result = service.getSkillDetail(namespaceSlug, skillSlug, userId, userNsRoles);

        // Assert
        assertNotNull(result);
        assertEquals(skillSlug, result.slug());
        assertEquals("Test Skill", result.displayName());
        assertEquals("1.0.0", result.latestVersion());
    }

    @Test
    void testGetSkillDetail_AccessDenied() throws Exception {
        // Arrange
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String userId = "user-100";
        Map<Long, NamespaceRole> userNsRoles = Map.of();

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "user-200", SkillVisibility.PRIVATE);
        setId(skill, 1L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, userId, userNsRoles)).thenReturn(false);

        // Act & Assert
        assertThrows(DomainForbiddenException.class, () ->
                service.getSkillDetail(namespaceSlug, skillSlug, userId, userNsRoles)
        );
    }

    @Test
    void testGetSkillDetail_ShouldHideArchivedNamespaceFromAnonymousUsers() throws Exception {
        String namespaceSlug = "archived-team";
        String skillSlug = "test-skill";

        Namespace namespace = new Namespace(namespaceSlug, "Archived Team", "user-1");
        namespace.setStatus(NamespaceStatus.ARCHIVED);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "user-200", SkillVisibility.PUBLIC);
        setId(skill, 1L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));

        assertThrows(DomainForbiddenException.class, () ->
                service.getSkillDetail(namespaceSlug, skillSlug, null, Map.of()));
    }

    @Test
    void testListSkillsByNamespace() throws Exception {
        // Arrange
        String namespaceSlug = "test-ns";
        String userId = "user-100";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);
        Pageable pageable = PageRequest.of(0, 10);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill skill1 = new Skill(1L, "skill1", userId, SkillVisibility.PUBLIC);
        setId(skill1, 1L);
        Skill skill2 = new Skill(1L, "skill2", userId, SkillVisibility.PRIVATE);
        setId(skill2, 2L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndStatus(1L, SkillStatus.ACTIVE)).thenReturn(List.of(skill1, skill2));
        when(visibilityChecker.canAccess(skill1, userId, userNsRoles)).thenReturn(true);
        when(visibilityChecker.canAccess(skill2, userId, userNsRoles)).thenReturn(false);

        // Act
        Page<Skill> result = service.listSkillsByNamespace(namespaceSlug, userId, userNsRoles, pageable);

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals("skill1", result.getContent().get(0).getSlug());
    }

    @Test
    void testListFiles() throws Exception {
        // Arrange
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String version = "1.0.0";

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "user-100", SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        SkillVersion skillVersion = new SkillVersion(1L, version, "user-100");
        setId(skillVersion, 1L);
        skillVersion.setStatus(SkillVersionStatus.PUBLISHED);
        SkillFile file1 = new SkillFile(1L, "file1.txt", 100L, "text/plain", "hash1", "key1");
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, "user-100", userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findBySkillIdAndVersion(1L, version)).thenReturn(Optional.of(skillVersion));
        when(skillFileRepository.findByVersionId(1L)).thenReturn(List.of(file1));

        // Act
        List<SkillFile> result = service.listFiles(namespaceSlug, skillSlug, version, "user-100", userNsRoles);

        // Assert
        assertEquals(1, result.size());
        assertEquals("file1.txt", result.get(0).getFilePath());
    }

    @Test
    void testListFiles_ShouldRejectDraftVersion() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String version = "1.0.0";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "user-100", SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        SkillVersion skillVersion = new SkillVersion(1L, version, "user-100");
        setId(skillVersion, 1L);
        skillVersion.setStatus(SkillVersionStatus.DRAFT);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, "user-100", userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findBySkillIdAndVersion(1L, version)).thenReturn(Optional.of(skillVersion));

        assertThrows(DomainBadRequestException.class, () ->
                service.listFiles(namespaceSlug, skillSlug, version, "user-100", userNsRoles));
    }

    @Test
    void testGetFileContent_ShouldTranslateMissingStorageObject() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String version = "1.0.0";
        String filePath = "SKILL.md";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "user-100", SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        SkillVersion skillVersion = new SkillVersion(1L, version, "user-100");
        setId(skillVersion, 1L);
        skillVersion.setStatus(SkillVersionStatus.PUBLISHED);
        SkillFile file = new SkillFile(1L, filePath, 100L, "text/markdown", "hash1", "skills/1/1/SKILL.md");

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, "user-100", userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findBySkillIdAndVersion(1L, version)).thenReturn(Optional.of(skillVersion));
        when(skillFileRepository.findByVersionId(1L)).thenReturn(List.of(file));
        when(objectStorageService.getObject(file.getStorageKey()))
                .thenThrow(new UncheckedIOException(new java.io.FileNotFoundException(file.getStorageKey())));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () ->
                service.getFileContent(namespaceSlug, skillSlug, version, filePath, "user-100", userNsRoles));

        assertEquals("error.skill.file.notFound", ex.messageCode());
        assertArrayEquals(new Object[]{filePath}, ex.messageArgs());
    }

    @Test
    void testGetVersionDetail_ShouldReturnMetadataPayload() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String version = "1.0.0";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "user-100", SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        SkillVersion skillVersion = new SkillVersion(1L, version, "user-100");
        setId(skillVersion, 10L);
        skillVersion.setStatus(SkillVersionStatus.PUBLISHED);
        skillVersion.setParsedMetadataJson("{\"name\":\"test-skill\"}");
        skillVersion.setManifestJson("[{\"path\":\"SKILL.md\"}]");

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, "user-100", userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findBySkillIdAndVersion(1L, version)).thenReturn(Optional.of(skillVersion));

        SkillQueryService.SkillVersionDetailDTO result = service.getVersionDetail(
                namespaceSlug,
                skillSlug,
                version,
                "user-100",
                userNsRoles
        );

        assertEquals("{\"name\":\"test-skill\"}", result.parsedMetadataJson());
        assertEquals("[{\"path\":\"SKILL.md\"}]", result.manifestJson());
    }

    @Test
    void testListFilesByTag_ShouldResolveLatestTag() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "user-100", SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(11L);
        SkillVersion latestVersion = new SkillVersion(1L, "1.1.0", "user-100");
        setId(latestVersion, 11L);
        latestVersion.setStatus(SkillVersionStatus.PUBLISHED);
        SkillFile file = new SkillFile(11L, "README.md", 12L, "text/markdown", "hash", "storage-key");

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, "user-100", userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findById(11L)).thenReturn(Optional.of(latestVersion));
        when(skillFileRepository.findByVersionId(11L)).thenReturn(List.of(file));

        List<SkillFile> result = service.listFilesByTag(namespaceSlug, skillSlug, "latest", "user-100", userNsRoles);

        assertEquals(1, result.size());
        assertEquals("README.md", result.get(0).getFilePath());
    }

    @Test
    void testListVersions_ShouldIncludePendingAndRejectedForLifecycleManagers() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String ownerId = "user-100";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.OWNER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", ownerId);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, ownerId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);

        SkillVersion published = new SkillVersion(1L, "1.0.0", ownerId);
        setId(published, 10L);
        published.setStatus(SkillVersionStatus.PUBLISHED);
        published.setPublishedAt(java.time.LocalDateTime.of(2026, 3, 1, 10, 0));

        SkillVersion pending = new SkillVersion(1L, "1.1.0", ownerId);
        setId(pending, 11L);
        pending.setStatus(SkillVersionStatus.PENDING_REVIEW);

        SkillVersion rejected = new SkillVersion(1L, "1.2.0", ownerId);
        setId(rejected, 12L);
        rejected.setStatus(SkillVersionStatus.REJECTED);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, ownerId, userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findBySkillId(1L)).thenReturn(List.of(pending, published, rejected));

        Page<SkillVersion> result = service.listVersions(namespaceSlug, skillSlug, ownerId, userNsRoles, PageRequest.of(0, 20));

        assertEquals(List.of("1.0.0", "1.2.0", "1.1.0"),
                result.getContent().stream().map(SkillVersion::getVersion).toList());
    }

    @Test
    void testResolveVersion_ShouldReturnLatestWhenHashDoesNotMatch() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "user-100", SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);

        SkillVersion version100 = new SkillVersion(1L, "1.0.0", "user-100");
        setId(version100, 9L);
        version100.setStatus(SkillVersionStatus.PUBLISHED);
        SkillVersion version110 = new SkillVersion(1L, "1.1.0", "user-100");
        setId(version110, 10L);
        version110.setStatus(SkillVersionStatus.PUBLISHED);

        SkillFile version100File = new SkillFile(9L, "SKILL.md", 10L, "text/markdown", "hash100", "key100");
        SkillFile version110File = new SkillFile(10L, "SKILL.md", 10L, "text/markdown", "hash110", "key110");

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, "user-100", userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findBySkillIdAndStatus(1L, SkillVersionStatus.PUBLISHED))
                .thenReturn(List.of(version100, version110));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version110));
        when(skillFileRepository.findByVersionId(9L)).thenReturn(List.of(version100File));
        when(skillFileRepository.findByVersionId(10L)).thenReturn(List.of(version110File));

        SkillQueryService.ResolvedVersionDTO result = service.resolveVersion(
                namespaceSlug,
                skillSlug,
                null,
                null,
                "sha256:does-not-match",
                "user-100",
                userNsRoles
        );

        assertEquals("1.1.0", result.version());
        assertEquals(Boolean.FALSE, result.matched());
        assertTrue(result.downloadUrl().contains("/versions/1.1.0/download"));
    }

    @Test
    void testResolveVersion_ShouldEncodeDownloadUrlPathSegments() throws Exception {
        String namespaceSlug = "global";
        String skillSlug = "smoke-skill-two";
        Map<Long, NamespaceRole> userNsRoles = Map.of();

        Namespace namespace = new Namespace(namespaceSlug, "Global", "user-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "user-100", SkillVisibility.PUBLIC);
        setId(skill, 3L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(11L);

        SkillVersion version = new SkillVersion(3L, "1.0.0 beta", "user-100");
        setId(version, 11L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        SkillFile file = new SkillFile(11L, "SKILL.md", 10L, "text/markdown", "hash", "key");

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, null, userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findById(11L)).thenReturn(Optional.of(version));
        when(skillVersionRepository.findBySkillIdAndStatus(3L, SkillVersionStatus.PUBLISHED)).thenReturn(List.of(version));
        when(skillFileRepository.findByVersionId(11L)).thenReturn(List.of(file));

        SkillQueryService.ResolvedVersionDTO result = service.resolveVersion(
                namespaceSlug,
                skillSlug,
                null,
                null,
                null,
                null,
                userNsRoles
        );

        assertEquals("/api/v1/skills/global/smoke-skill-two/versions/1.0.0%20beta/download", result.downloadUrl());
    }

    @Test
    void testGetSkillDetail_ShouldFlagLifecyclePermissionForOwner() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String userId = "owner-1";
        Map<Long, NamespaceRole> userNsRoles = Map.of();

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", userId);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, userId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, userId, userNsRoles)).thenReturn(true);

        SkillQueryService.SkillDetailDTO result = service.getSkillDetail(namespaceSlug, skillSlug, userId, userNsRoles);

        assertTrue(result.canManageLifecycle());
    }

    @Test
    void testGetSkillDetail_ShouldNotFlagLifecyclePermissionForRegularViewer() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String userId = "viewer-1";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "owner-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "owner-1", SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, userId, userNsRoles)).thenReturn(true);

        SkillQueryService.SkillDetailDTO result = service.getSkillDetail(namespaceSlug, skillSlug, userId, userNsRoles);

        assertFalse(result.canManageLifecycle());
    }

    @Test
    void testGetSkillDetail_ShouldPreferPendingVersionForOwnerPreview() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String ownerId = "owner-1";
        Map<Long, NamespaceRole> userNsRoles = Map.of();

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", ownerId);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, ownerId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(11L);

        SkillVersion published = new SkillVersion(1L, "1.0.0", ownerId);
        setId(published, 11L);
        published.setStatus(SkillVersionStatus.PUBLISHED);
        published.setPublishedAt(java.time.LocalDateTime.of(2026, 3, 1, 10, 0));

        SkillVersion pending = new SkillVersion(1L, "1.1.0", ownerId);
        setId(pending, 12L);
        pending.setStatus(SkillVersionStatus.PENDING_REVIEW);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, ownerId, userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findBySkillIdAndStatus(1L, SkillVersionStatus.PENDING_REVIEW))
                .thenReturn(List.of(pending));

        SkillQueryService.SkillDetailDTO result = service.getSkillDetail(namespaceSlug, skillSlug, ownerId, userNsRoles);

        assertEquals("1.1.0", result.latestVersion());
        assertEquals("PENDING_REVIEW", result.viewingVersionStatus());
        assertFalse(result.canInteract());
    }

    @Test
    void testGetSkillDetail_ShouldKeepPublishedVersionWhenSkillAlreadyPublic() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String ownerId = "owner-1";
        Map<Long, NamespaceRole> userNsRoles = Map.of();

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", ownerId);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, ownerId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(11L);

        SkillVersion published = new SkillVersion(1L, "1.0.0", ownerId);
        setId(published, 11L);
        published.setStatus(SkillVersionStatus.PUBLISHED);

        SkillVersion pending = new SkillVersion(1L, "1.1.0", ownerId);
        setId(pending, 12L);
        pending.setStatus(SkillVersionStatus.PENDING_REVIEW);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, ownerId, userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findById(11L)).thenReturn(Optional.of(published));

        SkillQueryService.SkillDetailDTO result = service.getSkillDetail(namespaceSlug, skillSlug, ownerId, userNsRoles);

        assertEquals("1.0.0", result.latestVersion());
        assertEquals("PUBLISHED", result.viewingVersionStatus());
        assertTrue(result.canInteract());
    }

    @Test
    void testGetVersionDetail_ShouldAllowPendingVersionForOwnerPreview() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String version = "1.1.0";
        String ownerId = "owner-1";
        Map<Long, NamespaceRole> userNsRoles = Map.of();

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", ownerId);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, ownerId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);

        SkillVersion pending = new SkillVersion(1L, version, ownerId);
        setId(pending, 11L);
        pending.setStatus(SkillVersionStatus.PENDING_REVIEW);
        pending.setParsedMetadataJson("{\"name\":\"test-skill\"}");
        pending.setManifestJson("[{\"path\":\"SKILL.md\"}]");

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, ownerId, userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findBySkillIdAndVersion(1L, version)).thenReturn(Optional.of(pending));

        SkillQueryService.SkillVersionDetailDTO result = service.getVersionDetail(
                namespaceSlug,
                skillSlug,
                version,
                ownerId,
                userNsRoles
        );

        assertEquals("PENDING_REVIEW", result.status());
        assertEquals("{\"name\":\"test-skill\"}", result.parsedMetadataJson());
    }

    @Test
    void testListFiles_ShouldAllowPendingVersionForOwnerPreview() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String version = "1.1.0";
        String ownerId = "owner-1";
        Map<Long, NamespaceRole> userNsRoles = Map.of();

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", ownerId);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, ownerId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        SkillVersion pending = new SkillVersion(1L, version, ownerId);
        setId(pending, 11L);
        pending.setStatus(SkillVersionStatus.PENDING_REVIEW);
        SkillFile file = new SkillFile(11L, "README.md", 12L, "text/markdown", "hash", "storage-key");

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, ownerId, userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findBySkillIdAndVersion(1L, version)).thenReturn(Optional.of(pending));
        when(skillFileRepository.findByVersionId(11L)).thenReturn(List.of(file));

        List<SkillFile> result = service.listFiles(namespaceSlug, skillSlug, version, ownerId, userNsRoles);

        assertEquals(1, result.size());
        assertEquals("README.md", result.get(0).getFilePath());
    }

    @Test
    void testGetVersionDetail_ShouldRejectPendingVersionForNonOwner() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String version = "1.1.0";
        String viewerId = "viewer-1";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "owner-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "owner-1", SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);

        SkillVersion pending = new SkillVersion(1L, version, "owner-1");
        setId(pending, 11L);
        pending.setStatus(SkillVersionStatus.PENDING_REVIEW);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, viewerId, userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findBySkillIdAndVersion(1L, version)).thenReturn(Optional.of(pending));

        assertThrows(DomainBadRequestException.class, () ->
                service.getVersionDetail(namespaceSlug, skillSlug, version, viewerId, userNsRoles));
    }

    @Test
    void testListVersions_ShouldIncludeDraftAndRejectedForLifecycleManagers() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String userId = "owner-1";
        Map<Long, NamespaceRole> userNsRoles = Map.of();

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", userId);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, userId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);

        SkillVersion published = new SkillVersion(1L, "1.0.0", userId);
        setId(published, 11L);
        published.setStatus(SkillVersionStatus.PUBLISHED);
        SkillVersion draft = new SkillVersion(1L, "1.1.0", userId);
        setId(draft, 12L);
        draft.setStatus(SkillVersionStatus.DRAFT);
        SkillVersion rejected = new SkillVersion(1L, "1.2.0", userId);
        setId(rejected, 13L);
        rejected.setStatus(SkillVersionStatus.REJECTED);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, userId, userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findBySkillId(1L)).thenReturn(List.of(rejected, draft, published));

        Page<SkillVersion> result = service.listVersions(
                namespaceSlug,
                skillSlug,
                userId,
                userNsRoles,
                PageRequest.of(0, 10)
        );

        assertEquals(List.of("1.0.0", "1.2.0", "1.1.0"),
                result.getContent().stream().map(SkillVersion::getVersion).toList());
    }

    @Test
    void testListVersions_ShouldOnlyReturnPublishedForRegularViewers() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String userId = "viewer-1";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "owner-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "owner-1", SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);

        SkillVersion published = new SkillVersion(1L, "1.0.0", "owner-1");
        setId(published, 11L);
        published.setStatus(SkillVersionStatus.PUBLISHED);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(Optional.of(skill));
        when(visibilityChecker.canAccess(skill, userId, userNsRoles)).thenReturn(true);
        when(skillVersionRepository.findBySkillIdAndStatus(1L, SkillVersionStatus.PUBLISHED)).thenReturn(List.of(published));

        Page<SkillVersion> result = service.listVersions(
                namespaceSlug,
                skillSlug,
                userId,
                userNsRoles,
                PageRequest.of(0, 10)
        );

        assertEquals(List.of("1.0.0"),
                result.getContent().stream().map(SkillVersion::getVersion).toList());
    }

    private void setId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
