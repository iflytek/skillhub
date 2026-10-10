package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "{validation.auth.password.reset.email.notBlank}")
        @Email(message = "{validation.auth.password.reset.email.invalid}")
        @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "{validation.auth.password.reset.email.invalid}")
        String email,
        @NotBlank(message = "{validation.auth.password.reset.code.notBlank}")
        @Pattern(regexp = "^\\d{6}$", message = "{validation.auth.password.reset.code.invalid}")
        String code,
        @NotBlank(message = "{validation.auth.password.reset.newPassword.notBlank}")
        String newPassword
) {
}
