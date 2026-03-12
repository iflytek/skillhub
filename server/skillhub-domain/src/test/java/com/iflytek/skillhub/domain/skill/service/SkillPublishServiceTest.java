package com.iflytek.skillhub.domain.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
                reviewTaskRepository
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
        SkillVersion version = new SkillVersion(1L, "1.0.0", publisherId);
        setId(version, 10L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(any(), eq(publisherId))).thenReturn(Optional.of(member));
        when(skillPackageValidator.validate(entries)).thenReturn(ValidationResult.pass());
        when(skillMetadataParser.parse(skillMdContent)).thenReturn(metadata);
        when(prePublishValidator.validate(any())).thenReturn(ValidationResult.pass());
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("test-skill"))).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findBySkillIdAndVersion(any(), eq("1.0.0"))).thenReturn(Optional.empty());
        when(skillVersionRepository.save(any())).thenReturn(version);
        when(skillRepository.save(any())).thenReturn(skill);

        // Act
        SkillPublishService.PublishResult result = service.publishFromEntries(
                namespaceSlug,
                entries,
                publisherId,
                SkillVisibility.PUBLIC
        );

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.skillId());
        assertEquals("test-skill", result.slug());
        assertEquals("1.0.0", result.version().getVersion());
        verify(skillFileRepository).saveAll(anyList());
        verify(objectStorageService, atLeastOnce()).putObject(anyString(), any(), anyLong(), anyString());
        verify(reviewTaskRepository).save(any(ReviewTask.class));
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
        SkillVersion version = new SkillVersion(2L, "0.2.0", publisherId);
        setId(version, 20L);

        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.of(namespace));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(any(), eq(publisherId))).thenReturn(Optional.of(member));
        when(skillPackageValidator.validate(entries)).thenReturn(ValidationResult.pass());
        when(skillMetadataParser.parse(skillMdContent)).thenReturn(metadata);
        when(prePublishValidator.validate(any())).thenReturn(ValidationResult.pass());
        when(skillRepository.findByNamespaceIdAndSlug(any(), eq("smoke-skill-two"))).thenReturn(Optional.of(skill));
        when(skillVersionRepository.findBySkillIdAndVersion(any(), eq("0.2.0"))).thenReturn(Optional.empty());
        when(skillVersionRepository.save(any())).thenReturn(version);
        when(skillRepository.save(any())).thenReturn(skill);

        SkillPublishService.PublishResult result = service.publishFromEntries(
                namespaceSlug,
                entries,
                publisherId,
                SkillVisibility.PUBLIC
        );

        assertEquals("smoke-skill-two", result.slug());
        verify(skillRepository).findByNamespaceIdAndSlug(1L, "smoke-skill-two");
        verify(reviewTaskRepository).save(any(ReviewTask.class));
    }

    @Test
    void testPublishFromEntries_ShouldRejectMissingVersionBeforePersistence() throws Exception {
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

        assertThrows(DomainBadRequestException.class, () ->
                service.publishFromEntries(namespaceSlug, entries, publisherId, SkillVisibility.PUBLIC));
        verify(skillVersionRepository, never()).save(any());
    }

    @Test
    void testPublishFromEntries_NamespaceNotFound() {
        // Arrange
        String namespaceSlug = "nonexistent";
        when(namespaceRepository.findBySlug(namespaceSlug)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(DomainBadRequestException.class, () ->
                service.publishFromEntries(namespaceSlug, List.of(), "user-100", SkillVisibility.PUBLIC)
        );
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
                service.publishFromEntries(namespaceSlug, List.of(), publisherId, SkillVisibility.PUBLIC)
        );
    }

    private void setId(Object entity, Long id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
