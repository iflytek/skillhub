package com.iflytek.skillhub.dto;

import java.time.Instant;

public record LoginConnectionTestResponse(
        boolean success,
        String revisionId,
        String failureReason,
        Instant checkedAt
) {
}
