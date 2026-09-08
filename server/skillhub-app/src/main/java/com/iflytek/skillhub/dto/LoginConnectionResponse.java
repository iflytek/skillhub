package com.iflytek.skillhub.dto;

import java.time.Instant;

public record LoginConnectionResponse(
        String id,
        String publicHandle,
        String displayName,
        String adapterKey,
        String status,
        String activeRevisionId,
        String lastTestedRevisionId,
        LoginConnectionRevisionResponse latestRevision,
        LoginConnectionSecretSummaryResponse secret,
        LoginConnectionHealthResponse health,
        Instant createdAt,
        Instant updatedAt
) {
}
