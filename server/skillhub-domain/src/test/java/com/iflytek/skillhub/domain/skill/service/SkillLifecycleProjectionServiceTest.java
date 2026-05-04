package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillLifecycleProjectionServiceTest {

    @Mock
    private SkillVersionRepository skillVersionRepository;

    private SkillLifecycleProjectionService service;

    @BeforeEach
    void setUp() {
        service = new SkillLifecycleProjectionService(skillVersionRepository);
    }

    @Test
    void projectForViewer_shouldReturnPublished_whenPublishedExists() {
        Skill skill = createSkill(1L, 10L);
        SkillVersion publishedVersion = createVersion(10L, 1L, "1.0.0", SkillVersionStatus.PUBLISHED);
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(publishedVersion));

        SkillLifecycleProjectionService.Projection projection =
                service.projectForViewer(skill, "user-1", Map.of(1L, NamespaceRole.MEMBER));

        assertNotNull(projection.headlineVersion());
        assertEquals("1.0.0", projection.headlineVersion().version());
        assertEquals("PUBLISHED", projection.headlineVersion().status());
        assertEquals(SkillLifecycleProjectionService.ResolutionMode.PUBLISHED, projection.resolutionMode());
    }

    @Test
    void projectForViewer_shouldReturnOwnerPreview_whenNoPublishedAndUserCanManage() {
        Skill skill = createSkill(1L, 10L);
        SkillVersion draftVersion = createVersion(10L, 1L, "1.0.0", SkillVersionStatus.DRAFT);
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(draftVersion));
        when(skillVersionRepository.findBySkillId(1L)).thenReturn(List.of(draftVersion));

        SkillLifecycleProjectionService.Projection projection =
                service.projectForViewer(skill, "owner-1", Map.of(1L, NamespaceRole.OWNER));

        assertNotNull(projection.headlineVersion());
        assertEquals("1.0.0", projection.headlineVersion().version());
        assertEquals(SkillLifecycleProjectionService.ResolutionMode.OWNER_PREVIEW, projection.resolutionMode());
    }

    @Test
    void projectForViewer_shouldReturnNone_whenNoPublishedAndUserCannotManage() {
        Skill skill = createSkill(1L, 10L);
        SkillVersion draftVersion = createVersion(10L, 1L, "1.0.0", SkillVersionStatus.DRAFT);
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(draftVersion));

        SkillLifecycleProjectionService.Projection projection =
                service.projectForViewer(skill, "user-1", Map.of(1L, NamespaceRole.MEMBER));

        assertNull(projection.headlineVersion());
        assertEquals(SkillLifecycleProjectionService.ResolutionMode.NONE, projection.resolutionMode());
    }

    @Test
    void projectForViewer_shouldReturnNone_whenLatestVersionIdIsNull() {
        Skill skill = createSkill(1L, null);
        when(skillVersionRepository.findBySkillIdAndStatus(1L, SkillVersionStatus.PUBLISHED))
                .thenReturn(List.of());

        SkillLifecycleProjectionService.Projection projection =
                service.projectForViewer(skill, "user-1", Map.of(1L, NamespaceRole.MEMBER));

        assertNull(projection.headlineVersion());
        assertEquals(SkillLifecycleProjectionService.ResolutionMode.NONE, projection.resolutionMode());
    }

    @Test
    void projectForOwnerSummary_shouldReturnPublished_whenPublishedExists() {
        Skill skill = createSkill(1L, 10L);
        SkillVersion publishedVersion = createVersion(10L, 1L, "1.0.0", SkillVersionStatus.PUBLISHED);
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(publishedVersion));

        SkillLifecycleProjectionService.Projection projection = service.projectForOwnerSummary(skill);

        assertNotNull(projection.headlineVersion());
        assertEquals("PUBLISHED", projection.headlineVersion().status());
        assertEquals(SkillLifecycleProjectionService.ResolutionMode.PUBLISHED, projection.resolutionMode());
    }

    @Test
    void projectForOwnerSummary_shouldReturnOwnerPreview_whenNoPublished() {
        Skill skill = createSkill(1L, 10L);
        SkillVersion draftVersion = createVersion(10L, 1L, "1.0.0", SkillVersionStatus.DRAFT);
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(draftVersion));
        when(skillVersionRepository.findBySkillId(1L)).thenReturn(List.of(draftVersion));

        SkillLifecycleProjectionService.Projection projection = service.projectForOwnerSummary(skill);

        assertNotNull(projection.headlineVersion());
        assertEquals("DRAFT", projection.headlineVersion().status());
        assertEquals(SkillLifecycleProjectionService.ResolutionMode.OWNER_PREVIEW, projection.resolutionMode());
    }

    @Test
    void projectForOwnerSummary_shouldReturnNone_whenNoVersions() {
        Skill skill = createSkill(1L, null);
        when(skillVersionRepository.findBySkillIdAndStatus(1L, SkillVersionStatus.PUBLISHED))
                .thenReturn(List.of());
        when(skillVersionRepository.findBySkillId(1L)).thenReturn(List.of());

        SkillLifecycleProjectionService.Projection projection = service.projectForOwnerSummary(skill);

        assertNull(projection.headlineVersion());
        assertEquals(SkillLifecycleProjectionService.ResolutionMode.NONE, projection.resolutionMode());
    }

    @Test
    void projectPublishedSummaries_shouldReturnEmptyMap_forEmptyList() {
        Map<Long, SkillLifecycleProjectionService.Projection> result =
                service.projectPublishedSummaries(List.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void projectPublishedSummaries_shouldUseLatestVersion_whenPublished() {
        Skill skill = createSkill(1L, 10L);
        SkillVersion publishedVersion = createVersion(10L, 1L, "1.0.0", SkillVersionStatus.PUBLISHED);
        when(skillVersionRepository.findByIdIn(List.of(10L))).thenReturn(List.of(publishedVersion));

        Map<Long, SkillLifecycleProjectionService.Projection> result =
                service.projectPublishedSummaries(List.of(skill));

        assertEquals(1, result.size());
        assertNotNull(result.get(1L).headlineVersion());
        assertEquals("PUBLISHED", result.get(1L).headlineVersion().status());
    }

    @Test
    void projectPublishedSummaries_shouldFallbackToPublishedVersion_whenLatestNotPublished() {
        Skill skill = createSkill(1L, 10L);
        SkillVersion draftVersion = createVersion(10L, 1L, "2.0.0", SkillVersionStatus.DRAFT);
        SkillVersion publishedVersion = createVersion(11L, 1L, "1.0.0", SkillVersionStatus.PUBLISHED);
        when(skillVersionRepository.findByIdIn(List.of(10L))).thenReturn(List.of(draftVersion));
        when(skillVersionRepository.findBySkillIdInAndStatus(List.of(1L), SkillVersionStatus.PUBLISHED))
                .thenReturn(List.of(publishedVersion));

        Map<Long, SkillLifecycleProjectionService.Projection> result =
                service.projectPublishedSummaries(List.of(skill));

        assertEquals(1, result.size());
        assertNotNull(result.get(1L).headlineVersion());
        assertEquals("1.0.0", result.get(1L).headlineVersion().version());
    }

    @Test
    void projectPublishedSummaries_shouldReturnNone_whenNoPublishedVersionExists() {
        Skill skill = createSkill(1L, 10L);
        SkillVersion draftVersion = createVersion(10L, 1L, "2.0.0", SkillVersionStatus.DRAFT);
        when(skillVersionRepository.findByIdIn(List.of(10L))).thenReturn(List.of(draftVersion));
        when(skillVersionRepository.findBySkillIdInAndStatus(List.of(1L), SkillVersionStatus.PUBLISHED))
                .thenReturn(List.of());

        Map<Long, SkillLifecycleProjectionService.Projection> result =
                service.projectPublishedSummaries(List.of(skill));

        assertEquals(1, result.size());
        assertNull(result.get(1L).headlineVersion());
        assertEquals(SkillLifecycleProjectionService.ResolutionMode.NONE, result.get(1L).resolutionMode());
    }

    @Test
    void projectPublishedSummaries_shouldHandleNullLatestVersionId() {
        Skill skill = createSkill(1L, null);
        when(skillVersionRepository.findBySkillIdInAndStatus(List.of(1L), SkillVersionStatus.PUBLISHED))
                .thenReturn(List.of());

        Map<Long, SkillLifecycleProjectionService.Projection> result =
                service.projectPublishedSummaries(List.of(skill));

        assertEquals(1, result.size());
        assertNull(result.get(1L).headlineVersion());
    }

    @Test
    void projectForViewer_shouldReturnNone_whenCurrentUserIdIsNull() {
        Skill skill = createSkill(1L, 10L);
        SkillVersion draftVersion = createVersion(10L, 1L, "1.0.0", SkillVersionStatus.DRAFT);
        when(skillVersionRepository.findById(10L)).thenReturn(Optional.of(draftVersion));

        SkillLifecycleProjectionService.Projection projection =
                service.projectForViewer(skill, null, Map.of(1L, NamespaceRole.OWNER));

        assertNull(projection.headlineVersion());
        assertEquals(SkillLifecycleProjectionService.ResolutionMode.NONE, projection.resolutionMode());
    }

    @Test
    void projectPublishedSummaries_shouldPickNewerVersion_whenMultiplePublishedExist() {
        Skill skill = createSkill(1L, 10L);
        SkillVersion oldPublished = createVersion(11L, 1L, "1.0.0", SkillVersionStatus.PUBLISHED);
        oldPublished.setPublishedAt(Instant.parse("2026-01-01T00:00:00Z"));
        SkillVersion newPublished = createVersion(12L, 1L, "2.0.0", SkillVersionStatus.PUBLISHED);
        newPublished.setPublishedAt(Instant.parse("2026-02-01T00:00:00Z"));

        when(skillVersionRepository.findByIdIn(List.of(10L))).thenReturn(List.of(
                createVersion(10L, 1L, "3.0.0", SkillVersionStatus.DRAFT)));
        when(skillVersionRepository.findBySkillIdInAndStatus(List.of(1L), SkillVersionStatus.PUBLISHED))
                .thenReturn(List.of(oldPublished, newPublished));

        Map<Long, SkillLifecycleProjectionService.Projection> result =
                service.projectPublishedSummaries(List.of(skill));

        assertEquals(1, result.size());
        assertNotNull(result.get(1L).headlineVersion());
        assertEquals("2.0.0", result.get(1L).headlineVersion().version());
    }

    private Skill createSkill(Long id, Long latestVersionId) {
        Skill skill = new Skill(1L, "test-skill", "owner-1", com.iflytek.skillhub.domain.skill.SkillVisibility.PUBLIC);
        skill.setLatestVersionId(latestVersionId);
        try {
            java.lang.reflect.Field idField = Skill.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(skill, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return skill;
    }

    private SkillVersion createVersion(Long id, Long skillId, String version, SkillVersionStatus status) {
        SkillVersion sv = new SkillVersion(skillId, version, "owner-1");
        try {
            java.lang.reflect.Field idField = SkillVersion.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(sv, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        sv.setStatus(status);
        sv.setPublishedAt(status == SkillVersionStatus.PUBLISHED ? Instant.now() : null);
        return sv;
    }
}
