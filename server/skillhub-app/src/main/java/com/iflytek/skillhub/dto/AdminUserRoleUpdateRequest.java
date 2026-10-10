package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminUserRoleUpdateRequest(
        @NotBlank(message = "{error.badRequest}")
        String role
) {
}
