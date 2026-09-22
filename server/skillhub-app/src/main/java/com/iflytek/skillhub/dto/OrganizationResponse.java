package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationRole;
import com.iflytek.skillhub.domain.organization.OrganizationStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record OrganizationResponse(
        String id,
        String slug,
        String displayName,
        OrganizationStatus status,
        long authorityVersion,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        List<OrganizationRole> callerRoles
) {
    public static OrganizationResponse from(
            Organization organization,
            Iterable<OrganizationRole> callerRoles
    ) {
        List<OrganizationRole> sortedRoles = new java.util.ArrayList<>();
        callerRoles.forEach(sortedRoles::add);
        sortedRoles.sort(Comparator.comparing(Enum::name));
        return new OrganizationResponse(
                organization.getId(),
                organization.getSlug(),
                organization.getDisplayName(),
                organization.getStatus(),
                organization.getAuthorityVersion(),
                organization.getCreatedBy(),
                organization.getCreatedAt(),
                organization.getUpdatedAt(),
                List.copyOf(sortedRoles)
        );
    }
}
