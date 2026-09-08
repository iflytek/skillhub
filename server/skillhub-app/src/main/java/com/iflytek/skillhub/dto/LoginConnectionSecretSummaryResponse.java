package com.iflytek.skillhub.dto;

import java.time.Instant;

public record LoginConnectionSecretSummaryResponse(
        boolean configured,
        Instant updatedAt,
        Instant previousValidUntil
) {
}
