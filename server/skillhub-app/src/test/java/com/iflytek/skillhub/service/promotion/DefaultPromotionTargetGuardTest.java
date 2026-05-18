package com.iflytek.skillhub.service.promotion;

import com.iflytek.skillhub.domain.promotion.PromotionException;
import com.iflytek.skillhub.domain.promotion.PromotionTargetType;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Unit coverage for promotion target eligibility checks that depend on app-layer repositories.
 */
class DefaultPromotionTargetGuardTest {

    private SkillRepository skillRepository;
    private SkillVersionRepository skillVersionRepository;
    private DefaultPromotionTargetGuard guard;

    @BeforeEach
    void setUp() {
        skillRepository = mock(SkillRepository.class);
        skillVersionRepository = mock(SkillVersionRepository.class);
        guard = new DefaultPromotionTargetGuard(skillRepository, skillVersionRepository);
    }

    @Test
    void assertPromotable_rejectsVersionFromDifferentSkill() {
        Skill skill = new Skill(1L, "demo", "owner", SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        SkillVersion otherSkillVersion = new SkillVersion(99L, "1.0.0", "owner");
        otherSkillVersion.setStatus(SkillVersionStatus.PUBLISHED);

        given(skillRepository.findById(10L)).willReturn(Optional.of(skill));
        given(skillVersionRepository.findById(200L)).willReturn(Optional.of(otherSkillVersion));

        assertThatThrownBy(() -> guard.assertPromotable(PromotionTargetType.SKILL, 10L, 200L))
                .isInstanceOf(PromotionException.class)
                .hasMessage("error.promotion.target.versionMismatch");
    }

    @Test
    void assertPromotable_acceptsPublishedVersionBelongingToSkill() {
        Skill skill = new Skill(1L, "demo", "owner", SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        SkillVersion version = new SkillVersion(10L, "1.0.0", "owner");
        version.setStatus(SkillVersionStatus.PUBLISHED);

        given(skillRepository.findById(10L)).willReturn(Optional.of(skill));
        given(skillVersionRepository.findById(200L)).willReturn(Optional.of(version));

        guard.assertPromotable(PromotionTargetType.SKILL, 10L, 200L);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
