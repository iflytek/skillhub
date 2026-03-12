package com.iflytek.skillhub.dto;

import java.time.Instant;

public record AuditLogItemResponse(
        String id,
        String userId,
        String action,
        String resourceType,
        String resourceId,
        Instant timestamp,
        String ipAddress
) {
}
