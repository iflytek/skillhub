package com.iflytek.skillhub.dto;

import java.time.Instant;

public record LoginConnectionResponse(
        String id,
        String displayName,
        String status,
        String activeRevisionId,
        String lastTestedRevisionId,
        LoginConnectionRevisionResponse activeRevision,
        LoginConnectionRevisionResponse latestRevision,
        LoginConnectionHealthResponse health,
        Instant createdAt,
        Instant updatedAt
) {
    public record LoginConnectionRevisionResponse(
            String id,
            long revision,
            String issuer,
            String clientId,
            boolean secretConfigured,
            Instant createdAt
    ) {
    }

    public record LoginConnectionHealthResponse(String status, String failureReason, Instant checkedAt) {
    }
}
