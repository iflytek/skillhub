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
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.skill.service.SkillSlugResolutionService;
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
}
