package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.skill.SkillVisibility;

import java.util.List;

/** Fully resolved input for creating a Suite and its first immutable-version draft. */
public record CreateSkillSuiteDraftCommand(
        Long namespaceId,
        String slug,
        String displayName,
        String summary,
        String overview,
        String version,
        SkillVisibility visibility,
        String changelog,
        Long entrySkillVersionId,
        List<SkillSuiteMemberSelection> members
) {
    public CreateSkillSuiteDraftCommand {
        members = List.copyOf(members);
    }
}
