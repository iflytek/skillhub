package com.iflytek.skillhub.dto.bundle;

import com.iflytek.skillhub.domain.bundle.SkillBundleVersion;
import com.iflytek.skillhub.domain.bundle.SkillBundleVersionStatus;

import java.time.Instant;

public record SkillBundleVersionResponse(
        Long bundleId,
        Long bundleVersionId,
        String version,
        SkillBundleVersionStatus status,
        Instant publishedAt
) {
    public static SkillBundleVersionResponse from(SkillBundleVersion v) {
        return new SkillBundleVersionResponse(
                v.getBundleId(), v.getId(), v.getVersion(),
                v.getStatus(), v.getPublishedAt());
    }
}
