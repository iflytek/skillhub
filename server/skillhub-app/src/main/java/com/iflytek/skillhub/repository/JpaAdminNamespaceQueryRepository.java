package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.dto.AdminNamespaceListStatsResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class JpaAdminNamespaceQueryRepository implements AdminNamespaceQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Namespace> search(String keyword, NamespaceStatus status, NamespaceType type, Pageable pageable) {
        Map<String, Object> params = new HashMap<>();
        String whereClause = buildWhereClause(keyword, status, type, params);
        String orderClause = " ORDER BY n.updatedAt DESC, n.slug ASC";

        TypedQuery<Namespace> query = entityManager.createQuery(
                "SELECT n FROM Namespace n" + whereClause + orderClause,
                Namespace.class);
        params.forEach(query::setParameter);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        TypedQuery<Long> countQuery = entityManager.createQuery(
                "SELECT COUNT(n) FROM Namespace n" + whereClause,
                Long.class);
        params.forEach(countQuery::setParameter);

        return new PageImpl<>(query.getResultList(), pageable, countQuery.getSingleResult());
    }

    @Override
    public AdminNamespaceListStatsResponse stats() {
        long total = entityManager.createQuery("SELECT COUNT(n) FROM Namespace n", Long.class).getSingleResult();
        long active = countByStatus(NamespaceStatus.ACTIVE);
        long frozen = countByStatus(NamespaceStatus.FROZEN);
        long archived = countByStatus(NamespaceStatus.ARCHIVED);
        return new AdminNamespaceListStatsResponse(total, active, frozen, archived);
    }

    @Override
    public Map<Long, Long> countMembersByNamespaceId(Iterable<Long> namespaceIds) {
        return countByNamespaceId("""
                SELECT m.namespaceId, COUNT(m)
                FROM NamespaceMember m
                WHERE m.namespaceId IN :namespaceIds
                GROUP BY m.namespaceId
                """, namespaceIds);
    }

    @Override
    public Map<Long, Long> countSkillsByNamespaceId(Iterable<Long> namespaceIds) {
        return countByNamespaceId("""
                SELECT s.namespaceId, COUNT(s)
                FROM Skill s
                WHERE s.namespaceId IN :namespaceIds
                GROUP BY s.namespaceId
                """, namespaceIds);
    }

    private String buildWhereClause(String keyword,
                                    NamespaceStatus status,
                                    NamespaceType type,
                                    Map<String, Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (LOWER(n.slug) LIKE :keyword OR LOWER(n.displayName) LIKE :keyword OR LOWER(n.description) LIKE :keyword)");
            params.put("keyword", "%" + keyword.trim().toLowerCase() + "%");
        }
        if (status != null) {
            where.append(" AND n.status = :status");
            params.put("status", status);
        }
        if (type != null) {
            where.append(" AND n.type = :type");
            params.put("type", type);
        }
        return where.toString();
    }

    private long countByStatus(NamespaceStatus status) {
        return entityManager.createQuery("SELECT COUNT(n) FROM Namespace n WHERE n.status = :status", Long.class)
                .setParameter("status", status)
                .getSingleResult();
    }

    private Map<Long, Long> countByNamespaceId(String jpql, Iterable<Long> namespaceIds) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        namespaceIds.forEach(ids::add);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Object[] row : entityManager.createQuery(jpql, Object[].class)
                .setParameter("namespaceIds", ids)
                .getResultList()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }
}
