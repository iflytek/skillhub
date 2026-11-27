package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.organization.MembershipSourceType;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipRepository;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/** JPA adapter that exposes only organization-scoped membership reads to domain callers. */
@Repository
public class JpaOrganizationMembershipRepositoryAdapter
        implements OrganizationMembershipRepository {

    private final OrganizationMembershipSpringDataRepository delegate;

    public JpaOrganizationMembershipRepositoryAdapter(
            OrganizationMembershipSpringDataRepository delegate
    ) {
        this.delegate = delegate;
    }

    @Override
    public Optional<OrganizationMembership> findByOrganizationIdAndId(
            String organizationId,
            String membershipId
    ) {
        return delegate.findByOrganizationIdAndId(organizationId, membershipId);
    }

    @Override
    public Optional<OrganizationMembership> findCurrentByOrganizationIdAndUserId(
            String organizationId,
            String userId
    ) {
        return delegate.findByOrganizationIdAndUserIdAndStatusNot(
                organizationId,
                userId,
                OrganizationMembershipStatus.DEPROVISIONED
        );
    }

    @Override
    public Optional<OrganizationMembership> findCurrentByOrganizationIdAndSource(
            String organizationId,
            MembershipSourceType sourceType,
            String sourceId
    ) {
        return delegate.findByOrganizationIdAndSourceTypeAndSourceIdAndStatusNot(
                organizationId,
                sourceType,
                sourceId,
                OrganizationMembershipStatus.DEPROVISIONED
        );
    }

    @Override
    public Optional<OrganizationMembership> findByOrganizationIdAndSource(
            String organizationId,
            MembershipSourceType sourceType,
            String sourceId
    ) {
        return delegate.findByOrganizationIdAndSourceTypeAndSourceId(
                organizationId, sourceType, sourceId);
    }

    @Override
    public Page<OrganizationMembership> findDirectoryMembers(
            String organizationId,
            String connectionId,
            String sourceUserName,
            String primaryEmail,
            Pageable pageable
    ) {
        String prefix = connectionId + ":";
        if (sourceUserName != null) {
            return delegate.findByOrganizationIdAndSourceTypeAndSourceIdStartingWithAndSourceUserNameContainingIgnoreCase(
                    organizationId, MembershipSourceType.DIRECTORY, prefix, sourceUserName, pageable);
        }
        if (primaryEmail != null) {
            return delegate.findByOrganizationIdAndSourceTypeAndSourceIdStartingWithAndPrimaryEmailContainingIgnoreCase(
                    organizationId, MembershipSourceType.DIRECTORY, prefix, primaryEmail, pageable);
        }
        return delegate.findByOrganizationIdAndSourceTypeAndSourceIdStartingWith(
                organizationId, MembershipSourceType.DIRECTORY, prefix, pageable);
    }

    @Override
    public List<OrganizationMembership> findIdentityCorrelationCandidatesByPrimaryEmail(
            String organizationId,
            String email
    ) {
        return delegate.findTop2ByOrganizationIdAndPrimaryEmailIgnoreCaseOrderById(
                organizationId,
                email
        );
    }

    @Override
    public OrganizationMembership save(OrganizationMembership membership) {
        return delegate.save(membership);
    }
}
