package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LocalRegisterRequest(
    @NotBlank(message = "{validation.auth.local.username.notBlank}")
    String username,
    @NotBlank(message = "{validation.auth.local.password.notBlank}")
    String password,
    @Email(message = "{validation.auth.local.email.invalid}")
    String email
) {}
