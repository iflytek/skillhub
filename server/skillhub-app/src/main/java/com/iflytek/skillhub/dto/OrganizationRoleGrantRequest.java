package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.organization.OrganizationRole;
import jakarta.validation.constraints.NotNull;

public record OrganizationRoleGrantRequest(@NotNull OrganizationRole role) {
}
