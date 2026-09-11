package com.iflytek.skillhub.dto;

/** Compact metadata for one Suite sibling Skill visible to the current viewer. */
public record SkillSuiteSiblingMemberResponse(
        Long skillId,
        String namespace,
        String slug,
        String displayName,
        String version,
        boolean entry,
        boolean available
) {
}
