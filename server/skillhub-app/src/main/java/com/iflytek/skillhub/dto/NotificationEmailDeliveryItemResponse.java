package com.iflytek.skillhub.dto;

public record NotificationEmailDeliveryItemResponse(
        long id, String category, String eventType, java.time.Instant occurredAt, String targetPath) {}
