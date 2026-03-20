package com.iflytek.skillhub.dto;

public record NotificationResponse(
    Long id, String category, String eventType, String title,
    String bodyJson, String entityType, Long entityId,
    String status, String createdAt, String readAt,
    String targetType, Long targetId, String targetRoute
) {}
