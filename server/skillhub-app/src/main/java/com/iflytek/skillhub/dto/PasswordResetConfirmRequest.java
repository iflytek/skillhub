package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetConfirmRequest(
    @NotBlank(message = "{validation.auth.password.reset.token.notBlank}")
    String token,
    @NotBlank(message = "{validation.auth.password.reset.newPassword.notBlank}")
    String newPassword
) {}
