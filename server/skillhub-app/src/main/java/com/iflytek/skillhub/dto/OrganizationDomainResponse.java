package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.organization.OrganizationDomain;
import com.iflytek.skillhub.domain.organization.OrganizationDomainStatus;
import com.iflytek.skillhub.domain.organization.OrganizationDomainVerificationMethod;
import java.time.Instant;

public record OrganizationDomainResponse(
        String id,
        String organizationId,
        String domain,
        OrganizationDomainStatus status,
        OrganizationDomainVerificationMethod verificationMethod,
        Instant verifiedAt,
        Instant lastCheckedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrganizationDomainResponse from(OrganizationDomain domain) {
        return new OrganizationDomainResponse(
                domain.getId(),
                domain.getOrganizationId(),
                domain.getDomain(),
                domain.getStatus(),
                domain.getVerificationMethod(),
                domain.getVerifiedAt(),
                domain.getLastCheckedAt(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }
}
