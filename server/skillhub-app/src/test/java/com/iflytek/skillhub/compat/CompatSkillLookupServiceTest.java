package com.iflytek.skillhub.compat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.skill.service.SkillSlugResolutionService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CompatSkillLookupServiceTest {

    private final SkillRepository skillRepository = mock(SkillRepository.class);
    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final SkillVersionRepository skillVersionRepository = mock(SkillVersionRepository.class);
    private final SkillSlugResolutionService skillSlugResolutionService = mock(SkillSlugResolutionService.class);
    private final VisibilityChecker visibilityChecker = mock(VisibilityChecker.class);

    private final CompatSkillLookupService service = new CompatSkillLookupService(
            skillRepository,
            namespaceRepository,
            skillVersionRepository,
            skillSlugResolutionService,
            visibilityChecker
    );

    @Test
    void resolveVisible_throwsNotFoundWhenCallerCannotAccessSkill() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        ReflectionTestUtils.setField(namespace, "id", 1L);
        Skill privateSkill = new Skill(1L, "priv", "owner-1", SkillVisibility.PRIVATE);
        ReflectionTestUtils.setField(privateSkill, "id", 7L);
        privateSkill.setLatestVersionId(70L);

        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(skillSlugResolutionService.resolve(1L, "priv", null, SkillSlugResolutionService.Preference.PUBLISHED))
                .thenReturn(privateSkill);
        when(visibilityChecker.canAccess(privateSkill, null, Map.of())).thenReturn(false);

        assertThatThrownBy(() -> service.resolveVisible("team-a", "priv", null, Map.of()))
                .isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    void resolveVisible_returnsSkillWhenCallerHasNamespaceAccess() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        ReflectionTestUtils.setField(namespace, "id", 1L);
        Skill privateSkill = new Skill(1L, "priv", "owner-1", SkillVisibility.PRIVATE);
        ReflectionTestUtils.setField(privateSkill, "id", 7L);
        privateSkill.setLatestVersionId(70L);

        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(skillSlugResolutionService.resolve(1L, "priv", "admin-1", SkillSlugResolutionService.Preference.PUBLISHED))
                .thenReturn(privateSkill);
        when(visibilityChecker.canAccess(privateSkill, "admin-1", Map.of(1L, NamespaceRole.ADMIN))).thenReturn(true);

        CompatSkillLookupService.CompatSkillContext result = service.resolveVisible(
                "team-a",
                "priv",
                "admin-1",
                Map.of(1L, NamespaceRole.ADMIN)
        );

        assertThat(result.skill().getId()).isEqualTo(7L);
    }

    @Test
    void canAccess_withNullSkill_returnsFalse() {
        assertThat(service.canAccess(null, "user-1", Map.of())).isFalse();
    }

    @Test
    void canAccess_withNullUserNsRoles_usesEmptyMap() {
        Skill skill = new Skill(1L, "demo", "owner-1", SkillVisibility.PUBLIC);
        when(visibilityChecker.canAccess(skill, "user-1", Map.of())).thenReturn(true);
        assertThat(service.canAccess(skill, "user-1", null)).isTrue();
    }

    @Test
    void resolveVisible_withThreeArgs_delegatesToFourArgs() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        ReflectionTestUtils.setField(namespace, "id", 1L);
        Skill skill = new Skill(1L, "demo", "owner-1", SkillVisibility.PUBLIC);
        ReflectionTestUtils.setField(skill, "id", 7L);

        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(skillSlugResolutionService.resolve(1L, "demo", "user-1", SkillSlugResolutionService.Preference.PUBLISHED))
                .thenReturn(skill);
        when(visibilityChecker.canAccess(skill, "user-1", Map.of())).thenReturn(true);

        CompatSkillLookupService.CompatSkillContext result = service.resolveVisible("team-a", "demo", "user-1");
        assertThat(result.skill().getId()).isEqualTo(7L);
    }

    @Test
    void findVersion_withNullSkillId_returnsEmpty() {
        assertThat(service.findVersion(null, "1.0.0")).isEmpty();
    }

    @Test
    void findVersion_withNullVersion_returnsEmpty() {
        assertThat(service.findVersion(1L, null)).isEmpty();
    }

    @Test
    void findVersion_withBlankVersion_returnsEmpty() {
        assertThat(service.findVersion(1L, "  ")).isEmpty();
    }

    @Test
    void findLatestVersion_withNullSkill_returnsEmpty() {
        assertThat(service.findLatestVersion(null)).isEmpty();
    }

    @Test
    void findLatestVersion_withNullLatestVersionId_returnsEmpty() {
        Skill skill = new Skill(1L, "demo", "owner-1", SkillVisibility.PUBLIC);
        assertThat(service.findLatestVersion(skill)).isEmpty();
    }

    @Test
    void resolveVisibleSkill_withBadRequestException_throwsNotFound() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        ReflectionTestUtils.setField(namespace, "id", 1L);

        when(namespaceRepository.findBySlug("team-a")).thenReturn(Optional.of(namespace));
        when(skillSlugResolutionService.resolve(1L, "missing", "user-1", SkillSlugResolutionService.Preference.PUBLISHED))
                .thenThrow(new com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException("error.skill.notFound", "missing"));

        assertThatThrownBy(() -> service.resolveVisible("team-a", "missing", "user-1", Map.of()))
                .isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    void findByLegacySlug_returnsContext() {
        Skill skill = new Skill(1L, "legacy", "owner-1", SkillVisibility.PUBLIC);
        ReflectionTestUtils.setField(skill, "id", 7L);
        skill.setLatestVersionId(70L);
        Namespace namespace = new Namespace("global", "Global", "owner-1");
        SkillVersion version = new SkillVersion(70L, "1.0.0", "owner-1");

        when(skillRepository.findBySlug("legacy")).thenReturn(List.of(skill));
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(namespace));
        when(skillVersionRepository.findById(70L)).thenReturn(Optional.of(version));

        CompatSkillLookupService.CompatSkillContext result = service.findByLegacySlug("legacy");

        assertThat(result.skill().getId()).isEqualTo(7L);
        assertThat(result.namespace().getSlug()).isEqualTo("global");
        assertThat(result.latestVersion()).isPresent();
    }

    @Test
    void findByLegacySlug_withMissingSkill_throwsNotFound() {
        when(skillRepository.findBySlug("missing")).thenReturn(List.of());

        assertThatThrownBy(() -> service.findByLegacySlug("missing"))
                .isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    void resolveVisible_withMissingNamespace_throwsNotFound() {
        when(namespaceRepository.findBySlug("missing-ns")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveVisible("missing-ns", "skill", "user-1", Map.of()))
                .isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    void findVersion_withValidInputs_returnsVersion() {
        SkillVersion version = new SkillVersion(1L, "1.0.0", "owner-1");
        when(skillVersionRepository.findBySkillIdAndVersion(7L, "1.0.0")).thenReturn(Optional.of(version));

        Optional<SkillVersion> result = service.findVersion(7L, "1.0.0");

        assertThat(result).isPresent();
        assertThat(result.get().getVersion()).isEqualTo("1.0.0");
    }
}
