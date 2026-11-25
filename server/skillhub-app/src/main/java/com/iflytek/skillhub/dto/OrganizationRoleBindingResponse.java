package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.organization.OrganizationRoleBinding;

public record OrganizationRoleBindingResponse(
        String id,
        String userId,
        String role,
        String status
) {
    public static OrganizationRoleBindingResponse from(OrganizationRoleBinding binding) {
        return new OrganizationRoleBindingResponse(
                binding.getId(), binding.getUserId(), binding.getRole().name(),
                binding.getStatus().name()
        );
    }
}
