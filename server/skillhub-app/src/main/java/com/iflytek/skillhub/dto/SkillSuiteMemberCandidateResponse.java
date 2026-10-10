package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.skill.SkillVisibility;

/** One exact published Skill version eligible for the target Suite audience. */
public record SkillSuiteMemberCandidateResponse(
        Long skillId,
        Long skillVersionId,
        String namespace,
        String slug,
        String displayName,
        String version,
        SkillVisibility visibility,
        boolean recommended
) {
}
