package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequestDto(
    @NotBlank(message = "{validation.auth.password.reset.identifier.notBlank}")
    String identifier
) {}
