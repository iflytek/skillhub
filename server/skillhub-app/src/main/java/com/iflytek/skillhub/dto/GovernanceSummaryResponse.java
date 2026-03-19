package com.iflytek.skillhub.dto;

public record GovernanceSummaryResponse(
        long pendingReviews,
        long pendingPromotions,
        long pendingReports,
        long unreadNotifications
) {
}
