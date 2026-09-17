package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.organization.MembershipSourceType;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal Spring Data delegate that keeps every membership lookup tenant scoped. */
interface OrganizationMembershipSpringDataRepository
        extends JpaRepository<OrganizationMembership, String> {

    Optional<OrganizationMembership> findByOrganizationIdAndId(
            String organizationId,
            String membershipId
    );

    Optional<OrganizationMembership> findByOrganizationIdAndUserIdAndStatusNot(
            String organizationId,
            String userId,
            OrganizationMembershipStatus excludedStatus
    );

    Optional<OrganizationMembership> findByOrganizationIdAndSourceTypeAndSourceIdAndStatusNot(
            String organizationId,
            MembershipSourceType sourceType,
            String sourceId,
            OrganizationMembershipStatus excludedStatus
    );

    List<OrganizationMembership> findTop2ByOrganizationIdAndPrimaryEmailIgnoreCaseOrderById(
            String organizationId,
            String email
    );
}
