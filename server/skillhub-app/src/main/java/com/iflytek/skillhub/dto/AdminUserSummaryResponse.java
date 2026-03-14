package com.iflytek.skillhub.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminUserSummaryResponse(
        String id,
        String username,
        String email,
        String status,
        List<String> platformRoles,
        LocalDateTime createdAt
) {
}
