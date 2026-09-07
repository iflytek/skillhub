package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationDomain;
import com.iflytek.skillhub.domain.organization.OrganizationMembershipStatus;
import com.iflytek.skillhub.domain.organization.OrganizationRole;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBindingStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class JpaEnterpriseIdentityQueryRepository
        implements EnterpriseIdentityQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Organization> findOrganizations(Pageable pageable) {
        return page(
                "SELECT o FROM Organization o ORDER BY o.updatedAt DESC, o.id ASC",
                "SELECT COUNT(o) FROM Organization o",
                Organization.class,
                pageable,
                Map.of()
        );
    }

    @Override
    public Page<Organization> findActiveOrganizationsByUserId(
            String userId,
            Pageable pageable
    ) {
        Map<String, Object> parameters = Map.of(
                "userId", userId,
                "status", OrganizationMembershipStatus.ACTIVE
        );
        return page(
                """
                SELECT o
                FROM Organization o, OrganizationMembership m
                WHERE m.organizationId = o.id
                  AND m.userId = :userId
                  AND m.status = :status
                ORDER BY o.updatedAt DESC, o.id ASC
                """,
                """
                SELECT COUNT(o)
                FROM Organization o, OrganizationMembership m
                WHERE m.organizationId = o.id
                  AND m.userId = :userId
                  AND m.status = :status
                """,
                Organization.class,
                pageable,
                parameters
        );
    }

    @Override
    public Map<String, Set<OrganizationRole>> findActiveRoles(
            List<String> organizationIds,
            String userId
    ) {
        if (organizationIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager.createQuery("""
                        SELECT b.organizationId, b.role
                        FROM OrganizationRoleBinding b
                        WHERE b.organizationId IN :organizationIds
                          AND b.userId = :userId
                          AND b.status = :status
                        ORDER BY b.organizationId ASC, b.role ASC
                        """, Object[].class)
                .setParameter("organizationIds", organizationIds)
                .setParameter("userId", userId)
                .setParameter("status", OrganizationRoleBindingStatus.ACTIVE)
                .getResultList();
        Map<String, Set<OrganizationRole>> result = new LinkedHashMap<>();
        rows.forEach(row -> result.computeIfAbsent(
                (String) row[0],
                ignored -> new LinkedHashSet<>()
        ).add((OrganizationRole) row[1]));
        return result;
    }

    @Override
    public Page<OrganizationDomain> findDomains(
            String organizationId,
            Pageable pageable
    ) {
        return page(
                """
                SELECT d FROM OrganizationDomain d
                WHERE d.organizationId = :organizationId
                ORDER BY d.updatedAt DESC, d.id ASC
                """,
                """
                SELECT COUNT(d) FROM OrganizationDomain d
                WHERE d.organizationId = :organizationId
                """,
                OrganizationDomain.class,
                pageable,
                Map.of("organizationId", organizationId)
        );
    }

    @Override
    public Page<OrganizationMembershipView> findMemberships(
            String organizationId,
            Pageable pageable
    ) {
        Map<String, Object> parameters = Map.of("organizationId", organizationId);
        TypedQuery<Object[]> query = entityManager.createQuery("""
                SELECT m, u.displayName, u.email
                FROM OrganizationMembership m
                LEFT JOIN UserAccount u ON u.id = m.userId
                WHERE m.organizationId = :organizationId
                ORDER BY m.updatedAt DESC, m.id ASC
                """, Object[].class);
        parameters.forEach(query::setParameter);
        applyPage(query, pageable);
        List<OrganizationMembershipView> items = query.getResultList().stream()
                .map(row -> new OrganizationMembershipView(
                        (com.iflytek.skillhub.domain.organization.OrganizationMembership) row[0],
                        (String) row[1],
                        (String) row[2]
                ))
                .toList();
        long total = count(
                """
                SELECT COUNT(m) FROM OrganizationMembership m
                WHERE m.organizationId = :organizationId
                """,
                parameters
        );
        return new PageImpl<>(items, pageable, total);
    }

    @Override
    public Page<OrganizationRoleBindingView> findRoleBindings(
            String organizationId,
            Pageable pageable
    ) {
        Map<String, Object> parameters = Map.of("organizationId", organizationId);
        TypedQuery<Object[]> query = entityManager.createQuery("""
                SELECT b, u.displayName, u.email
                FROM OrganizationRoleBinding b
                JOIN UserAccount u ON u.id = b.userId
                WHERE b.organizationId = :organizationId
                ORDER BY b.updatedAt DESC, b.id ASC
                """, Object[].class);
        parameters.forEach(query::setParameter);
        applyPage(query, pageable);
        List<OrganizationRoleBindingView> items = query.getResultList().stream()
                .map(row -> new OrganizationRoleBindingView(
                        (com.iflytek.skillhub.domain.organization.OrganizationRoleBinding) row[0],
                        (String) row[1],
                        (String) row[2]
                ))
                .toList();
        long total = count(
                """
                SELECT COUNT(b) FROM OrganizationRoleBinding b
                WHERE b.organizationId = :organizationId
                """,
                parameters
        );
        return new PageImpl<>(items, pageable, total);
    }

    private <T> Page<T> page(
            String contentJpql,
            String countJpql,
            Class<T> type,
            Pageable pageable,
            Map<String, Object> parameters
    ) {
        TypedQuery<T> query = entityManager.createQuery(contentJpql, type);
        parameters.forEach(query::setParameter);
        applyPage(query, pageable);
        return new PageImpl<>(
                query.getResultList(),
                pageable,
                count(countJpql, parameters)
        );
    }

    private long count(String jpql, Map<String, Object> parameters) {
        TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class);
        parameters.forEach(query::setParameter);
        return query.getSingleResult();
    }

    private void applyPage(TypedQuery<?> query, Pageable pageable) {
        query.setFirstResult(Math.toIntExact(pageable.getOffset()));
        query.setMaxResults(pageable.getPageSize());
    }
}
