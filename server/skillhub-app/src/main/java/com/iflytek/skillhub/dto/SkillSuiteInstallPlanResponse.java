package com.iflytek.skillhub.dto;

import java.util.List;

/** Fully preflighted exact-version plan consumed by the CLI staged installer. */
public record SkillSuiteInstallPlanResponse(
        String operationId,
        String namespace,
        String slug,
        String version,
        String fingerprint,
        List<SkillSuiteInstallMemberResponse> members
) {
}
