package com.iflytek.skillhub.dto;

public record NotificationEmailDeliveryAdminResponse(long id, String recipientId, String kind, String status,
        int attempts, @io.swagger.v3.oas.annotations.media.Schema(nullable = true) String resultCode, java.time.Instant createdAt, @io.swagger.v3.oas.annotations.media.Schema(nullable = true) java.time.Instant acceptedAt,
        @io.swagger.v3.oas.annotations.media.Schema(nullable = true) Long mergedIntoDeliveryId) {}
