package com.iflytek.skillhub.dto;

/** One exact downloadable Skill in a Suite install plan. */
public record SkillSuiteInstallMemberResponse(
        Long skillId,
        Long skillVersionId,
        String namespace,
        String slug,
        String version,
        String fingerprint,
        String downloadUrl,
        int position,
        boolean entry
) {
}
