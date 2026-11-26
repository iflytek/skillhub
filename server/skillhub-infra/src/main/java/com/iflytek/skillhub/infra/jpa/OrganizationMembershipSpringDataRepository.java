package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.organization.MembershipSourceType;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Optional<OrganizationMembership> findByOrganizationIdAndSourceTypeAndSourceId(
            String organizationId,
            MembershipSourceType sourceType,
            String sourceId
    );

    Page<OrganizationMembership> findByOrganizationIdAndSourceTypeAndSourceIdStartingWithAndSourceUserNameContainingIgnoreCase(
            String organizationId,
            MembershipSourceType sourceType,
            String sourcePrefix,
            String sourceUserName,
            Pageable pageable
    );

    Page<OrganizationMembership> findByOrganizationIdAndSourceTypeAndSourceIdStartingWithAndPrimaryEmailContainingIgnoreCase(
            String organizationId,
            MembershipSourceType sourceType,
            String sourcePrefix,
            String primaryEmail,
            Pageable pageable
    );

    Page<OrganizationMembership> findByOrganizationIdAndSourceTypeAndSourceIdStartingWith(
            String organizationId,
            MembershipSourceType sourceType,
            String sourcePrefix,
            Pageable pageable
    );

    List<OrganizationMembership> findTop2ByOrganizationIdAndPrimaryEmailIgnoreCaseOrderById(
            String organizationId,
            String email
    );
}
