package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationStatus;

public record OrganizationCreateResponse(
        String id,
        String slug,
        String displayName,
        OrganizationStatus status,
        long authorityVersion
) {
    public static OrganizationCreateResponse from(Organization organization) {
        return new OrganizationCreateResponse(
                organization.getId(),
                organization.getSlug(),
                organization.getDisplayName(),
                organization.getStatus(),
                organization.getAuthorityVersion());
    }
}
