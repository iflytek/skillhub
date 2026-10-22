package com.iflytek.skillhub.dto;

public record NotificationEmailDeliveryDetailResponse(long id, String status, String kind, java.time.Instant createdAt,
        java.time.Instant acceptedAt, java.util.List<NotificationEmailDeliveryItemResponse> items, long totalItems,
        long unavailableItems, int page, int size, int totalPages) {}
