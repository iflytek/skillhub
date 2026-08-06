package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    private VisibilityChecker visibilityChecker;
    @Mock
    private PromotionRequestRepository promotionRequestRepository;
    @Mock
    private ReviewTaskRepository reviewTaskRepository;
    @Mock
    private UserAccountRepository userAccountRepository;

    private SkillQueryService service;
    private SkillSlugResolutionService skillSlugResolutionService;
    private SkillLifecycleProjectionService skillLifecycleProjectionService;

    @BeforeEach
    void setUp() {
        visibilityChecker = new VisibilityChecker();
        skillSlugResolutionService = new SkillSlugResolutionService(skillRepository);
        skillLifecycleProjectionService = new SkillLifecycleProjectionService(skillVersionRepository);
        service = new SkillQueryService(
                namespaceRepository,
                skillRepository,
                skillVersionRepository,
                skillFileRepository,
                skillTagRepository,
                objectStorageService,
                visibilityChecker,
                promotionRequestRepository,
                reviewTaskRepository,
                skillSlugResolutionService,
                skillLifecycleProjectionService,
                userAccountRepository
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
        version.setStatus(SkillVersionStatus.PUBLISHED);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(new UserAccount(userId, "Alice", "alice@example.com", null)));

        // Act
        SkillQueryService.SkillDetailDTO result = service.getSkillDetail(namespaceSlug, skillSlug, userId, userNsRoles);

        // Assert
        assertNotNull(result);
        assertEquals(skillSlug, result.slug());
        assertEquals("Test Skill", result.displayName());
        assertEquals("Alice", result.ownerDisplayName());
        assertNotNull(result.headlineVersion());
        assertEquals("1.0.0", result.headlineVersion().version());
        assertFalse(result.canReport());
    }

    @Test
    void testGetSkillDetail_PrefersCurrentUsersOwnSkillOverOtherPublishedSkill() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String userId = "user-100";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);

        Skill publishedSkill = new Skill(1L, skillSlug, "user-200", SkillVisibility.PUBLIC);
        setId(publishedSkill, 1L);
        publishedSkill.setDisplayName("Published Skill");
        publishedSkill.setLatestVersionId(11L);

        Skill ownSkill = new Skill(1L, skillSlug, userId, SkillVisibility.PUBLIC);
        setId(ownSkill, 2L);
        ownSkill.setDisplayName("Own Skill");
        ownSkill.setLatestVersionId(22L);

        SkillVersion ownVersion = new SkillVersion(2L, "2.0.0", userId);
        setId(ownVersion, 22L);
        ownVersion.setStatus(SkillVersionStatus.PUBLISHED);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(publishedSkill, ownSkill));
        when(skillVersionRepository.findById(22L)).thenReturn(Optional.of(ownVersion));

        SkillQueryService.SkillDetailDTO result = service.getSkillDetail(namespaceSlug, skillSlug, userId, userNsRoles);

        assertEquals(2L, result.id());
        assertEquals("Own Skill", result.displayName());
        assertNotNull(result.headlineVersion());
        assertEquals("2.0.0", result.headlineVersion().version());
        assertFalse(result.canReport());
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
        skill.setLatestVersionId(11L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));

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
        skill.setLatestVersionId(11L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));

        assertThrows(DomainForbiddenException.class, () ->
                service.getSkillDetail(namespaceSlug, skillSlug, null, Map.of()));
    }

    @Test
    void testGetSkillDetail_ShouldAllowSuperAdminToReadArchivedNamespaceOnlySkillWithoutMembership() throws Exception {
        String namespaceSlug = "archived-team";
        String skillSlug = "namespace-only-skill";

        Namespace namespace = new Namespace(namespaceSlug, "Archived Team", "owner-1");
        namespace.setStatus(NamespaceStatus.ARCHIVED);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "owner-1", SkillVisibility.NAMESPACE_ONLY);
        setId(skill, 1L);
        skill.setLatestVersionId(11L);

        SkillVersion version = new SkillVersion(1L, "1.0.0", "owner-1");
        setId(version, 11L);
        version.setStatus(SkillVersionStatus.PUBLISHED);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findById(11L)).thenReturn(Optional.of(version));
        when(userAccountRepository.findById("owner-1")).thenReturn(Optional.empty());

        SkillQueryService.SkillDetailDTO result = service.getSkillDetail(
                namespaceSlug,
                skillSlug,
                "super-1",
                Map.of(),
                Set.of("SUPER_ADMIN")
        );

        assertEquals(skillSlug, result.slug());
        assertFalse(result.canManageLifecycle());
    }

    @Test
    void testGetSkillDetail_ShouldDenySuperAdminArchivedPublicSkillWithoutMembership() throws Exception {
        String namespaceSlug = "active-team";
        String skillSlug = "archived-public-skill";

        Namespace namespace = new Namespace(namespaceSlug, "Active Team", "owner-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "owner-1", SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ARCHIVED);
        skill.setLatestVersionId(11L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));

        assertThrows(DomainForbiddenException.class, () -> service.getSkillDetail(
                namespaceSlug,
                skillSlug,
                "super-1",
                Map.of(),
                Set.of("SUPER_ADMIN")
        ));
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
        Skill skill2 = new Skill(1L, "skill2", "user-200", SkillVisibility.PRIVATE);
        setId(skill2, 2L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndStatus(1L, SkillStatus.ACTIVE)).thenReturn(List.of(skill1, skill2));

        // Act
        Page<Skill> result = service.listSkillsByNamespace(namespaceSlug, userId, userNsRoles, pageable);

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals("skill1", result.getContent().get(0).getSlug());
    }

    @Test
    void testGetSkillDetail_ShouldHideOtherUsersUnpublishedSkill() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String viewerId = "user-300";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.ADMIN);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill unpublishedSkill = new Skill(1L, skillSlug, "user-200", SkillVisibility.PUBLIC);
        setId(unpublishedSkill, 1L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(unpublishedSkill));

        assertThrows(DomainBadRequestException.class, () ->
                service.getSkillDetail(namespaceSlug, skillSlug, viewerId, userNsRoles));
    }

    @Test
    void testListSkillsByNamespace_ShouldHideOtherUsersUnpublishedSkills() throws Exception {
        String namespaceSlug = "test-ns";
        String userId = "user-100";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);
        Pageable pageable = PageRequest.of(0, 10);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill ownUnpublishedSkill = new Skill(1L, "own-skill", userId, SkillVisibility.PUBLIC);
        setId(ownUnpublishedSkill, 1L);
        Skill othersUnpublishedSkill = new Skill(1L, "other-skill", "user-200", SkillVisibility.PUBLIC);
        setId(othersUnpublishedSkill, 2L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndStatus(1L, SkillStatus.ACTIVE))
                .thenReturn(List.of(ownUnpublishedSkill, othersUnpublishedSkill));

        Page<Skill> result = service.listSkillsByNamespace(namespaceSlug, userId, userNsRoles, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("own-skill", result.getContent().get(0).getSlug());
    }

    @Test
    void testListSkillsByNamespace_ShouldHideHiddenSkillsFromRegularUsers() throws Exception {
        String namespaceSlug = "test-ns";
        String userId = "user-100";
        Map<Long, NamespaceRole> userNsRoles = Map.of();
        Pageable pageable = PageRequest.of(0, 10);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill visibleSkill = new Skill(1L, "visible-skill", "user-200", SkillVisibility.PUBLIC);
        setId(visibleSkill, 1L);
        visibleSkill.setLatestVersionId(11L);
        Skill hiddenSkill = new Skill(1L, "hidden-skill", "user-300", SkillVisibility.PUBLIC);
        setId(hiddenSkill, 2L);
        hiddenSkill.setLatestVersionId(12L);
        hiddenSkill.setHidden(true);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndStatus(1L, SkillStatus.ACTIVE))
                .thenReturn(List.of(visibleSkill, hiddenSkill));

        Page<Skill> result = service.listSkillsByNamespace(namespaceSlug, userId, userNsRoles, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("visible-skill", result.getContent().get(0).getSlug());
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
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findBySkillIdAndVersion(1L, version)).thenReturn(Optional.of(skillVersion));
        when(skillFileRepository.findByVersionId(1L)).thenReturn(List.of(file1));
        when(objectStorageService.exists("key1")).thenReturn(true);

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
        String callerId = "user-999";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "user-100", SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(1L);
        SkillVersion skillVersion = new SkillVersion(1L, version, "user-100");
        setId(skillVersion, 1L);
        skillVersion.setStatus(SkillVersionStatus.DRAFT);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findBySkillIdAndVersion(1L, version)).thenReturn(Optional.of(skillVersion));

        assertThrows(DomainBadRequestException.class, () ->
                service.listFiles(namespaceSlug, skillSlug, version, callerId, userNsRoles));
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
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findBySkillIdAndVersion(1L, version)).thenReturn(Optional.of(skillVersion));
        when(skillFileRepository.findByVersionId(1L)).thenReturn(List.of(file));
        when(objectStorageService.exists(file.getStorageKey())).thenReturn(true);
        when(objectStorageService.getObject(file.getStorageKey()))
                .thenThrow(new UncheckedIOException(new java.io.FileNotFoundException(file.getStorageKey())));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () ->
                service.getFileContent(namespaceSlug, skillSlug, version, filePath, "user-100", userNsRoles));

        assertEquals("error.skill.file.notFound", ex.messageCode());
        assertArrayEquals(new Object[]{filePath}, ex.messageArgs());
    }

    @Test
    void testListFiles_ShouldHideEntriesWhoseStorageObjectIsMissing() throws Exception {
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
        skillVersion.setStatus(SkillVersionStatus.PUBLISHED);
        SkillFile availableFile = new SkillFile(1L, "SKILL.md", 100L, "text/markdown", "hash1", "skills/1/1/SKILL.md");
        SkillFile missingFile = new SkillFile(1L, "_meta.json", 100L, "application/json", "hash2", "skills/1/1/_meta.json");

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findBySkillIdAndVersion(1L, version)).thenReturn(Optional.of(skillVersion));
        when(skillFileRepository.findByVersionId(1L)).thenReturn(List.of(availableFile, missingFile));
        when(objectStorageService.exists("skills/1/1/SKILL.md")).thenReturn(true);
        when(objectStorageService.exists("skills/1/1/_meta.json")).thenReturn(false);

        List<SkillFile> result = service.listFiles(namespaceSlug, skillSlug, version, "user-100", userNsRoles);

        assertEquals(1, result.size());
        assertEquals("SKILL.md", result.get(0).getFilePath());
    }

    @Test
    void testIsDownloadAvailable_ShouldReturnFalseWhenBundleIsMissing() throws Exception {
        SkillVersion version = new SkillVersion(1L, "1.0.0", "user-100");
        setId(version, 10L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setDownloadReady(false);

        assertFalse(service.isDownloadAvailable(version));
    }

    @Test
    void testIsDownloadAvailable_ShouldReturnTrueWhenPublishedVersionHasFiles() throws Exception {
        SkillVersion version = new SkillVersion(1L, "1.0.0", "user-100");
        setId(version, 10L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setDownloadReady(true);

        assertTrue(service.isDownloadAvailable(version));
    }

    @Test
    void testIsDownloadAvailable_ShouldReturnFalseWhenVersionIsYanked() throws Exception {
        SkillVersion version = new SkillVersion(1L, "1.0.0", "user-100");
        setId(version, 10L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setDownloadReady(true);
        version.setYankedAt(Instant.parse("2026-06-12T00:00:00Z"));

        assertFalse(service.isDownloadAvailable(version));
    }

    @Test
    void testIsDownloadAvailable_ShouldNotHitObjectStorageForListSignals() throws Exception {
        SkillVersion version = new SkillVersion(1L, "1.0.0", "user-100");
        setId(version, 10L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setDownloadReady(true);

        assertTrue(service.isDownloadAvailable(version));
        verifyNoInteractions(objectStorageService, skillFileRepository);
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
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
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
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findById(11L)).thenReturn(Optional.of(latestVersion));
        when(skillFileRepository.findByVersionId(11L)).thenReturn(List.of(file));
        when(objectStorageService.exists("storage-key")).thenReturn(true);

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
        published.setPublishedAt(java.time.Instant.parse("2026-03-01T10:00:00Z"));

        SkillVersion pending = new SkillVersion(1L, "1.1.0", ownerId);
        setId(pending, 11L);
        pending.setStatus(SkillVersionStatus.PENDING_REVIEW);

        SkillVersion rejected = new SkillVersion(1L, "1.2.0", ownerId);
        setId(rejected, 12L);
        rejected.setStatus(SkillVersionStatus.REJECTED);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findBySkillId(1L)).thenReturn(List.of(pending, published, rejected));

        Page<SkillVersion> result = service.listVersions(namespaceSlug, skillSlug, ownerId, userNsRoles, PageRequest.of(0, 20));

        assertEquals(List.of("1.0.0", "1.2.0", "1.1.0"),
                result.getContent().stream().map(SkillVersion::getVersion).toList());
    }

    @Test
    void testListVersions_ShouldAllowSuperAdminReadWithoutGrantingLifecycleManagement() throws Exception {
        String namespaceSlug = "archived-team";
        String skillSlug = "namespace-only-skill";

        Namespace namespace = new Namespace(namespaceSlug, "Archived Team", "owner-1");
        namespace.setStatus(NamespaceStatus.ARCHIVED);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "owner-1", SkillVisibility.NAMESPACE_ONLY);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(10L);

        SkillVersion published = new SkillVersion(1L, "1.0.0", "owner-1");
        setId(published, 10L);
        published.setStatus(SkillVersionStatus.PUBLISHED);
        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findBySkillIdAndStatus(1L, SkillVersionStatus.PUBLISHED))
                .thenReturn(List.of(published));

        Page<SkillVersion> result = service.listVersions(
                namespaceSlug,
                skillSlug,
                "super-1",
                Map.of(),
                PageRequest.of(0, 20),
                Set.of("SUPER_ADMIN")
        );

        assertEquals(List.of("1.0.0"),
                result.getContent().stream().map(SkillVersion::getVersion).toList());
        verify(skillVersionRepository, never()).findBySkillId(1L);
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
        version100.setDownloadReady(true);
        SkillVersion version110 = new SkillVersion(1L, "1.1.0", "user-100");
        setId(version110, 10L);
        version110.setStatus(SkillVersionStatus.PUBLISHED);
        version110.setDownloadReady(true);

        SkillFile version100File = new SkillFile(9L, "SKILL.md", 10L, "text/markdown", "hash100", "key100");
        SkillFile version110File = new SkillFile(10L, "SKILL.md", 10L, "text/markdown", "hash110", "key110");

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
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
        version.setDownloadReady(true);
        SkillFile file = new SkillFile(11L, "SKILL.md", 10L, "text/markdown", "hash", "key");

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
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
    void testResolveVersion_ShouldRejectDownloadUnavailableLatestVersion() throws Exception {
        String namespaceSlug = "global";
        String skillSlug = "not-ready";

        Namespace namespace = new Namespace(namespaceSlug, "Global", "owner-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "owner-1", SkillVisibility.PUBLIC);
        setId(skill, 3L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(11L);

        SkillVersion version = new SkillVersion(3L, "1.0.0", "owner-1");
        setId(version, 11L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setDownloadReady(false);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findBySkillIdAndStatus(3L, SkillVersionStatus.PUBLISHED)).thenReturn(List.of(version));
        when(skillVersionRepository.findById(11L)).thenReturn(Optional.of(version));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () ->
                service.resolveVersion(namespaceSlug, skillSlug, null, null, null, null, Map.of()));

        assertEquals("error.skill.version.notDownloadable", ex.messageCode());
        assertArrayEquals(new Object[]{"1.0.0"}, ex.messageArgs());
    }

    @Test
    void testResolveVersion_ShouldRejectSkillWithoutLatest() throws Exception {
        String namespaceSlug = "global";
        String skillSlug = "missing-latest";

        Namespace namespace = new Namespace(namespaceSlug, "Global", "owner-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "owner-1", SkillVisibility.PUBLIC);
        setId(skill, 3L);
        skill.setStatus(SkillStatus.ACTIVE);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () ->
                service.resolveVersion(namespaceSlug, skillSlug, null, null, null, null, Map.of()));

        assertEquals("error.skill.notFound", ex.messageCode());
        assertArrayEquals(new Object[]{skillSlug}, ex.messageArgs());
    }

    @Test
    void testResolveVersion_ShouldRejectYankedLatestVersion() throws Exception {
        String namespaceSlug = "global";
        String skillSlug = "yanked-latest";

        Namespace namespace = new Namespace(namespaceSlug, "Global", "owner-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "owner-1", SkillVisibility.PUBLIC);
        setId(skill, 3L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(11L);

        SkillVersion version = new SkillVersion(3L, "1.0.0", "owner-1");
        setId(version, 11L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setDownloadReady(true);
        version.setYankedAt(Instant.parse("2026-06-12T00:00:00Z"));

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findBySkillIdAndStatus(3L, SkillVersionStatus.PUBLISHED)).thenReturn(List.of(version));
        when(skillVersionRepository.findById(11L)).thenReturn(Optional.of(version));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () ->
                service.resolveVersion(namespaceSlug, skillSlug, null, null, null, null, Map.of()));

        assertEquals("error.skill.version.notDownloadable", ex.messageCode());
        assertArrayEquals(new Object[]{"1.0.0"}, ex.messageArgs());
    }

    @Test
    void testResolveVersion_ShouldRejectDownloadUnavailableExplicitVersion() throws Exception {
        String namespaceSlug = "global";
        String skillSlug = "explicit-not-ready";

        Namespace namespace = new Namespace(namespaceSlug, "Global", "owner-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "owner-1", SkillVisibility.PUBLIC);
        setId(skill, 3L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(11L);

        SkillVersion version = new SkillVersion(3L, "1.0.0", "owner-1");
        setId(version, 11L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setDownloadReady(false);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findBySkillIdAndVersion(3L, "1.0.0")).thenReturn(Optional.of(version));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () ->
                service.resolveVersion(namespaceSlug, skillSlug, "1.0.0", null, null, null, Map.of()));

        assertEquals("error.skill.version.notDownloadable", ex.messageCode());
        assertArrayEquals(new Object[]{"1.0.0"}, ex.messageArgs());
    }

    @Test
    void testResolveVersion_ShouldRejectDownloadUnavailableTaggedVersion() throws Exception {
        String namespaceSlug = "global";
        String skillSlug = "tag-not-ready";

        Namespace namespace = new Namespace(namespaceSlug, "Global", "owner-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "owner-1", SkillVisibility.PUBLIC);
        setId(skill, 3L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(11L);

        SkillVersion version = new SkillVersion(3L, "1.0.0", "owner-1");
        setId(version, 11L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setDownloadReady(false);
        SkillTag tag = new SkillTag(3L, "stable", 11L, "owner-1");

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillTagRepository.findBySkillIdAndTagName(3L, "stable")).thenReturn(Optional.of(tag));
        when(skillVersionRepository.findById(11L)).thenReturn(Optional.of(version));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () ->
                service.resolveVersion(namespaceSlug, skillSlug, null, "stable", null, null, Map.of()));

        assertEquals("error.skill.version.notDownloadable", ex.messageCode());
        assertArrayEquals(new Object[]{"1.0.0"}, ex.messageArgs());
    }

    @Test
    void testResolveVersion_ShouldRejectAnonymousHiddenPrivateArchivedAndUnpublishedSkills() throws Exception {
        Namespace activeNamespace = new Namespace("global", "Global", "owner-1");
        setId(activeNamespace, 1L);
        Namespace archivedNamespace = new Namespace("archived", "Archived", "owner-1");
        setId(archivedNamespace, 2L);
        archivedNamespace.setStatus(NamespaceStatus.ARCHIVED);

        Skill hiddenSkill = new Skill(1L, "hidden", "owner-1", SkillVisibility.PUBLIC);
        setId(hiddenSkill, 10L);
        hiddenSkill.setStatus(SkillStatus.ACTIVE);
        hiddenSkill.setLatestVersionId(101L);
        hiddenSkill.setHidden(true);

        Skill privateSkill = new Skill(1L, "private", "owner-1", SkillVisibility.PRIVATE);
        setId(privateSkill, 11L);
        privateSkill.setStatus(SkillStatus.ACTIVE);
        privateSkill.setLatestVersionId(102L);

        Skill archivedSkill = new Skill(2L, "archived", "owner-1", SkillVisibility.PUBLIC);
        setId(archivedSkill, 12L);
        archivedSkill.setStatus(SkillStatus.ACTIVE);
        archivedSkill.setLatestVersionId(103L);

        Skill unpublishedSkill = new Skill(1L, "unpublished", "owner-1", SkillVisibility.PUBLIC);
        setId(unpublishedSkill, 13L);
        unpublishedSkill.setStatus(SkillStatus.ACTIVE);

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(activeNamespace));
        when(namespaceRepository.findBySlug("archived")).thenReturn(Optional.of(archivedNamespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, "hidden")).thenReturn(List.of(hiddenSkill));
        when(skillRepository.findByNamespaceIdAndSlug(1L, "private")).thenReturn(List.of(privateSkill));
        when(skillRepository.findByNamespaceIdAndSlug(2L, "archived")).thenReturn(List.of(archivedSkill));
        when(skillRepository.findByNamespaceIdAndSlug(1L, "unpublished")).thenReturn(List.of(unpublishedSkill));

        assertThrows(DomainBadRequestException.class, () ->
                service.resolveVersion("global", "hidden", null, null, null, null, Map.of()));
        assertThrows(DomainForbiddenException.class, () ->
                service.resolveVersion("global", "private", null, null, null, null, Map.of()));
        assertThrows(DomainForbiddenException.class, () ->
                service.resolveVersion("archived", "archived", null, null, null, null, Map.of()));
        assertThrows(DomainBadRequestException.class, () ->
                service.resolveVersion("global", "unpublished", null, null, null, null, Map.of()));
    }

    @Test
    void testResolveVersion_ShouldRejectAnonymousPrivateAndNamespaceOnlyWhenRolesAreMissing() throws Exception {
        Namespace namespace = new Namespace("global", "Global", "owner-1");
        setId(namespace, 1L);

        Skill privateSkill = new Skill(1L, "private", "owner-1", SkillVisibility.PRIVATE);
        setId(privateSkill, 11L);
        privateSkill.setStatus(SkillStatus.ACTIVE);
        privateSkill.setLatestVersionId(101L);

        Skill namespaceOnlySkill = new Skill(1L, "team-only", "owner-1", SkillVisibility.NAMESPACE_ONLY);
        setId(namespaceOnlySkill, 12L);
        namespaceOnlySkill.setStatus(SkillStatus.ACTIVE);
        namespaceOnlySkill.setLatestVersionId(102L);

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, "private")).thenReturn(List.of(privateSkill));
        when(skillRepository.findByNamespaceIdAndSlug(1L, "team-only")).thenReturn(List.of(namespaceOnlySkill));

        assertThrows(DomainForbiddenException.class, () ->
                service.resolveVersion("global", "private", null, null, null, null, null));
        assertThrows(DomainForbiddenException.class, () ->
                service.resolveVersion("global", "team-only", null, null, null, null, null));
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
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));

        SkillQueryService.SkillDetailDTO result = service.getSkillDetail(namespaceSlug, skillSlug, userId, userNsRoles);

        assertTrue(result.canManageLifecycle());
    }

    @Test
    void testGetSkillDetail_ShouldAllowPromotionForTeamOwnerOnPublishedSkill() throws Exception {
        String namespaceSlug = "team-ns";
        String skillSlug = "team-skill";
        String userId = "owner-1";
        Map<Long, NamespaceRole> userNsRoles = Map.of();

        Namespace namespace = new Namespace(namespaceSlug, "Team NS", userId);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, userId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(11L);

        SkillVersion published = new SkillVersion(1L, "1.0.0", userId);
        setId(published, 11L);
        published.setStatus(SkillVersionStatus.PUBLISHED);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findById(11L)).thenReturn(Optional.of(published));
        when(promotionRequestRepository.findBySourceSkillIdAndStatus(1L, ReviewTaskStatus.PENDING)).thenReturn(Optional.empty());
        when(promotionRequestRepository.findBySourceSkillIdAndStatus(1L, ReviewTaskStatus.APPROVED)).thenReturn(Optional.empty());

        SkillQueryService.SkillDetailDTO result = service.getSkillDetail(namespaceSlug, skillSlug, userId, userNsRoles);

        assertNotNull(result.publishedVersion());
        assertEquals(11L, result.publishedVersion().id());
        assertTrue(result.canSubmitPromotion());
    }

    @Test
    void testGetSkillDetail_ShouldHidePromotionWhenPendingPromotionExists() throws Exception {
        String namespaceSlug = "team-ns";
        String skillSlug = "team-skill";
        String userId = "owner-1";
        Map<Long, NamespaceRole> userNsRoles = Map.of();

        Namespace namespace = new Namespace(namespaceSlug, "Team NS", userId);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, userId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(11L);

        SkillVersion published = new SkillVersion(1L, "1.0.0", userId);
        setId(published, 11L);
        published.setStatus(SkillVersionStatus.PUBLISHED);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findById(11L)).thenReturn(Optional.of(published));
        when(promotionRequestRepository.findBySourceSkillIdAndStatus(1L, ReviewTaskStatus.PENDING))
                .thenReturn(Optional.of(mock(com.iflytek.skillhub.domain.review.PromotionRequest.class)));

        SkillQueryService.SkillDetailDTO result = service.getSkillDetail(namespaceSlug, skillSlug, userId, userNsRoles);

        assertFalse(result.canSubmitPromotion());
    }

    @Test
    void testGetSkillDetail_ShouldHidePromotionWhenSkillAlreadyPromoted() throws Exception {
        String namespaceSlug = "team-ns";
        String skillSlug = "team-skill";
        String userId = "owner-1";
        Map<Long, NamespaceRole> userNsRoles = Map.of();

        Namespace namespace = new Namespace(namespaceSlug, "Team NS", userId);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, userId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(11L);

        SkillVersion published = new SkillVersion(1L, "1.0.0", userId);
        setId(published, 11L);
        published.setStatus(SkillVersionStatus.PUBLISHED);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findById(11L)).thenReturn(Optional.of(published));
        when(promotionRequestRepository.findBySourceSkillIdAndStatus(1L, ReviewTaskStatus.PENDING)).thenReturn(Optional.empty());
        when(promotionRequestRepository.findBySourceSkillIdAndStatus(1L, ReviewTaskStatus.APPROVED))
                .thenReturn(Optional.of(mock(com.iflytek.skillhub.domain.review.PromotionRequest.class)));

        SkillQueryService.SkillDetailDTO result = service.getSkillDetail(namespaceSlug, skillSlug, userId, userNsRoles);

        assertFalse(result.canSubmitPromotion());
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
        skill.setLatestVersionId(11L);

        SkillVersion published = new SkillVersion(1L, "1.0.0", "owner-1");
        setId(published, 11L);
        published.setStatus(SkillVersionStatus.PUBLISHED);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findById(11L)).thenReturn(Optional.of(published));

        SkillQueryService.SkillDetailDTO result = service.getSkillDetail(namespaceSlug, skillSlug, userId, userNsRoles);

        assertFalse(result.canManageLifecycle());
        assertFalse(result.canSubmitPromotion());
    }

    @Test
    void testGetSkillDetail_ShouldNotGrantLifecyclePermissionToSuperAdminInPortal() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String userId = "super-1";
        Map<Long, NamespaceRole> userNsRoles = Map.of(1L, NamespaceRole.MEMBER);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "owner-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "owner-1", SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(11L);

        SkillVersion published = new SkillVersion(1L, "1.0.0", "owner-1");
        setId(published, 11L);
        published.setStatus(SkillVersionStatus.PUBLISHED);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findById(11L)).thenReturn(Optional.of(published));

        SkillQueryService.SkillDetailDTO result = service.getSkillDetail(
                namespaceSlug, skillSlug, userId, userNsRoles, Set.of("SUPER_ADMIN"));

        assertFalse(result.canManageLifecycle());
        assertFalse(result.canSubmitPromotion());
        assertEquals("PUBLISHED", result.resolutionMode());
    }

    @Test
    void testGetSkillDetail_ShouldNotGrantPrivateVisibilityToSuperAdminInPortal() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String userId = "super-1";

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "owner-1");
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, "owner-1", SkillVisibility.PRIVATE);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(11L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));

        assertThrows(DomainForbiddenException.class, () ->
                service.getSkillDetail(namespaceSlug, skillSlug, userId, Map.of(), Set.of("SUPER_ADMIN")));
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
        skill.setLatestVersionId(12L);

        SkillVersion pending = new SkillVersion(1L, "1.1.0", ownerId);
        setId(pending, 12L);
        pending.setStatus(SkillVersionStatus.PENDING_REVIEW);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findById(12L)).thenReturn(Optional.of(pending));
        when(skillVersionRepository.findBySkillIdAndStatus(1L, SkillVersionStatus.PUBLISHED))
                .thenReturn(List.of());
        when(skillVersionRepository.findBySkillId(1L))
                .thenReturn(List.of(pending));

        SkillQueryService.SkillDetailDTO result = service.getSkillDetail(namespaceSlug, skillSlug, ownerId, userNsRoles);

        assertNotNull(result.headlineVersion());
        assertEquals("1.1.0", result.headlineVersion().version());
        assertEquals("PENDING_REVIEW", result.headlineVersion().status());
        assertEquals("OWNER_PREVIEW", result.resolutionMode());
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
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findById(11L)).thenReturn(Optional.of(published));

        SkillQueryService.SkillDetailDTO result = service.getSkillDetail(namespaceSlug, skillSlug, ownerId, userNsRoles);

        assertNotNull(result.headlineVersion());
        assertEquals("1.0.0", result.headlineVersion().version());
        assertEquals("PUBLISHED", result.headlineVersion().status());
        assertEquals("PUBLISHED", result.resolutionMode());
        assertTrue(result.canInteract());
    }

    @Test
    void testGetSkillDetail_ShouldIncludeRejectedOwnerPreviewComment() throws Exception {
        String namespaceSlug = "test-ns";
        String skillSlug = "test-skill";
        String ownerId = "owner-1";
        Map<Long, NamespaceRole> userNsRoles = Map.of();

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", ownerId);
        setId(namespace, 1L);
        Skill skill = new Skill(1L, skillSlug, ownerId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        skill.setStatus(SkillStatus.ACTIVE);
        skill.setLatestVersionId(12L);

        SkillVersion rejected = new SkillVersion(1L, "1.1.0", ownerId);
        setId(rejected, 12L);
        rejected.setStatus(SkillVersionStatus.REJECTED);

        ReviewTask reviewTask = new ReviewTask(12L, 1L, ownerId);
        reviewTask.setStatus(ReviewTaskStatus.REJECTED);
        reviewTask.setReviewComment("metadata missing");

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findById(12L)).thenReturn(Optional.of(rejected));
        when(skillVersionRepository.findBySkillIdAndStatus(1L, SkillVersionStatus.PUBLISHED)).thenReturn(List.of());
        when(skillVersionRepository.findBySkillId(1L)).thenReturn(List.of(rejected));
        when(reviewTaskRepository.findBySkillVersionIdAndStatus(12L, ReviewTaskStatus.REJECTED))
                .thenReturn(Optional.of(reviewTask));

        SkillQueryService.SkillDetailDTO result = service.getSkillDetail(namespaceSlug, skillSlug, ownerId, userNsRoles);

        assertNotNull(result.ownerPreviewVersion());
        assertEquals("REJECTED", result.ownerPreviewVersion().status());
        assertEquals("metadata missing", result.ownerPreviewReviewComment());
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
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
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
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
        when(skillVersionRepository.findBySkillIdAndVersion(1L, version)).thenReturn(Optional.of(pending));
        when(skillFileRepository.findByVersionId(11L)).thenReturn(List.of(file));
        when(objectStorageService.exists("storage-key")).thenReturn(true);

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
        skill.setLatestVersionId(10L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
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
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
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
        skill.setLatestVersionId(11L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillRepository.findByNamespaceIdAndSlug(1L, skillSlug)).thenReturn(List.of(skill));
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
