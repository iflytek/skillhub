package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.SkillSuiteReferenceResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skill-detail read model for current Suite entry references.
 *
 * <p>The query starts from each Suite's latest published snapshot so historical Suite versions do
 * not look like current installation recommendations. Visibility filtering happens in SQL to avoid
 * leaking private Suite coordinates through a public Skill page.</p>
 */
@Repository
public class SkillSuiteReferenceQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SkillSuiteReferenceQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<SkillSuiteReferenceResponse> findVisibleEntryReferences(
            Long skillId,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        List<Long> memberNamespaceIds = namespaceRoles.keySet().stream().toList();
        List<Long> adminNamespaceIds = namespaceRoles.entrySet().stream()
                .filter(entry -> entry.getValue() == NamespaceRole.OWNER
                        || entry.getValue() == NamespaceRole.ADMIN)
                .map(Map.Entry::getKey)
                .toList();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("skillId", skillId)
                .addValue("userId", userId)
                .addValue("memberNamespaceIds", nonEmpty(memberNamespaceIds))
                .addValue("adminNamespaceIds", nonEmpty(adminNamespaceIds))
                .addValue("authenticated", userId != null)
                .addValue("superAdmin", platformRoles.contains("SUPER_ADMIN"));

        return jdbcTemplate.query("""
                SELECT suite.id,
                       namespace.slug AS namespace_slug,
                       suite.slug,
                       version.display_name,
                       version.version,
                       COUNT(all_members.id) AS member_count
                FROM skill_suite suite
                JOIN namespace ON namespace.id = suite.namespace_id
                JOIN skill_suite_version version ON version.id = suite.latest_version_id
                JOIN skill_suite_version_member entry_member
                  ON entry_member.suite_version_id = version.id AND entry_member.entry = TRUE
                JOIN skill_suite_version_member all_members
                  ON all_members.suite_version_id = version.id
                WHERE entry_member.skill_id = :skillId
                  AND suite.status = 'ACTIVE'
                  AND suite.hidden = FALSE
                  AND namespace.status = 'ACTIVE'
                  AND version.status = 'PUBLISHED'
                  AND (
                        :superAdmin = TRUE
                        OR version.visibility = 'PUBLIC'
                        OR (version.visibility = 'NAMESPACE_ONLY'
                            AND suite.namespace_id IN (:memberNamespaceIds))
                        OR (version.visibility = 'PRIVATE' AND (
                            suite.namespace_id IN (:adminNamespaceIds)
                            OR (:authenticated = TRUE
                                AND suite.created_by = :userId
                                AND suite.namespace_id IN (:memberNamespaceIds))
                        ))
                  )
                GROUP BY suite.id, namespace.slug, suite.slug, version.display_name, version.version
                ORDER BY LOWER(version.display_name), suite.id
                """, parameters, (resultSet, rowNumber) -> new SkillSuiteReferenceResponse(
                resultSet.getLong("id"),
                resultSet.getString("namespace_slug"),
                resultSet.getString("slug"),
                resultSet.getString("display_name"),
                resultSet.getString("version"),
                resultSet.getInt("member_count")));
    }

    private List<Long> nonEmpty(List<Long> values) {
        return values.isEmpty() ? List.of(-1L) : values;
    }
}
