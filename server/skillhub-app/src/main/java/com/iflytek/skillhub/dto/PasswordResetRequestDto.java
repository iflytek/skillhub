package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetRequestDto(
        @NotBlank(message = "{validation.auth.password.reset.email.notBlank}")
        @Email(message = "{validation.auth.password.reset.email.invalid}")
        @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "{validation.auth.password.reset.email.invalid}")
        String email
) {
}
