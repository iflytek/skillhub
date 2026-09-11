package com.iflytek.skillhub.dto;

import java.util.List;

/** One currently visible published Suite whose latest snapshot contains this Skill. */
public record SkillSuiteReferenceResponse(
        Long suiteId,
        String namespace,
        String slug,
        String displayName,
        String version,
        int memberCount,
        boolean currentSkillEntry,
        List<SkillSuiteSiblingMemberResponse> visibleSiblingMembers,
        int restrictedMemberCount,
        int omittedVisibleMemberCount
) {
}
