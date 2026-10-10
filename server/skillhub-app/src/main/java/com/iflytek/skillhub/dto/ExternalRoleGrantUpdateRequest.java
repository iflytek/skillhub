package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExternalRoleGrantUpdateRequest(
        @NotBlank String roleCode,
        @NotNull Long version
) {}
