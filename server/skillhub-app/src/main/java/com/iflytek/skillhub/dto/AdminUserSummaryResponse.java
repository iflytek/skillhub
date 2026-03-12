package com.iflytek.skillhub.dto;

public record AdminUserSummaryResponse(
        String userId,
        String username,
        String role,
        String status
) {
}
