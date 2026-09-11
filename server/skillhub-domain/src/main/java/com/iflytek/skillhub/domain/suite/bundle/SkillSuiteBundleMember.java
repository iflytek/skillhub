package com.iflytek.skillhub.domain.suite.bundle;

import com.iflytek.skillhub.domain.skill.SkillVisibility;

/** One ordered Bundle member, backed by either uploaded package content or an exact reference. */
public record SkillSuiteBundleMember(
        SkillSuiteBundleCoordinate coordinate,
        PackageSource packageSource,
        ReferenceSource referenceSource
) {
    public record PackageSource(String path, SkillVisibility visibility) {
    }

    public record ReferenceSource(String version) {
    }
}
