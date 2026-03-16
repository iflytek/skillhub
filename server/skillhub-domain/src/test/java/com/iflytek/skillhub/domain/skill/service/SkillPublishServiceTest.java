package com.iflytek.skillhub.domain.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.PrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillPublishServiceTest {

    @Mock
    private NamespaceRepository namespaceRepository;
    @Mock
    private NamespaceMemberRepository namespaceMemberRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private SkillVersionRepository skillVersionRepository;
    @Mock
    private SkillFileRepository skillFileRepository;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private SkillPackageValidator skillPackageValidator;
    @Mock
    private SkillMetadataParser skillMetadataParser;
    @Mock
    private PrePublishValidator prePublishValidator;
    @Mock
    private ReviewTaskRepository reviewTaskRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SkillPublishService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new SkillPublishService(
                namespaceRepository,
                namespaceMemberRepository,
                skillRepository,
                skillVersionRepository,
                skillFileRepository,
                objectStorageService,
                skillPackageValidator,
                skillMetadataParser,
                prePublishValidator,
                objectMapper,
                reviewTaskRepository,
                eventPublisher
        );
    }

    @Test
    void testPublishFromEntries_Success() throws Exception {
        // Arrange
        String namespaceSlug = "test-ns";
        String publisherId = "user-100";
        String skillMdContent = "---\nname: test-skill\ndescription: Test\nversion: 1.0.0\n---\nBody";

        PackageEntry skillMd = new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown");
        PackageEntry file1 = new PackageEntry("file1.txt", "content".getBytes(), 7, "text/plain");
        List<PackageEntry> entries = List.of(skillMd, file1);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        NamespaceMember member = mock(NamespaceMember.class);
        SkillMetadata metadata = new SkillMetadata("test-skill", "Test", "1.0.0", "Body", Map.of());

        Skill skill = new Skill(1L, "test-skill", publisherId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(any(), eq(publisherId))).thenReturn(Optional.of(member));
        when(skillPackageValidator.validate(entries)).thenReturn(ValidationResult.pass());
        when(skillMetadataParser.parse(skillMdContent)).thenReturn(metadata);
        when(prePublishValidator.validate(any())).thenReturn(ValidationResult.pass());
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("test-skill"))).thenReturn(List.of(skill));
        when(skillRepository.findByNamespaceIdAndSlugAndOwnerId(any(), eq("test-skill"), eq(publisherId))).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findBySkillIdAndVersion(any(), eq("1.0.0"))).thenReturn(Optional.empty());
        when(skillVersionRepository.save(any(SkillVersion.class))).thenAnswer(invocation -> {
            SkillVersion saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                setId(saved, 10L);
            }
            return saved;
        });
        when(skillRepository.save(any())).thenReturn(skill);

        // Act
        SkillPublishService.PublishResult result = service.publishFromEntries(
                namespaceSlug,
                entries,
                publisherId,
                SkillVisibility.PUBLIC,
                Set.of()
        );

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.skillId());
        assertEquals("test-skill", result.slug());
        assertEquals("1.0.0", result.version().getVersion());
        assertEquals(SkillVersionStatus.PENDING_REVIEW, result.version().getStatus());
        verify(skillFileRepository).saveAll(anyList());
        verify(objectStorageService, atLeastOnce()).putObject(anyString(), any(), anyLong(), anyString());
        verify(reviewTaskRepository).save(any(ReviewTask.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void testPublishFromEntries_ShouldSlugifyNameBeforeLookupAndResponse() throws Exception {
        String namespaceSlug = "test-ns";
        String publisherId = "user-100";
        String skillMdContent = "---\nname: Smoke Skill Two\ndescription: Test\nversion: 0.2.0\n---\nBody";

        PackageEntry skillMd = new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown");
        List<PackageEntry> entries = List.of(skillMd);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        NamespaceMember member = mock(NamespaceMember.class);
        SkillMetadata metadata = new SkillMetadata("Smoke Skill Two", "Test", "0.2.0", "Body", Map.of());

        Skill skill = new Skill(1L, "smoke-skill-two", publisherId, SkillVisibility.PUBLIC);
        setId(skill, 2L);
        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(any(), eq(publisherId))).thenReturn(Optional.of(member));
        when(skillPackageValidator.validate(entries)).thenReturn(ValidationResult.pass());
        when(skillMetadataParser.parse(skillMdContent)).thenReturn(metadata);
        when(prePublishValidator.validate(any())).thenReturn(ValidationResult.pass());
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("smoke-skill-two"))).thenReturn(List.of(skill));
        when(skillRepository.findByNamespaceIdAndSlugAndOwnerId(any(), eq("smoke-skill-two"), eq(publisherId))).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findBySkillIdAndVersion(any(), eq("0.2.0"))).thenReturn(Optional.empty());
        when(skillVersionRepository.save(any(SkillVersion.class))).thenAnswer(invocation -> {
            SkillVersion saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                setId(saved, 20L);
            }
            return saved;
        });
        when(skillRepository.save(any())).thenReturn(skill);

        SkillPublishService.PublishResult result = service.publishFromEntries(
                namespaceSlug,
                entries,
                publisherId,
                SkillVisibility.PUBLIC,
                Set.of()
        );

        assertEquals("smoke-skill-two", result.slug());
        verify(skillRepository).findByNamespaceIdAndSlug(1L, "smoke-skill-two");
        verify(reviewTaskRepository).save(any(ReviewTask.class));
    }

    @Test
    void testPublishFromEntries_SuperAdminShouldAutoPublish() throws Exception {
        String namespaceSlug = "test-ns";
        String publisherId = "user-100";
        String skillMdContent = "---\nname: Auto Skill\ndescription: Test\nversion: 1.0.0\n---\nBody";

        PackageEntry skillMd = new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown");
        List<PackageEntry> entries = List.of(skillMd);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        SkillMetadata metadata = new SkillMetadata("Auto Skill", "Test", "1.0.0", "Body", Map.of());

        Skill skill = new Skill(1L, "auto-skill", publisherId, SkillVisibility.PUBLIC);
        setId(skill, 1L);
        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillPackageValidator.validate(entries)).thenReturn(ValidationResult.pass());
        when(skillMetadataParser.parse(skillMdContent)).thenReturn(metadata);
        when(prePublishValidator.validate(any())).thenReturn(ValidationResult.pass());
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("auto-skill"))).thenReturn(List.of(skill));
        when(skillRepository.findByNamespaceIdAndSlugAndOwnerId(any(), eq("auto-skill"), eq(publisherId))).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findBySkillIdAndVersion(any(), eq("1.0.0"))).thenReturn(Optional.empty());
        when(skillVersionRepository.save(any(SkillVersion.class))).thenAnswer(invocation -> {
            SkillVersion saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                setId(saved, 10L);
            }
            return saved;
        });
        when(skillRepository.save(any())).thenReturn(skill);

        SkillPublishService.PublishResult result = service.publishFromEntries(
                namespaceSlug,
                entries,
                publisherId,
                SkillVisibility.PUBLIC,
                Set.of("SUPER_ADMIN")
        );

        assertEquals(SkillVersionStatus.PUBLISHED, result.version().getStatus());
        assertNotNull(result.version().getPublishedAt());
        verify(reviewTaskRepository, never()).save(any(ReviewTask.class));
        verify(skillRepository).save(argThat(savedSkill ->
                savedSkill.getLatestVersionId() != null && savedSkill.getLatestVersionId().equals(10L)));
        verify(eventPublisher).publishEvent(any(SkillPublishedEvent.class));
    }

    @Test
    void testPublishFromEntries_ShouldRejectArchivedSkill() throws Exception {
        String namespaceSlug = "test-ns";
        String publisherId = "user-100";
        String skillMdContent = "---\nname: test-skill\ndescription: Test\nversion: 1.0.0\n---\nBody";

        PackageEntry skillMd = new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown");
        List<PackageEntry> entries = List.of(skillMd);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        NamespaceMember member = mock(NamespaceMember.class);
        SkillMetadata metadata = new SkillMetadata("test-skill", "Test", "1.0.0", "Body", Map.of());
        Skill archivedSkill = new Skill(1L, "test-skill", publisherId, SkillVisibility.PUBLIC);
        archivedSkill.setStatus(SkillStatus.ARCHIVED);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(any(), eq(publisherId))).thenReturn(Optional.of(member));
        when(skillPackageValidator.validate(entries)).thenReturn(ValidationResult.pass());
        when(skillMetadataParser.parse(skillMdContent)).thenReturn(metadata);
        when(prePublishValidator.validate(any())).thenReturn(ValidationResult.pass());
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("test-skill"))).thenReturn(List.of(archivedSkill));
        when(skillRepository.findByNamespaceIdAndSlugAndOwnerId(any(), eq("test-skill"), eq(publisherId))).thenReturn(Optional.of(archivedSkill));

        assertThrows(DomainBadRequestException.class, () -> service.publishFromEntries(
                namespaceSlug,
                entries,
                publisherId,
                SkillVisibility.PUBLIC,
                Set.of()
        ));
    }

    @Test
    void testPublishFromEntries_ShouldAutoGenerateVersionWhenMissing() throws Exception {
        String namespaceSlug = "test-ns";
        String publisherId = "user-100";
        String skillMdContent = "---\nname: test-skill\ndescription: Test\n---\nBody";

        PackageEntry skillMd = new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown");
        List<PackageEntry> entries = List.of(skillMd);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        NamespaceMember member = mock(NamespaceMember.class);
        SkillMetadata metadata = new SkillMetadata("test-skill", "Test", null, "Body", Map.of());

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(any(), eq(publisherId))).thenReturn(Optional.of(member));
        when(skillPackageValidator.validate(entries)).thenReturn(ValidationResult.pass());
        when(skillMetadataParser.parse(skillMdContent)).thenReturn(metadata);
        when(prePublishValidator.validate(any())).thenReturn(ValidationResult.pass());
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("test-skill"))).thenReturn(List.of(new Skill(1L, "test-skill", publisherId, SkillVisibility.PUBLIC)));
        when(skillRepository.findByNamespaceIdAndSlugAndOwnerId(any(), eq("test-skill"), eq(publisherId))).thenReturn(Optional.of(new Skill(1L, "test-skill", publisherId, SkillVisibility.PUBLIC)));
        when(skillVersionRepository.findBySkillIdAndVersion(any(), anyString())).thenReturn(Optional.empty());
        when(skillVersionRepository.save(any(SkillVersion.class))).thenAnswer(invocation -> {
            SkillVersion saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                setId(saved, 10L);
            }
            return saved;
        });

        SkillPublishService.PublishResult result = service.publishFromEntries(
                namespaceSlug, entries, publisherId, SkillVisibility.PUBLIC, Set.of());

        assertNotNull(result.version().getVersion());
        assertFalse(result.version().getVersion().isBlank());
    }

    @Test
    void testPublishFromEntries_NamespaceNotFound() {
        // Arrange
        String namespaceSlug = "nonexistent";
        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(DomainBadRequestException.class, () ->
                service.publishFromEntries(namespaceSlug, List.of(), "user-100", SkillVisibility.PUBLIC, Set.of())
        );
    }

    @Test
    void testPublishFromEntries_ShouldRejectFrozenNamespace() throws Exception {
        String namespaceSlug = "test-ns";
        String publisherId = "user-100";
        String skillMdContent = "---\nname: test-skill\ndescription: Test\nversion: 1.0.0\n---\nBody";

        PackageEntry skillMd = new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown");
        List<PackageEntry> entries = List.of(skillMd);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        namespace.setStatus(NamespaceStatus.FROZEN);
        setId(namespace, 1L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));

        assertThrows(DomainBadRequestException.class, () ->
                service.publishFromEntries(namespaceSlug, entries, publisherId, SkillVisibility.PUBLIC, Set.of()));
    }

    @Test
    void testPublishFromEntries_NotAMember() throws Exception {
        // Arrange
        String namespaceSlug = "test-ns";
        String publisherId = "user-100";
        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(any(), eq(publisherId))).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(DomainBadRequestException.class, () ->
                service.publishFromEntries(namespaceSlug, List.of(), publisherId, SkillVisibility.PUBLIC, Set.of())
        );
    }

    @Test
    void testPublishFromEntries_SuperAdminShouldBypassNamespaceMembership() throws Exception {
        String namespaceSlug = "test-ns";
        String publisherId = "user-100";
        String skillMdContent = "---\nname: Admin Skill\ndescription: Test\nversion: 1.0.0\n---\nBody";

        PackageEntry skillMd = new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown");
        List<PackageEntry> entries = List.of(skillMd);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        SkillMetadata metadata = new SkillMetadata("Admin Skill", "Test", "1.0.0", "Body", Map.of());
        Skill skill = new Skill(1L, "admin-skill", publisherId, SkillVisibility.PUBLIC);
        setId(skill, 1L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(skillPackageValidator.validate(entries)).thenReturn(ValidationResult.pass());
        when(skillMetadataParser.parse(skillMdContent)).thenReturn(metadata);
        when(prePublishValidator.validate(any())).thenReturn(ValidationResult.pass());
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("admin-skill"))).thenReturn(List.of(skill));
        when(skillRepository.findByNamespaceIdAndSlugAndOwnerId(any(), eq("admin-skill"), eq(publisherId))).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findBySkillIdAndVersion(any(), eq("1.0.0"))).thenReturn(Optional.empty());
        when(skillVersionRepository.save(any(SkillVersion.class))).thenAnswer(invocation -> {
            SkillVersion saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                setId(saved, 10L);
            }
            return saved;
        });
        when(skillRepository.save(any())).thenReturn(skill);

        SkillPublishService.PublishResult result = service.publishFromEntries(
                namespaceSlug,
                entries,
                publisherId,
                SkillVisibility.PUBLIC,
                Set.of("SUPER_ADMIN")
        );

        assertEquals(SkillVersionStatus.PUBLISHED, result.version().getStatus());
        verify(namespaceMemberRepository, never()).findByNamespaceIdAndUserId(any(), any());
    }

    @Test
    void testPublishFromEntries_AllowsDescriptionLongerThanPreviousDatabaseLimit() throws Exception {
        String namespaceSlug = "test-ns";
        String publisherId = "user-100";
        String longDescription = "x".repeat(513);
        String skillMdContent = "---\nname: Too Long Skill\ndescription: ignored\nversion: 1.0.0\n---\nBody";

        PackageEntry skillMd = new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown");
        List<PackageEntry> entries = List.of(skillMd);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        NamespaceMember member = mock(NamespaceMember.class);
        SkillMetadata metadata = new SkillMetadata("Too Long Skill", longDescription, "1.0.0", "Body", Map.of());

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(any(), eq(publisherId))).thenReturn(Optional.of(member));
        when(skillPackageValidator.validate(entries)).thenReturn(ValidationResult.pass());
        when(skillMetadataParser.parse(skillMdContent)).thenReturn(metadata);
        when(prePublishValidator.validate(any())).thenReturn(ValidationResult.pass());

        Skill skill = new Skill(namespace.getId(), "too-long-skill", publisherId, SkillVisibility.PUBLIC);
        setId(skill, 10L);
        when(skillRepository.findByNamespaceIdAndSlug(namespace.getId(), "too-long-skill")).thenReturn(List.of(skill));
        when(skillRepository.findByNamespaceIdAndSlugAndOwnerId(namespace.getId(), "too-long-skill", publisherId)).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findBySkillIdAndVersion(skill.getId(), "1.0.0")).thenReturn(Optional.empty());
        when(skillVersionRepository.save(any())).thenAnswer(invocation -> {
            SkillVersion version = invocation.getArgument(0);
            if (version.getId() == null) {
                setId(version, 20L);
            }
            return version;
        });

        SkillPublishService.PublishResult result = service.publishFromEntries(
                namespaceSlug,
                entries,
                publisherId,
                SkillVisibility.PUBLIC,
                Set.of()
        );

        assertEquals(longDescription, skill.getSummary());
        assertEquals(SkillVersionStatus.PENDING_REVIEW, result.version().getStatus());
        verify(prePublishValidator).validate(any());
        verify(skillRepository).save(skill);
    }

    @Test
    void testRereleasePublishedVersion_ShouldCloneFilesAndAutoPublish() throws Exception {
        String publisherId = "user-100";
        Skill skill = new Skill(1L, "demo-skill", publisherId, SkillVisibility.PUBLIC);
        setId(skill, 11L);
        skill.setDisplayName("Demo Skill");
        skill.setSummary("Original summary");
        Namespace namespace = new Namespace("global", "Global", "owner");
        setId(namespace, 1L);

        SkillVersion sourceVersion = new SkillVersion(skill.getId(), "1.2.3", publisherId);
        setId(sourceVersion, 21L);
        sourceVersion.setStatus(SkillVersionStatus.PUBLISHED);
        sourceVersion.setPublishedAt(LocalDateTime.of(2026, 3, 15, 10, 0));

        String sourceSkillMd = """
                ---
                name: Demo Skill
                description: Original summary
                version: 1.2.3
                ---
                Hello world
                """;
        byte[] readmeBytes = "# Demo".getBytes(StandardCharsets.UTF_8);

        SkillFile skillMdFile = new SkillFile(sourceVersion.getId(), "SKILL.md", (long) sourceSkillMd.getBytes(StandardCharsets.UTF_8).length, "text/markdown", "hash1", "skills/11/21/SKILL.md");
        SkillFile readmeFile = new SkillFile(sourceVersion.getId(), "README.md", (long) readmeBytes.length, "text/markdown", "hash2", "skills/11/21/README.md");

        SkillMetadata rereleaseMetadata = new SkillMetadata(
                "Demo Skill",
                "Original summary",
                "1.2.4",
                "Hello world",
                Map.of("name", "Demo Skill", "description", "Original summary", "version", "1.2.4"));

        when(skillRepository.findById(skill.getId())).thenReturn(Optional.of(skill));
        when(namespaceRepository.findById(skill.getNamespaceId())).thenReturn(Optional.of(namespace));
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(namespace));
        when(skillVersionRepository.findBySkillIdAndVersion(skill.getId(), "1.2.3")).thenReturn(Optional.of(sourceVersion));
        when(skillVersionRepository.findBySkillIdAndVersion(skill.getId(), "1.2.4")).thenReturn(Optional.empty());
        when(skillFileRepository.findByVersionId(sourceVersion.getId())).thenReturn(List.of(skillMdFile, readmeFile));
        when(objectStorageService.getObject(skillMdFile.getStorageKey())).thenReturn(new java.io.ByteArrayInputStream(sourceSkillMd.getBytes(StandardCharsets.UTF_8)));
        when(objectStorageService.getObject(readmeFile.getStorageKey())).thenReturn(new java.io.ByteArrayInputStream(readmeBytes));
        when(skillPackageValidator.validate(anyList())).thenReturn(ValidationResult.pass());
        when(skillMetadataParser.parse(anyString())).thenReturn(rereleaseMetadata);
        when(prePublishValidator.validate(any())).thenReturn(ValidationResult.pass());
        when(skillVersionRepository.save(any(SkillVersion.class))).thenAnswer(invocation -> {
            SkillVersion saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                setId(saved, 30L);
            }
            return saved;
        });
        when(skillRepository.save(any())).thenReturn(skill);

        SkillPublishService.PublishResult result = service.rereleasePublishedVersion(
                skill.getId(),
                "1.2.3",
                "1.2.4",
                publisherId,
                Map.of(skill.getNamespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.OWNER)
        );

        assertEquals("1.2.4", result.version().getVersion());
        assertEquals(SkillVersionStatus.PUBLISHED, result.version().getStatus());
        assertEquals(30L, skill.getLatestVersionId());
        verify(reviewTaskRepository, never()).save(any());
        verify(eventPublisher).publishEvent(any(SkillPublishedEvent.class));
        verify(skillPackageValidator).validate(argThat(entries ->
                entries.size() == 2
                        && entries.stream().anyMatch(entry ->
                        entry.path().equals("SKILL.md")
                                && new String(entry.content(), StandardCharsets.UTF_8).contains("version: 1.2.4"))));
        verify(prePublishValidator).validate(any());
    }

    @Test
    void testRereleasePublishedVersion_ShouldRejectDuplicateTargetVersion() throws Exception {
        String publisherId = "user-100";
        Skill skill = new Skill(1L, "demo-skill", publisherId, SkillVisibility.PUBLIC);
        setId(skill, 11L);
        SkillVersion sourceVersion = new SkillVersion(skill.getId(), "1.2.3", publisherId);
        setId(sourceVersion, 21L);
        sourceVersion.setStatus(SkillVersionStatus.PUBLISHED);
        SkillVersion existingTarget = new SkillVersion(skill.getId(), "1.2.4", publisherId);
        setId(existingTarget, 22L);

        when(skillRepository.findById(skill.getId())).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findBySkillIdAndVersion(skill.getId(), "1.2.3")).thenReturn(Optional.of(sourceVersion));
        when(skillVersionRepository.findBySkillIdAndVersion(skill.getId(), "1.2.4")).thenReturn(Optional.of(existingTarget));

        assertThrows(DomainBadRequestException.class, () -> service.rereleasePublishedVersion(
                skill.getId(),
                "1.2.3",
                "1.2.4",
                publisherId,
                Map.of(skill.getNamespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.OWNER)
        ));
    }

    @Test
    void testPublishFromEntries_ShouldRejectWhenOtherOwnerHasPublishedSkill() throws Exception {
        String namespaceSlug = "test-ns";
        String publisherId = "user-200";
        String skillMdContent = "---\nname: test-skill\ndescription: Test\nversion: 1.0.0\n---\nBody";

        PackageEntry skillMd = new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown");
        List<PackageEntry> entries = List.of(skillMd);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        NamespaceMember member = mock(NamespaceMember.class);
        SkillMetadata metadata = new SkillMetadata("test-skill", "Test", "1.0.0", "Body", Map.of());

        // Existing skill owned by another user with a published version
        Skill existingSkill = new Skill(1L, "test-skill", "user-100", SkillVisibility.PUBLIC);
        setId(existingSkill, 1L);
        SkillVersion publishedVersion = new SkillVersion(1L, "0.1.0", "user-100");
        publishedVersion.setStatus(SkillVersionStatus.PUBLISHED);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(any(), eq(publisherId))).thenReturn(Optional.of(member));
        when(skillPackageValidator.validate(entries)).thenReturn(ValidationResult.pass());
        when(skillMetadataParser.parse(skillMdContent)).thenReturn(metadata);
        when(prePublishValidator.validate(any())).thenReturn(ValidationResult.pass());
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("test-skill"))).thenReturn(List.of(existingSkill));
        when(skillVersionRepository.findBySkillIdAndStatus(1L, SkillVersionStatus.PUBLISHED)).thenReturn(List.of(publishedVersion));

        assertThrows(DomainBadRequestException.class, () -> service.publishFromEntries(
                namespaceSlug, entries, publisherId, SkillVisibility.PUBLIC, Set.of()
        ));
    }

    @Test
    void testPublishFromEntries_ShouldAllowWhenOtherOwnerHasNonPublishedSkill() throws Exception {
        String namespaceSlug = "test-ns";
        String publisherId = "user-200";
        String skillMdContent = "---\nname: test-skill\ndescription: Test\nversion: 1.0.0\n---\nBody";

        PackageEntry skillMd = new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown");
        List<PackageEntry> entries = List.of(skillMd);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        NamespaceMember member = mock(NamespaceMember.class);
        SkillMetadata metadata = new SkillMetadata("test-skill", "Test", "1.0.0", "Body", Map.of());

        // Existing skill owned by another user with NO published version
        Skill existingSkill = new Skill(1L, "test-skill", "user-100", SkillVisibility.PUBLIC);
        setId(existingSkill, 1L);

        Skill newSkill = new Skill(1L, "test-skill", publisherId, SkillVisibility.PUBLIC);
        setId(newSkill, 2L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(any(), eq(publisherId))).thenReturn(Optional.of(member));
        when(skillPackageValidator.validate(entries)).thenReturn(ValidationResult.pass());
        when(skillMetadataParser.parse(skillMdContent)).thenReturn(metadata);
        when(prePublishValidator.validate(any())).thenReturn(ValidationResult.pass());
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("test-skill"))).thenReturn(List.of(existingSkill));
        when(skillVersionRepository.findBySkillIdAndStatus(1L, SkillVersionStatus.PUBLISHED)).thenReturn(List.of());
        when(skillRepository.findByNamespaceIdAndSlugAndOwnerId(any(), eq("test-skill"), eq(publisherId))).thenReturn(Optional.empty());
        when(skillRepository.save(any(Skill.class))).thenReturn(newSkill);
        when(skillVersionRepository.findBySkillIdAndVersion(any(), eq("1.0.0"))).thenReturn(Optional.empty());
        when(skillVersionRepository.save(any(SkillVersion.class))).thenAnswer(invocation -> {
            SkillVersion saved = invocation.getArgument(0);
            if (saved.getId() == null) setId(saved, 10L);
            return saved;
        });

        SkillPublishService.PublishResult result = service.publishFromEntries(
                namespaceSlug, entries, publisherId, SkillVisibility.PUBLIC, Set.of()
        );

        assertNotNull(result);
        assertEquals("test-skill", result.slug());
    }

    @Test
    void testPublishFromEntries_ShouldAutoWithdrawPendingVersions() throws Exception {
        String namespaceSlug = "test-ns";
        String publisherId = "user-100";
        String skillMdContent = "---\nname: test-skill\ndescription: Test\nversion: 2.0.0\n---\nBody";

        PackageEntry skillMd = new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown");
        List<PackageEntry> entries = List.of(skillMd);

        Namespace namespace = new Namespace(namespaceSlug, "Test NS", "user-1");
        setId(namespace, 1L);
        NamespaceMember member = mock(NamespaceMember.class);
        SkillMetadata metadata = new SkillMetadata("test-skill", "Test", "2.0.0", "Body", Map.of());

        Skill skill = new Skill(1L, "test-skill", publisherId, SkillVisibility.PUBLIC);
        setId(skill, 1L);

        // Existing pending version
        SkillVersion pendingV1 = new SkillVersion(1L, "1.0.0", publisherId);
        pendingV1.setStatus(SkillVersionStatus.PENDING_REVIEW);
        setId(pendingV1, 5L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(any(), eq(publisherId))).thenReturn(Optional.of(member));
        when(skillPackageValidator.validate(entries)).thenReturn(ValidationResult.pass());
        when(skillMetadataParser.parse(skillMdContent)).thenReturn(metadata);
        when(prePublishValidator.validate(any())).thenReturn(ValidationResult.pass());
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("test-skill"))).thenReturn(List.of(skill));
        when(skillRepository.findByNamespaceIdAndSlugAndOwnerId(any(), eq("test-skill"), eq(publisherId))).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findBySkillIdAndStatus(1L, SkillVersionStatus.PENDING_REVIEW)).thenReturn(List.of(pendingV1));
        when(skillVersionRepository.findBySkillIdAndVersion(any(), eq("2.0.0"))).thenReturn(Optional.empty());
        when(skillVersionRepository.save(any(SkillVersion.class))).thenAnswer(invocation -> {
            SkillVersion saved = invocation.getArgument(0);
            if (saved.getId() == null) setId(saved, 10L);
            return saved;
        });
        when(skillRepository.save(any())).thenReturn(skill);

        service.publishFromEntries(namespaceSlug, entries, publisherId, SkillVisibility.PUBLIC, Set.of());

        // Verify pending version was withdrawn to DRAFT
        assertEquals(SkillVersionStatus.DRAFT, pendingV1.getStatus());
        verify(skillVersionRepository).save(pendingV1);
    }

    private void setId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
