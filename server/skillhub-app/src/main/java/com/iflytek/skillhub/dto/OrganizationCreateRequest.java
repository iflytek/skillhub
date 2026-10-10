package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizationCreateRequest(
        @NotBlank @Size(max = 64) String slug,
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(max = 128) String initialOwnerUserId
) {
}
