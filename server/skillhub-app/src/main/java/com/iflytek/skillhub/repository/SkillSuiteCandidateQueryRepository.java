package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.dto.SkillSuiteMemberCandidateResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Server-side candidate projection for Suite authoring.
 *
 * <p>Direct SQL keeps authorization, installability, audience compatibility, and result limiting
 * in one database query; assembling this through aggregate repositories would load inaccessible
 * Skills before filtering and introduce an N+1 version lookup.
 */
@Repository
public class SkillSuiteCandidateQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SkillSuiteCandidateQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SkillSuiteMemberCandidateResponse> search(
            Long suiteNamespaceId,
            SkillVisibility suiteVisibility,
            String query,
            String userId,
            List<Long> memberNamespaceIds,
            List<Long> adminNamespaceIds,
            boolean superAdmin,
            int size
    ) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("suiteNamespaceId", suiteNamespaceId)
                .addValue("suiteVisibility", suiteVisibility.name())
                .addValue("query", "%" + normalizedQuery + "%")
                .addValue("userId", userId)
                .addValue("memberNamespaceIds", nonEmpty(memberNamespaceIds))
                .addValue("adminNamespaceIds", nonEmpty(adminNamespaceIds))
                .addValue("superAdmin", superAdmin)
                .addValue("size", size);

        return jdbcTemplate.query("""
                SELECT s.id AS skill_id,
                       sv.id AS skill_version_id,
                       n.slug AS namespace_slug,
                       s.slug AS skill_slug,
                       COALESCE(s.display_name, s.slug) AS display_name,
                       sv.version,
                       s.visibility,
                       (s.latest_version_id = sv.id) AS recommended
                FROM skill s
                JOIN namespace n ON n.id = s.namespace_id
                JOIN skill_version sv ON sv.skill_id = s.id
                WHERE s.status = 'ACTIVE'
                  AND s.hidden = FALSE
                  AND n.status = 'ACTIVE'
                  AND sv.status = 'PUBLISHED'
                  AND sv.download_ready = TRUE
                  AND sv.yanked_at IS NULL
                  AND (
                        :superAdmin = TRUE
                        OR s.visibility = 'PUBLIC'
                        OR (s.visibility = 'NAMESPACE_ONLY' AND s.namespace_id IN (:memberNamespaceIds))
                        OR (s.visibility = 'PRIVATE' AND (
                            s.owner_id = :userId OR s.namespace_id IN (:adminNamespaceIds)
                        ))
                  )
                  AND (
                        s.visibility = 'PUBLIC'
                        OR (:suiteVisibility = 'NAMESPACE_ONLY'
                            AND s.namespace_id = :suiteNamespaceId
                            AND s.visibility = 'NAMESPACE_ONLY')
                        OR (:suiteVisibility = 'PRIVATE'
                            AND s.namespace_id = :suiteNamespaceId
                            AND s.visibility IN ('NAMESPACE_ONLY', 'PRIVATE'))
                  )
                  AND (
                        :query = '%%'
                        OR LOWER(n.slug) LIKE :query
                        OR LOWER(s.slug) LIKE :query
                        OR LOWER(COALESCE(s.display_name, '')) LIKE :query
                  )
                ORDER BY recommended DESC, LOWER(COALESCE(s.display_name, s.slug)), sv.published_at DESC, sv.id DESC
                LIMIT :size
                """, parameters, (resultSet, rowNumber) -> new SkillSuiteMemberCandidateResponse(
                resultSet.getLong("skill_id"),
                resultSet.getLong("skill_version_id"),
                resultSet.getString("namespace_slug"),
                resultSet.getString("skill_slug"),
                resultSet.getString("display_name"),
                resultSet.getString("version"),
                SkillVisibility.valueOf(resultSet.getString("visibility")),
                resultSet.getBoolean("recommended")));
    }

    private List<Long> nonEmpty(List<Long> values) {
        return values.isEmpty() ? List.of(-1L) : values;
    }
}
