package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
    @NotBlank(message = "{validation.auth.local.currentPassword.notBlank}")
    String currentPassword,
    @NotBlank(message = "{validation.auth.local.newPassword.notBlank}")
    String newPassword
) {}
