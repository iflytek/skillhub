package com.iflytek.skillhub.compat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
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
    void findByLegacySlug_prefersPublicGlobalPublishedCandidate() {
        Skill privateTeamSkill = skill(11L, 1L, "demo", SkillVisibility.PRIVATE, 110L);
        Skill publicTeamSkill = skill(12L, 1L, "demo", SkillVisibility.PUBLIC, 120L);
        Skill publicGlobalSkill = skill(13L, 2L, "demo", SkillVisibility.PUBLIC, 130L);
        Namespace teamNamespace = namespace(1L, "team-a", NamespaceType.TEAM);
        Namespace globalNamespace = namespace(2L, "global", NamespaceType.GLOBAL);

        when(skillRepository.findBySlug("demo"))
                .thenReturn(List.of(privateTeamSkill, publicTeamSkill, publicGlobalSkill));
        when(namespaceRepository.findByIdIn(List.of(1L, 2L))).thenReturn(List.of(teamNamespace, globalNamespace));

        CompatSkillLookupService.CompatSkillContext result = service.findByLegacySlug("demo");

        assertThat(result.skill().getId()).isEqualTo(13L);
        assertThat(result.namespace().getSlug()).isEqualTo("global");
    }

    @Test
    void findByLegacySlug_prefersPublishedCandidateOverGlobalDraft() {
        Skill publicGlobalDraft = skill(21L, 2L, "demo", SkillVisibility.PUBLIC, null);
        Skill publicTeamPublished = skill(22L, 1L, "demo", SkillVisibility.PUBLIC, 220L);
        Namespace teamNamespace = namespace(1L, "team-a", NamespaceType.TEAM);
        Namespace globalNamespace = namespace(2L, "global", NamespaceType.GLOBAL);

        when(skillRepository.findBySlug("demo")).thenReturn(List.of(publicGlobalDraft, publicTeamPublished));
        when(namespaceRepository.findByIdIn(List.of(2L, 1L))).thenReturn(List.of(globalNamespace, teamNamespace));

        CompatSkillLookupService.CompatSkillContext result = service.findByLegacySlug("demo");

        assertThat(result.skill().getId()).isEqualTo(22L);
        assertThat(result.namespace().getSlug()).isEqualTo("team-a");
    }

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

    private static Skill skill(Long id, Long namespaceId, String slug, SkillVisibility visibility, Long latestVersionId) {
        Skill skill = new Skill(namespaceId, slug, "owner-1", visibility);
        ReflectionTestUtils.setField(skill, "id", id);
        skill.setLatestVersionId(latestVersionId);
        return skill;
    }

    private static Namespace namespace(Long id, String slug, NamespaceType type) {
        Namespace namespace = new Namespace(slug, slug, "owner-1");
        ReflectionTestUtils.setField(namespace, "id", id);
        namespace.setType(type);
        return namespace;
    }
}
