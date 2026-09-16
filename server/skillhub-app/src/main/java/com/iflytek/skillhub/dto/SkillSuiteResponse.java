package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.suite.SkillSuiteAllowedAction;

import java.util.List;
import java.util.Set;

/** Suite container and one concrete version snapshot. */
public record SkillSuiteResponse(
        Long id,
        Long versionId,
        String namespace,
        String slug,
        String displayName,
        String summary,
        String overview,
        String version,
        String status,
        SkillVisibility visibility,
        String suiteStatus,
        boolean hidden,
        Set<SkillSuiteAllowedAction> allowedActions,
        boolean available,
        List<SkillSuiteMemberResponse> members
) {
}
