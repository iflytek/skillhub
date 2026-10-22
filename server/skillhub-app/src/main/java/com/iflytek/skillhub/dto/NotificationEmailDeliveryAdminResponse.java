package com.iflytek.skillhub.dto;

public record NotificationEmailDeliveryAdminResponse(long id, String recipientId, String kind, String status,
        int attempts, String resultCode, java.time.Instant createdAt, java.time.Instant acceptedAt) {}
