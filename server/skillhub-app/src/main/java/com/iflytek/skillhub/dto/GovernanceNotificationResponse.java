package com.iflytek.skillhub.dto;

public record GovernanceNotificationResponse(
        Long id,
        String category,
        String entityType,
        Long entityId,
        String title,
        String bodyJson,
        String status,
        String createdAt,
        String readAt
) {
}
