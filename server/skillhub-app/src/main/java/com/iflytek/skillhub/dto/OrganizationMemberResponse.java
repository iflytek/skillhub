package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.organization.MembershipSourceType;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipStatus;
import com.iflytek.skillhub.repository.OrganizationMembershipView;
import java.time.Instant;

public record OrganizationMemberResponse(
        String id,
        String organizationId,
        String userId,
        OrganizationMembershipStatus status,
        MembershipSourceType sourceType,
        String sourceId,
        String organizationDisplayName,
        String organizationEmail,
        String accountDisplayName,
        String accountEmail,
        String department,
        String employeeNumber,
        long authorityVersion,
        Instant activatedAt,
        Instant suspendedAt,
        Instant deprovisionedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrganizationMemberResponse from(OrganizationMembership membership) {
        return from(new OrganizationMembershipView(membership, null, null));
    }

    public static OrganizationMemberResponse from(OrganizationMembershipView view) {
        OrganizationMembership membership = view.membership();
        return new OrganizationMemberResponse(
                membership.getId(),
                membership.getOrganizationId(),
                membership.getUserId(),
                membership.getStatus(),
                membership.getSourceType(),
                membership.getSourceId(),
                membership.getDisplayName(),
                membership.getPrimaryEmail(),
                view.accountDisplayName(),
                view.accountEmail(),
                membership.getDepartment(),
                membership.getEmployeeNumber(),
                membership.getAuthorityVersion(),
                membership.getActivatedAt(),
                membership.getSuspendedAt(),
                membership.getDeprovisionedAt(),
                membership.getCreatedAt(),
                membership.getUpdatedAt()
        );
    }
}
