package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.organization.OrganizationRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrganizationRoleBindingCreateRequest(
        @NotBlank @Size(max = 128) String userId,
        @NotNull OrganizationRole role
) {
}
