package com.iflytek.skillhub.domain.organization;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Tenant-scoped repository port for organization membership persistence. */
public interface OrganizationMembershipRepository {

    Optional<OrganizationMembership> findByOrganizationIdAndId(
            String organizationId,
            String membershipId
    );

    Optional<OrganizationMembership> findCurrentByOrganizationIdAndUserId(
            String organizationId,
            String userId
    );

    Optional<OrganizationMembership> findCurrentByOrganizationIdAndSource(
            String organizationId,
            MembershipSourceType sourceType,
            String sourceId
    );

    Optional<OrganizationMembership> findByOrganizationIdAndSource(
            String organizationId,
            MembershipSourceType sourceType,
            String sourceId
    );

    Page<OrganizationMembership> findDirectoryMembers(
            String organizationId,
            String connectionId,
            String sourceUserName,
            String primaryEmail,
            Pageable pageable
    );

    /**
     * Returns at most two current/history rows: zero is absent, one is unique and two is ambiguous.
     * Inactive rows participate so login cannot bypass suspension or deprovisioning via JIT.
     */
    List<OrganizationMembership> findIdentityCorrelationCandidatesByPrimaryEmail(
            String organizationId,
            String email
    );

    OrganizationMembership save(OrganizationMembership membership);
}
