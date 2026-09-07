package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizationMemberCreateRequest(
        @NotBlank @Size(max = 128) String userId
) {
}
