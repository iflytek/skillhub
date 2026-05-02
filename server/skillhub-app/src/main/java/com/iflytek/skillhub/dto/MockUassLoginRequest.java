package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record MockUassLoginRequest(
        @NotBlank String state,
        @NotBlank String callbackUrl,
        @NotBlank String ussId,
        @NotBlank String displayName,
        String mobile,
        @Email String email
) {
}
