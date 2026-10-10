package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.skill.Skill;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * Executes the pageable, presentation-specific hidden-skill search without widening the domain
 * repository with platform-governance query concerns.
 */
@Repository
public class JpaHiddenSkillQueryRepository implements HiddenSkillQueryRepository {

    private final EntityManager entityManager;

    public JpaHiddenSkillQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<Skill> search(String keyword, Long namespaceId, Pageable pageable) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();

        CriteriaQuery<Skill> query = builder.createQuery(Skill.class);
        Root<Skill> root = query.from(Skill.class);
        query.select(root)
                .where(buildPredicates(keyword, namespaceId, builder, root).toArray(Predicate[]::new))
                .orderBy(builder.desc(root.get("updatedAt")));
        TypedQuery<Skill> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<Skill> countRoot = countQuery.from(Skill.class);
        countQuery.select(builder.count(countRoot))
                .where(buildPredicates(keyword, namespaceId, builder, countRoot).toArray(Predicate[]::new));

        return new PageImpl<>(typedQuery.getResultList(), pageable, entityManager.createQuery(countQuery).getSingleResult());
    }

    private List<Predicate> buildPredicates(String keyword,
                                            Long namespaceId,
                                            CriteriaBuilder builder,
                                            Root<Skill> root) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.isTrue(root.get("hidden")));
        if (namespaceId != null) {
            predicates.add(builder.equal(root.get("namespaceId"), namespaceId));
        }
        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            predicates.add(builder.or(
                    builder.like(builder.lower(root.get("displayName")), pattern),
                    builder.like(builder.lower(root.get("slug")), pattern),
                    builder.like(builder.lower(root.get("summary")), pattern)
            ));
        }
        return predicates;
    }
}
