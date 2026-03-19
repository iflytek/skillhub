package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom query repository that builds pageable admin-user search results with optional filters.
 */
@Repository
public class AdminUserSearchRepository {

    private final EntityManager entityManager;

    public AdminUserSearchRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Page<UserAccount> search(String search, UserStatus status, Pageable pageable) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();

        CriteriaQuery<UserAccount> query = builder.createQuery(UserAccount.class);
        Root<UserAccount> root = query.from(UserAccount.class);
        List<Predicate> predicates = buildPredicates(search, status, builder, root);
        query.select(root)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(builder.desc(root.get("createdAt")));

        TypedQuery<UserAccount> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        List<UserAccount> users = typedQuery.getResultList();

        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<UserAccount> countRoot = countQuery.from(UserAccount.class);
        List<Predicate> countPredicates = buildPredicates(search, status, builder, countRoot);
        countQuery.select(builder.count(countRoot))
                .where(countPredicates.toArray(Predicate[]::new));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(users, pageable, total);
    }

    private List<Predicate> buildPredicates(
            String search,
            UserStatus status,
            CriteriaBuilder builder,
            Root<UserAccount> root) {
        List<Predicate> predicates = new ArrayList<>();
        if (StringUtils.hasText(search)) {
            String normalized = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            predicates.add(builder.or(
                    builder.like(builder.lower(root.get("id")), normalized),
                    builder.like(builder.lower(root.get("displayName")), normalized),
                    builder.like(builder.lower(root.get("email")), normalized)
            ));
        }
        if (status != null) {
            predicates.add(builder.equal(root.get("status"), status));
        }
        return predicates;
    }
}
