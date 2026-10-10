package com.iflytek.skillhub.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrganizationQueryRepository {

    Page<OrganizationMembershipProjection> findActiveMembershipsForUser(
            String userId,
            Pageable pageable
    );

    Optional<OrganizationMembershipProjection> findActiveMembership(
            String organizationId,
            String userId
    );

    Map<String, Set<String>> findActiveRoleNames(
            String userId,
            Collection<String> organizationIds
    );

    Page<LoginConnectionSummaryProjection> findLoginConnections(
            String organizationId,
            Pageable pageable
    );

    record OrganizationMembershipProjection(
            String id,
            String slug,
            String displayName,
            String status,
            long authorityVersion,
            String membershipId,
            long membershipAuthorityVersion,
            Instant memberSince,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    record LoginConnectionSummaryProjection(
            String id,
            String publicHandle,
            String displayName,
            String status,
            String adapterKey,
            String activeRevisionId,
            String lastTestedRevisionId,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
    }
}
