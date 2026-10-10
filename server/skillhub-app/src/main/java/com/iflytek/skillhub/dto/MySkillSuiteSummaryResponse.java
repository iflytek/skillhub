package com.iflytek.skillhub.dto;

import java.time.Instant;

/** Latest manageable version of one Suite shown in the current user's dashboard. */
public record MySkillSuiteSummaryResponse(
        Long id,
        Long versionId,
        String namespace,
        String slug,
        String displayName,
        String summary,
        String version,
        String versionStatus,
        String suiteStatus,
        String visibility,
        boolean hidden,
        Instant updatedAt
) {
}
