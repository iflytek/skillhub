package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.organization.OrganizationRole;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBinding;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBindingStatus;
import com.iflytek.skillhub.repository.OrganizationRoleBindingView;
import java.time.Instant;

public record OrganizationRoleBindingResponse(
        String id,
        String organizationId,
        String userId,
        String accountDisplayName,
        String accountEmail,
        OrganizationRole role,
        OrganizationRoleBindingStatus status,
        String createdBy,
        String revokedBy,
        Instant revokedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrganizationRoleBindingResponse from(OrganizationRoleBinding binding) {
        return from(new OrganizationRoleBindingView(binding, null, null));
    }

    public static OrganizationRoleBindingResponse from(OrganizationRoleBindingView view) {
        OrganizationRoleBinding binding = view.binding();
        return new OrganizationRoleBindingResponse(
                binding.getId(),
                binding.getOrganizationId(),
                binding.getUserId(),
                view.accountDisplayName(),
                view.accountEmail(),
                binding.getRole(),
                binding.getStatus(),
                binding.getCreatedBy(),
                binding.getRevokedBy(),
                binding.getRevokedAt(),
                binding.getCreatedAt(),
                binding.getUpdatedAt()
        );
    }
}
