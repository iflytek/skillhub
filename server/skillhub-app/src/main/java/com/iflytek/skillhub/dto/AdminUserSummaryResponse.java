package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.List;

public record AdminUserSummaryResponse(
        String id,
        String username,
        String email,
        String status,
        List<String> platformRoles,
        Instant createdAt
) {
}
