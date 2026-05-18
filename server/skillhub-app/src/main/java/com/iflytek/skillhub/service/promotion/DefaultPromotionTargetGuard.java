package com.iflytek.skillhub.service.promotion;

import com.iflytek.skillhub.domain.promotion.PromotionException;
import com.iflytek.skillhub.domain.promotion.PromotionTargetGuard;
import com.iflytek.skillhub.domain.promotion.PromotionTargetType;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.springframework.stereotype.Component;

/**
 * Default {@link PromotionTargetGuard} implementation. For SKILL targets, checks
 * the existence and PUBLISHED state of the bound version (or the skill's latest
 * version when none was provided), and refuses non-PUBLIC, hidden, or archived
 * skills. SKILL_BUNDLE targets are wired in once the bundle aggregate ships.
 */
@Component
public class DefaultPromotionTargetGuard implements PromotionTargetGuard {

    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;

    public DefaultPromotionTargetGuard(SkillRepository skillRepository,
                                       SkillVersionRepository skillVersionRepository) {
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
    }

    @Override
    public void assertPromotable(PromotionTargetType targetType, Long targetId, Long targetVersionId) {
        if (targetType == null || targetId == null) {
            throw new PromotionException("error.promotion.target.invalid");
        }
        switch (targetType) {
            case SKILL -> assertSkillPromotable(targetId, targetVersionId);
            case SKILL_BUNDLE -> {
                // Skill bundle is delivered in branch feature/skill-bundle-management.
                // Until that branch lands, the guard accepts SKILL_BUNDLE targets so the
                // promotion module can be smoke-tested independently.
            }
        }
    }

    private void assertSkillPromotable(Long skillId, Long versionId) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new PromotionException("error.promotion.target.notFound"));
        if (skill.isHidden()) {
            throw new PromotionException("error.promotion.target.hidden");
        }
        if (skill.getVisibility() != SkillVisibility.PUBLIC) {
            throw new PromotionException("error.promotion.target.notPublic");
        }
        Long resolvedVersionId = versionId != null ? versionId : skill.getLatestVersionId();
        if (resolvedVersionId == null) {
            throw new PromotionException("error.promotion.target.noPublishedVersion");
        }
        SkillVersion version = skillVersionRepository.findById(resolvedVersionId)
                .orElseThrow(() -> new PromotionException("error.promotion.target.versionNotFound"));
        if (!skillId.equals(version.getSkillId())) {
            throw new PromotionException("error.promotion.target.versionMismatch");
        }
        if (version.getStatus() != SkillVersionStatus.PUBLISHED) {
            throw new PromotionException("error.promotion.target.versionNotPublished");
        }
    }
}
