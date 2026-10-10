package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminUserStatusUpdateRequest(
        @NotBlank(message = "{error.badRequest}")
        String status
) {
}
