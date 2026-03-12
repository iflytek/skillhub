package com.iflytek.skillhub.dto;

public record AdminUserMutationResponse(
        String userId,
        String role,
        String status
) {
}
