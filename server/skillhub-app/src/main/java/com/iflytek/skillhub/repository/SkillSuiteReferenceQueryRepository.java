package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillSuiteReferenceResponse;
import com.iflytek.skillhub.dto.SkillSuiteSiblingMemberResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Skill-detail read model for current Suite membership references.
 *
 * <p>Only each Suite's latest published snapshot participates. Suite visibility and sibling Skill
 * visibility are evaluated in bounded collection queries so callers cannot infer private
 * coordinates and the projection does not perform an N+1 lookup.</p>
 */
@Repository
public class SkillSuiteReferenceQueryRepository {

    public static final int MAX_PAGE_SIZE = 20;
    private static final int MAX_VISIBLE_SIBLINGS = 8;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SkillSuiteReferenceQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Legacy entry-only projection retained for older clients. */
    @Transactional(readOnly = true)
    public List<SkillSuiteReferenceResponse> findVisibleEntryReferences(
            Long skillId,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        return findVisibleMemberships(
                skillId, userId, namespaceRoles, platformRoles, 0, MAX_PAGE_SIZE).items().stream()
                .filter(SkillSuiteReferenceResponse::currentSkillEntry)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<SkillSuiteReferenceResponse> findVisibleMemberships(
            Long skillId,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles,
            int page,
            int size
    ) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        MapSqlParameterSource parameters = viewerParameters(
                skillId, userId, namespaceRoles, platformRoles)
                .addValue("limit", boundedSize)
                .addValue("offset", boundedPage * boundedSize);

        List<SuiteRow> suites = jdbcTemplate.query("""
                SELECT suite.id,
                       version.id AS suite_version_id,
                       suite_namespace.slug AS namespace_slug,
                       suite.slug,
                       version.display_name,
                       version.version,
                       current_member.entry AS current_skill_entry,
                       COUNT(all_members.id) AS member_count,
                       COUNT(*) OVER() AS total_count
                FROM skill_suite suite
                JOIN namespace suite_namespace ON suite_namespace.id = suite.namespace_id
                JOIN skill_suite_version version ON version.id = suite.latest_version_id
                JOIN skill_suite_version_member current_member
                  ON current_member.suite_version_id = version.id
                 AND current_member.skill_id = :skillId
                JOIN skill_suite_version_member all_members
                  ON all_members.suite_version_id = version.id
                WHERE %s
                GROUP BY suite.id, version.id, suite_namespace.slug, suite.slug,
                         version.display_name, version.version, current_member.entry
                ORDER BY current_member.entry DESC, LOWER(version.display_name), suite.id
                LIMIT :limit OFFSET :offset
                """.formatted(visibleSuitePredicate()), parameters, (resultSet, rowNumber) -> new SuiteRow(
                resultSet.getLong("id"),
                resultSet.getLong("suite_version_id"),
                resultSet.getString("namespace_slug"),
                resultSet.getString("slug"),
                resultSet.getString("display_name"),
                resultSet.getString("version"),
                resultSet.getBoolean("current_skill_entry"),
                resultSet.getInt("member_count"),
                resultSet.getLong("total_count")));

        if (suites.isEmpty()) {
            long total = boundedPage == 0 ? 0 : countVisibleMemberships(parameters);
            return new PageResponse<>(List.of(), total, boundedPage, boundedSize);
        }

        Map<Long, List<MemberRow>> membersBySuiteVersion = loadMembers(
                suites.stream().map(SuiteRow::suiteVersionId).toList(),
                skillId, userId, namespaceRoles, platformRoles);
        List<SkillSuiteReferenceResponse> items = suites.stream()
                .map(suite -> toResponse(suite, membersBySuiteVersion.getOrDefault(
                        suite.suiteVersionId(), List.of())))
                .toList();
        return new PageResponse<>(items, suites.getFirst().totalCount(), boundedPage, boundedSize);
    }

