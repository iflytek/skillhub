package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.social.SkillStar;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class JpaStarredSkillQueryRepository implements StarredSkillQueryRepository {
    private final EntityManager entityManager;

    public JpaStarredSkillQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<Skill> findAccessible(String userId, Map<Long, NamespaceRole> namespaceRoles,
                                      Set<String> platformRoles, Pageable pageable) {
        var builder = entityManager.getCriteriaBuilder();
        var query = builder.createQuery(Skill.class);
        var skill = query.from(Skill.class);
        var star = query.from(SkillStar.class);
        query.select(skill).where(predicate(builder, skill, star, userId, namespaceRoles, platformRoles))
                .orderBy(builder.desc(star.get("createdAt")), builder.desc(star.get("id")));
        var result = entityManager.createQuery(query)
                .setFirstResult(Math.toIntExact(pageable.getOffset()))
                .setMaxResults(pageable.getPageSize()).getResultList();

        var countQuery = builder.createQuery(Long.class);
        var countSkill = countQuery.from(Skill.class);
        var countStar = countQuery.from(SkillStar.class);
        countQuery.select(builder.count(countSkill))
                .where(predicate(builder, countSkill, countStar, userId, namespaceRoles, platformRoles));
        return new PageImpl<>(result, pageable, entityManager.createQuery(countQuery).getSingleResult());
    }

    private Predicate predicate(CriteriaBuilder builder, Root<Skill> skill, Root<SkillStar> star,
                                 String userId, Map<Long, NamespaceRole> namespaceRoles, Set<String> platformRoles) {
        Predicate relation = builder.and(builder.equal(star.get("userId"), userId),
                builder.equal(star.get("skillId"), skill.get("id")));
        if (platformRoles != null && platformRoles.contains("SUPER_ADMIN")) return relation;

        // Keep this SQL projection equivalent to VisibilityChecker, including unpublished and hidden skills.
        Map<Long, NamespaceRole> roles = namespaceRoles != null ? namespaceRoles : Map.of();
        List<Long> adminNamespaces = roles.entrySet().stream()
                .filter(entry -> entry.getValue() == NamespaceRole.ADMIN || entry.getValue() == NamespaceRole.OWNER)
                .map(Map.Entry::getKey).toList();
        Predicate owner = builder.equal(skill.get("ownerId"), userId);
        Predicate admin = adminNamespaces.isEmpty() ? builder.disjunction() : skill.get("namespaceId").in(adminNamespaces);
        Predicate member = roles.isEmpty() ? builder.disjunction() : skill.get("namespaceId").in(roles.keySet());
        Predicate visibility = builder.or(
                builder.equal(skill.get("visibility"), SkillVisibility.PUBLIC),
                builder.and(builder.equal(skill.get("visibility"), SkillVisibility.NAMESPACE_ONLY), builder.or(owner, member)),
                builder.and(builder.equal(skill.get("visibility"), SkillVisibility.PRIVATE), builder.or(owner, admin)));
        Predicate accessible = builder.or(
                builder.and(builder.isTrue(skill.get("hidden")), builder.or(owner, admin)),
                builder.and(builder.isFalse(skill.get("hidden")), builder.or(
                        builder.and(builder.isNull(skill.get("latestVersionId")), owner),
                        builder.and(builder.isNotNull(skill.get("latestVersionId")), visibility))));
        return builder.and(relation, accessible);
    }
}
