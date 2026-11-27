package com.iflytek.skillhub.dto;

import java.time.Instant;

public record DirectoryConnectionResponse(
        String id,
        String organizationId,
        String protocol,
        String status,
        long secretVersion,
        String tokenHint,
        String scimBaseUrl,
        Instant createdAt,
        Instant updatedAt,
        String bootstrapToken
) {
    public DirectoryConnectionResponse withoutSecret() {
        return new DirectoryConnectionResponse(
                id, organizationId, protocol, status, secretVersion, tokenHint,
                scimBaseUrl, createdAt, updatedAt, null);
    }
}
