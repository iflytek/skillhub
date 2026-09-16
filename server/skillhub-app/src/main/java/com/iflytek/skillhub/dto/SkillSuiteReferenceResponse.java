package com.iflytek.skillhub.dto;

/** One currently visible published Suite that uses this Skill as its orchestration entry. */
public record SkillSuiteReferenceResponse(
        Long suiteId,
        String namespace,
        String slug,
        String displayName,
        String version,
        int memberCount
) {
}
