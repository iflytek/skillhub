package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.dto.GithubReviewGroupResponse;
import com.iflytek.skillhub.dto.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Fixed snapshot membership and the existing queue predicate share authorization before paging. */
@Repository
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class GithubReviewGroupQueryRepository {
    private static final String JOINS = """
            JOIN github_sync_package_ingestion ingestion ON ingestion.id = task.github_sync_ingestion_id
            JOIN github_repository_snapshot_member member ON member.ingestion_id = ingestion.id AND member.skill_path = ingestion.skill_path
            JOIN github_repository_snapshot snapshot ON snapshot.id = member.snapshot_id
                 AND snapshot.acquisition_task_id = ingestion.task_id AND snapshot.commit_sha = ingestion.commit_sha
            JOIN github_source_binding source ON source.id = snapshot.source_id
                 AND source.namespace_id = task.namespace_id AND source.repository_id = ingestion.repository_id
            """ + JpaReviewQueueQueryRepository.CURRENT_SCAN;
    private static final String SAFE = "scan.verdict = 'SAFE' AND scan.is_safe AND scan.scanned_at IS NOT NULL AND scan.failure_reason IS NULL";
    private final NamedParameterJdbcTemplate jdbc;

    public GithubReviewGroupQueryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private JpaReviewQueueQueryRepository.Condition condition(ReviewQueueQueryRepository.Filter filter,
            Set<Long> scopes, Long snapshotId) {
        var base = JpaReviewQueueQueryRepository.condition(new ReviewQueueQueryRepository.Filter(
                filter.status(), filter.namespaceId(), "", filter.reviewKind(), filter.originType()), true, Set.of());
        Map<String, Object> params = new HashMap<>(base.parameters());
        params.put("scopes", scopes);
        String where = base.sql() + " AND ns.id IN (:scopes) AND task.origin_type = 'GITHUB_SYNC' AND task.subject_type = 'SKILL_VERSION'";
        if (!filter.query().isBlank()) {
            String value = filter.query().strip().toLowerCase(java.util.Locale.ROOT);
            if (value.startsWith("@")) value = value.substring(1);
            params.put("query", "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
            where += " AND (LOWER(" + JpaReviewQueueQueryRepository.NAME + ") LIKE :query ESCAPE '!'"
                    + " OR LOWER(CONCAT(ns.slug, '/', skill.slug)) LIKE :query ESCAPE '!'"
                    + " OR LOWER(author.display_name) LIKE :query ESCAPE '!'"
                    + " OR LOWER(task.submitted_by) LIKE :query ESCAPE '!'"
                    + " OR LOWER(CONCAT(source.owner_login, '/', source.repository_name)) LIKE :query ESCAPE '!')";
        }
        if (snapshotId != null) { where += " AND snapshot.id = :snapshotId"; params.put("snapshotId", snapshotId); }
        return new JpaReviewQueueQueryRepository.Condition(where, params);
    }

    public PageResponse<GithubReviewGroupResponse> list(ReviewQueueQueryRepository.Filter filter,
            Set<Long> scopes, int page, int size, boolean ascending) {
        return list(filter, scopes, page, size, ascending, null);
    }

    public PageResponse<GithubReviewGroupResponse> list(ReviewQueueQueryRepository.Filter filter,
            Set<Long> scopes, int page, int size, boolean ascending, Long snapshotId) {
        if (scopes.isEmpty()) return new PageResponse<>(List.of(), 0, page, size);
        var c = condition(filter, scopes, snapshotId);
        String from = JpaReviewQueueQueryRepository.FROM + JOINS + c.sql();
        long total = jdbc.queryForObject("SELECT COUNT(DISTINCT snapshot.id) " + from, c.parameters(), Long.class);
        var params = new HashMap<>(c.parameters()); params.put("limit", size); params.put("offset", (long) page * size);
        String direction = ascending ? "ASC" : "DESC";
        var rows = jdbc.query("""
                SELECT snapshot.id, ns.id AS namespace_id, ns.slug, source.repository_id,
                       source.owner_login, source.repository_name, snapshot.commit_sha, snapshot.captured_at,
                       COUNT(*) AS total,
                """ + " COUNT(*) FILTER (WHERE " + SAFE + ") AS scan_passed, "
                + " COUNT(*) FILTER (WHERE scan.scanned_at IS NULL OR scan.failure_reason IS NOT NULL) AS scan_waiting, "
                + " COUNT(*) FILTER (WHERE scan.scanned_at IS NOT NULL AND scan.failure_reason IS NULL AND NOT COALESCE((" + SAFE + "), FALSE)) AS scan_blocked "
                + from + " GROUP BY snapshot.id, ns.id, ns.slug, source.repository_id, source.owner_login, source.repository_name "
                + " ORDER BY snapshot.captured_at " + direction + ", snapshot.id " + direction + " LIMIT :limit OFFSET :offset",
                params, (rs, index) -> new GithubReviewGroupResponse(rs.getLong("id"), rs.getLong("namespace_id"),
                        rs.getString("slug"), rs.getLong("repository_id"), rs.getString("owner_login"),
                        rs.getString("repository_name"), rs.getString("commit_sha"), rs.getTimestamp("captured_at").toInstant(),
                        rs.getLong("total"), rs.getLong("scan_passed"), rs.getLong("scan_waiting"), rs.getLong("scan_blocked")));
        return new PageResponse<>(rows, total, page, size);
    }

    public record Member(long reviewId, String scanStatus, String displayName, String summary, String reviewKind) {}

    public PageResponse<Member> members(ReviewQueueQueryRepository.Filter filter, Set<Long> scopes,
            long snapshotId, int page, int size, boolean ascending) {
        if (scopes.isEmpty()) return new PageResponse<>(List.of(), 0, page, size);
        var c = condition(filter, scopes, snapshotId);
        String from = JpaReviewQueueQueryRepository.FROM + JOINS + c.sql();
        long total = jdbc.queryForObject("SELECT COUNT(*) " + from, c.parameters(), Long.class);
        var params = new HashMap<>(c.parameters()); params.put("limit", size); params.put("offset", (long) page * size);
        String direction = ascending ? "ASC" : "DESC";
        List<Member> ids = jdbc.query("SELECT task.id, " + JpaReviewQueueQueryRepository.NAME + " AS display_name, " + JpaReviewQueueQueryRepository.SUMMARY + " AS summary, " + JpaReviewQueueQueryRepository.KIND + " AS review_kind, CASE WHEN " + SAFE + " THEN 'PASSED' WHEN scan.scanned_at IS NULL OR scan.failure_reason IS NOT NULL THEN 'WAITING' ELSE 'BLOCKED' END AS scan_status " + from + " ORDER BY task.submitted_at " + direction
                + ", task.id " + direction + " LIMIT :limit OFFSET :offset", params, (rs, index) -> new Member(rs.getLong("id"), rs.getString("scan_status"), rs.getString("display_name"), rs.getString("summary"), rs.getString("review_kind")));
        return new PageResponse<>(ids, total, page, size);
    }
}
