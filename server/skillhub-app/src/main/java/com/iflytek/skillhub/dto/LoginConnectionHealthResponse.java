package com.iflytek.skillhub.dto;

import java.time.Instant;

public record LoginConnectionHealthResponse(
        String status,
        String failureReason,
        Instant checkedAt
) {
}
