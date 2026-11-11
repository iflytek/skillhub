package com.iflytek.skillhub.dto;

public record NotificationEmailDeliveryAdminPageResponse(java.util.List<NotificationEmailDeliveryAdminResponse> items,
        long totalItems, int page, int size, int totalPages) {}
