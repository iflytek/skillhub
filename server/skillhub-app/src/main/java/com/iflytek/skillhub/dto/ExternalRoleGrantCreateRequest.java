package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExternalRoleGrantCreateRequest(
        @NotBlank @Size(max = 64) String providerCode,
        @NotBlank @Size(max = 256) String email,
        @NotBlank @Size(max = 64) String roleCode
) {}
