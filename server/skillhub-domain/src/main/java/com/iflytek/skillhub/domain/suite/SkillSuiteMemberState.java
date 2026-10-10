package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;

/** Current state resolved for an immutable member reference. Null IDs represent a tombstone. */
public record SkillSuiteMemberState(
        Long skillId,
        Long skillVersionId,
        Long namespaceId,
        String displayName,
        String summary,
        NamespaceStatus namespaceStatus,
        SkillVisibility visibility,
        SkillStatus skillStatus,
        boolean hidden,
        SkillVersionStatus versionStatus,
        boolean downloadReady,
        boolean yanked,
        boolean viewerCanRead
) {
    public static SkillSuiteMemberState deleted(Long skillId, Long skillVersionId) {
        return new SkillSuiteMemberState(
                skillId, skillVersionId, null, null, null, null, null, null,
                false, null, false, false, false);
    }

    public boolean tombstoned() {
        return skillId == null || skillVersionId == null || namespaceId == null;
    }
}
