package com.iflytek.skillhub.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.List;
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

    Page<OrganizationMemberProjection> findCurrentMembers(
            String organizationId,
            Pageable pageable
    );

    Map<String, List<OrganizationMemberRoleProjection>> findActiveMemberRoles(
            String organizationId,
            Collection<String> userIds
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

    record OrganizationMemberProjection(
            String id,
            String userId,
            String displayName,
            String status,
            long authorityVersion,
            Instant createdAt
    ) {
    }

    record OrganizationMemberRoleProjection(String id, String userId, String role) {
    }
}
