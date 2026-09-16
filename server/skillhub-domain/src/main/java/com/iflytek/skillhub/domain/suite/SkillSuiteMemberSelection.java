package com.iflytek.skillhub.domain.suite;

/**
 * Exact published Skill version selected for a Suite draft.
 */
public record SkillSuiteMemberSelection(
        Long skillId,
        Long skillVersionId,
        String namespaceSlug,
        String skillSlug,
        String version,
        String fingerprint
) {
}
