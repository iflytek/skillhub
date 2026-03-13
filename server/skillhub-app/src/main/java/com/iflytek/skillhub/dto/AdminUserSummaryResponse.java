package com.iflytek.skillhub.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminUserSummaryResponse(
        String userId,
        String username,
        String email,
        List<String> platformRoles,
        String status,
        LocalDateTime createdAt
) {
}
