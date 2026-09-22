package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizationDomainCreateRequest(
        @NotBlank @Size(max = 253) String domain
) {
}
