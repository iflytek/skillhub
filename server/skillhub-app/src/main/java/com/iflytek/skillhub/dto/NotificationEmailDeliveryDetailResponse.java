package com.iflytek.skillhub.dto;

public record NotificationEmailDeliveryDetailResponse(long id, String status, String kind, java.time.Instant createdAt,
        @io.swagger.v3.oas.annotations.media.Schema(nullable = true) java.time.Instant acceptedAt, java.util.List<NotificationEmailDeliveryItemResponse> items, long totalItems,
        long unavailableCount, int size, @io.swagger.v3.oas.annotations.media.Schema(nullable = true) String nextCursor,
        @io.swagger.v3.oas.annotations.media.Schema(nullable = true) Long mergedIntoDeliveryId) {}
