package com.iflytek.skillhub.dto;

import java.time.Instant;

public record LoginConnectionSummaryResponse(
        String id,
        String publicHandle,
        String displayName,
        String status,
        String adapterKey,
        String activeRevisionId,
        String lastTestedRevisionId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
