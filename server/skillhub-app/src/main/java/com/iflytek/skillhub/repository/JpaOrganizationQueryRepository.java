package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.organization.OrganizationRoleBindingStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class JpaOrganizationQueryRepository implements OrganizationQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<OrganizationMembershipProjection> findActiveMembershipsForUser(
            String userId,
            Pageable pageable
    ) {
        long total = ((Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM organization organization
                        JOIN organization_membership membership
                          ON membership.organization_id = organization.id
                        WHERE membership.user_id = :userId
                          AND membership.status = 'ACTIVE'
                          AND organization.status = 'ACTIVE'
                        """)
                .setParameter("userId", userId)
                .getSingleResult()).longValue();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT organization.id,
                               organization.slug,
                               organization.display_name,
                               organization.status,
                               organization.authority_version,
                               membership.id,
                               membership.authority_version,
                               membership.activated_at,
                               organization.created_at,
                               organization.updated_at
                        FROM organization organization
                        JOIN organization_membership membership
                          ON membership.organization_id = organization.id
                        WHERE membership.user_id = :userId
                          AND membership.status = 'ACTIVE'
                          AND organization.status = 'ACTIVE'
                        ORDER BY organization.slug ASC, organization.id ASC
                        """)
                .setParameter("userId", userId)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        return new PageImpl<>(
                rows.stream().map(this::toOrganizationMembership).toList(),
                pageable,
                total
        );
    }

    @Override
    public Optional<OrganizationMembershipProjection> findActiveMembership(
            String organizationId,
            String userId
    ) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT organization.id,
                               organization.slug,
                               organization.display_name,
                               organization.status,
                               organization.authority_version,
                               membership.id,
                               membership.authority_version,
                               membership.activated_at,
                               organization.created_at,
                               organization.updated_at
                        FROM organization organization
                        JOIN organization_membership membership
                          ON membership.organization_id = organization.id
                        WHERE organization.id = :organizationId
                          AND membership.user_id = :userId
                          AND membership.status = 'ACTIVE'
                          AND organization.status = 'ACTIVE'
                        """)
                .setParameter("organizationId", organizationId)
                .setParameter("userId", userId)
                .setMaxResults(1)
                .getResultList();
        return rows.stream().findFirst().map(this::toOrganizationMembership);
    }

    @Override
    public Map<String, Set<String>> findActiveRoleNames(
            String userId,
            Collection<String> organizationIds
    ) {
        if (organizationIds == null || organizationIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager.createQuery("""
                        SELECT binding.organizationId, binding.role
                        FROM OrganizationRoleBinding binding
                        WHERE binding.userId = :userId
                          AND binding.status = :status
                          AND binding.organizationId IN :organizationIds
                        ORDER BY binding.organizationId ASC, binding.role ASC
                        """, Object[].class)
                .setParameter("userId", userId)
                .setParameter("status", OrganizationRoleBindingStatus.ACTIVE)
                .setParameter("organizationIds", organizationIds)
                .getResultList();
        Map<String, Set<String>> grouped = rows.stream().collect(Collectors.groupingBy(
                row -> (String) row[0],
                LinkedHashMap::new,
                Collectors.mapping(row -> ((Enum<?>) row[1]).name(), Collectors.toUnmodifiableSet())
        ));
        return Map.copyOf(grouped);
    }

    @Override
    public Page<LoginConnectionSummaryProjection> findLoginConnections(
            String organizationId,
            Pageable pageable
    ) {
        long total = ((Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM login_connection
                        WHERE organization_id = :organizationId
                        """)
                .setParameter("organizationId", organizationId)
                .getSingleResult()).longValue();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT id,
                               public_handle,
                               display_name,
                               status,
                               adapter_key,
                               active_revision_id,
                               last_tested_revision_id,
                               created_at,
                               updated_at,
                               version
                        FROM login_connection
                        WHERE organization_id = :organizationId
                        ORDER BY created_at DESC, id ASC
                        """)
                .setParameter("organizationId", organizationId)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        return new PageImpl<>(
                rows.stream().map(this::toLoginConnection).toList(),
                pageable,
                total
        );
    }

    private OrganizationMembershipProjection toOrganizationMembership(Object[] row) {
        return new OrganizationMembershipProjection(
                (String) row[0],
                (String) row[1],
                (String) row[2],
                (String) row[3],
                ((Number) row[4]).longValue(),
                (String) row[5],
                ((Number) row[6]).longValue(),
                instant(row[7]),
                instant(row[8]),
                instant(row[9])
        );
    }

    private LoginConnectionSummaryProjection toLoginConnection(Object[] row) {
        return new LoginConnectionSummaryProjection(
                (String) row[0],
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (String) row[4],
                (String) row[5],
                (String) row[6],
                instant(row[7]),
                instant(row[8]),
                ((Number) row[9]).longValue()
        );
    }

    private Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        throw new IllegalArgumentException("unsupported timestamp type: " + value.getClass());
    }
}
