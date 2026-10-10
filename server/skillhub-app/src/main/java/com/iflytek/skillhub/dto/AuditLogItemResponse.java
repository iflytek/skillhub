package com.iflytek.skillhub.dto;

import java.time.Instant;

public record AuditLogItemResponse(
        Long id,
        String action,
        String userId,
        String username,
        String details,
        String ipAddress,
        String requestId,
        String resourceType,
        String resourceId,
        Instant timestamp
) {
}