    private long countVisibleMemberships(MapSqlParameterSource parameters) {
        Long counted = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM skill_suite suite
                JOIN namespace suite_namespace ON suite_namespace.id = suite.namespace_id
                JOIN skill_suite_version version ON version.id = suite.latest_version_id
                JOIN skill_suite_version_member current_member
                  ON current_member.suite_version_id = version.id
                 AND current_member.skill_id = :skillId
                WHERE %s
                """.formatted(visibleSuitePredicate()), parameters, Long.class);
        return counted == null ? 0 : counted;
    }

    private Map<Long, List<MemberRow>> loadMembers(
            List<Long> suiteVersionIds,
            Long currentSkillId,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        MapSqlParameterSource parameters = viewerParameters(
                currentSkillId, userId, namespaceRoles, platformRoles)
                .addValue("suiteVersionIds", suiteVersionIds);
        List<MemberRow> rows = jdbcTemplate.query("""
                SELECT member.suite_version_id,
                       member.position,
                       member.entry,
                       skill.id AS skill_id,
                       skill_namespace.slug AS namespace_slug,
                       skill.slug,
                       COALESCE(skill.display_name, skill.slug) AS display_name,
                       skill_version.version,
                       CASE WHEN skill.id IS NULL
                                  OR skill_version.id IS NULL
                                  OR skill_namespace.id IS NULL THEN FALSE
                            WHEN :superAdmin = TRUE THEN TRUE
                            WHEN skill.hidden = TRUE THEN (
                                 skill.owner_id = :userId
                                 OR skill.namespace_id IN (:adminNamespaceIds))
                            WHEN skill.latest_version_id IS NULL THEN skill.owner_id = :userId
                            WHEN skill.visibility = 'PUBLIC' THEN TRUE
                            WHEN skill.visibility = 'NAMESPACE_ONLY' THEN
                                 skill.namespace_id IN (:memberNamespaceIds)
                            WHEN skill.visibility = 'PRIVATE' THEN (
                                 skill.owner_id = :userId
                                 OR skill.namespace_id IN (:adminNamespaceIds))
                            ELSE FALSE
                       END AS visible,
                       CASE WHEN skill.id IS NOT NULL
                                  AND skill_version.id IS NOT NULL
                                  AND skill_namespace.status = 'ACTIVE'
                                  AND skill.status = 'ACTIVE'
                                  AND skill.hidden = FALSE
                                  AND skill_version.status = 'PUBLISHED'
                                  AND skill_version.download_ready = TRUE
                                  AND skill_version.yanked_at IS NULL
                            THEN TRUE ELSE FALSE
                       END AS available
                FROM skill_suite_version_member member
                LEFT JOIN skill ON skill.id = member.skill_id
                LEFT JOIN skill_version
                  ON skill_version.id = member.skill_version_id
                 AND skill_version.skill_id = member.skill_id
                LEFT JOIN namespace skill_namespace ON skill_namespace.id = skill.namespace_id
                WHERE member.suite_version_id IN (:suiteVersionIds)
                  AND (member.skill_id IS NULL OR member.skill_id <> :skillId)
                ORDER BY member.suite_version_id, member.position
                """, parameters, (resultSet, rowNumber) -> new MemberRow(
                resultSet.getLong("suite_version_id"),
                resultSet.getObject("skill_id", Long.class),
                resultSet.getString("namespace_slug"),
                resultSet.getString("slug"),
                resultSet.getString("display_name"),
                resultSet.getString("version"),
                resultSet.getBoolean("entry"),
                resultSet.getBoolean("visible"),
                resultSet.getBoolean("available")));
        Map<Long, List<MemberRow>> grouped = new LinkedHashMap<>();
        rows.forEach(row -> grouped.computeIfAbsent(row.suiteVersionId(), ignored -> new ArrayList<>()).add(row));
        return grouped;
    }

    private SkillSuiteReferenceResponse toResponse(SuiteRow suite, List<MemberRow> members) {
        List<MemberRow> visible = members.stream().filter(MemberRow::visible).toList();
        List<SkillSuiteSiblingMemberResponse> summaries = visible.stream()
                .limit(MAX_VISIBLE_SIBLINGS)
                .map(member -> new SkillSuiteSiblingMemberResponse(
                        member.skillId(), member.namespace(), member.slug(), member.displayName(),
                        member.version(), member.entry(), member.available()))
                .toList();
        int restrictedCount = (int) members.stream().filter(member -> !member.visible()).count();
        int omittedVisibleCount = Math.max(0, visible.size() - summaries.size());
        return new SkillSuiteReferenceResponse(
                suite.suiteId(), suite.namespace(), suite.slug(), suite.displayName(), suite.version(),
                suite.memberCount(), suite.currentSkillEntry(), summaries,
                restrictedCount, omittedVisibleCount);
    }

    private MapSqlParameterSource viewerParameters(
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
        return new MapSqlParameterSource()
                .addValue("skillId", skillId)
                .addValue("userId", userId)
                .addValue("memberNamespaceIds", nonEmpty(memberNamespaceIds))
                .addValue("adminNamespaceIds", nonEmpty(adminNamespaceIds))
                .addValue("authenticated", userId != null)
                .addValue("superAdmin", platformRoles.contains("SUPER_ADMIN"));
    }

    private String visibleSuitePredicate() {
        return """
                suite.status = 'ACTIVE'
                  AND suite.hidden = FALSE
                  AND suite_namespace.status = 'ACTIVE'
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
                """;
    }

    private List<Long> nonEmpty(List<Long> values) {
        return values.isEmpty() ? List.of(-1L) : values;
    }

    private record SuiteRow(
            Long suiteId,
            Long suiteVersionId,
            String namespace,
            String slug,
            String displayName,
            String version,
            boolean currentSkillEntry,
            int memberCount,
            long totalCount
    ) {
    }

    private record MemberRow(
            Long suiteVersionId,
            Long skillId,
            String namespace,
            String slug,
            String displayName,
            String version,
            boolean entry,
            boolean visible,
            boolean available
    ) {
    }
}
