package com.iflytek.skillhub.repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Reads the current aggregate and distributable latest version, independently of viewer privileges or search indexing. */
@Repository
public class RecommendationSkillQuery {
    private static final String FROM = """
            FROM skill s JOIN namespace n ON n.id = s.namespace_id
            JOIN skill_version v ON v.id = s.latest_version_id AND v.skill_id = s.id
            WHERE s.visibility = 'PUBLIC' AND s.status = 'ACTIVE' AND s.hidden = FALSE
              AND n.status <> 'ARCHIVED' AND v.status = 'PUBLISHED'
              AND v.download_ready = TRUE AND v.yanked_at IS NULL
            """;
    private final EntityManager em;
    public RecommendationSkillQuery(EntityManager em) { this.em = em; }

    public List<Long> eligible(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return numbers(em.createNativeQuery("SELECT s.id " + FROM + " AND s.id IN (:ids)")
                .setParameter("ids", ids).getResultList());
    }
    public List<Long> candidates(String q, int page, int size) {
        return candidates(q, page, size, "");
    }
    public List<Long> candidates(String q, int page, int size, String label) {
        return numbers(em.createNativeQuery("SELECT s.id " + FROM + filter() + " ORDER BY s.updated_at DESC, s.id DESC")
                .setParameter("q", q.toLowerCase(java.util.Locale.ROOT))
                .setParameter("label", label)
                .setFirstResult(Math.multiplyExact(page, size)).setMaxResults(size).getResultList());
    }
    public long count(String q) {
        return count(q, "");
    }
    public long count(String q, String label) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) " + FROM + filter())
                .setParameter("q", q.toLowerCase(java.util.Locale.ROOT))
                .setParameter("label", label).getSingleResult()).longValue();
    }
    private String filter() {
        // POSITION makes %, _ and backslashes literal, without inventing a query language.
        return " AND (:q = '' OR POSITION(:q IN LOWER(s.display_name)) > 0 OR POSITION(:q IN LOWER(s.slug)) > 0 OR POSITION(:q IN LOWER(n.slug)) > 0)"
                + " AND (:label = '' OR EXISTS (SELECT 1 FROM skill_label sl JOIN label_definition ld ON ld.id = sl.label_id WHERE sl.skill_id = s.id AND ld.slug = :label))";
    }
    private List<Long> numbers(List<?> rows) { return rows.stream().map(value -> ((Number) value).longValue()).toList(); }
}
