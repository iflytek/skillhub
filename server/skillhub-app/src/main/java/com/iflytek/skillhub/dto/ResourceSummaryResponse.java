package com.iflytek.skillhub.dto;

import java.time.Instant;

/** Type-explicit discovery item used by new clients without changing the legacy Skill search API. */
public record ResourceSummaryResponse(
        String resourceType,
        String detailUrl,
        Long id,
        String namespace,
        String slug,
        String displayName,
        String summary,
        String version,
        String visibility,
        long installCount,
        boolean available,
        Instant updatedAt
) {
}
