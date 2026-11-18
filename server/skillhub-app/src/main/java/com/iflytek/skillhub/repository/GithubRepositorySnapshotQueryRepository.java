package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.dto.GithubRepositoryCatalogResponse.GithubCatalogMember;
import com.iflytek.skillhub.dto.GithubRepositoryCatalogResponse.GithubRepositorySnapshot;
import com.iflytek.skillhub.dto.GithubRepositoryCatalogResponse.GithubRepositorySnapshotChange;
import com.iflytek.skillhub.dto.GithubRepositoryCatalogResponse.GithubRepositorySnapshotComparison;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reassesses the captured members against current publication state and requester access. */
@Repository
public class GithubRepositorySnapshotQueryRepository {
    private static final String SNAPSHOT_MEMBERS = """
            WITH snapshots AS (
                SELECT snapshot.id, snapshot.commit_sha, snapshot.captured_at,
                       snapshot.ignore_patterns = source.ignore_patterns AS current_rules
                FROM github_repository_snapshot snapshot
                JOIN github_source_binding source ON source.id = snapshot.source_id
                JOIN namespace ns ON ns.id = source.namespace_id
                WHERE source.repository_id = :repositoryId AND ns.slug = 'github' AND ns.status <> 'ARCHIVED'
                  AND (source.visibility = 'PUBLIC'
                       OR (source.visibility = 'NAMESPACE_ONLY' AND ns.id IN (:members)))
            ), evaluated AS (
                SELECT snapshot.id AS snapshot_id, member.skill_path, skill.id, skill.slug,
                       COALESCE(NULLIF(skill.display_name, ''), skill.slug) AS display_name,
                       skill.summary, version.id AS version_id, version.version, version.github_sync_revision,
                       CASE
                         WHEN skill.id IS NOT NULL AND (skill.hidden OR NOT (
                                  skill.visibility = 'PUBLIC'
                                  OR (skill.visibility = 'NAMESPACE_ONLY' AND skill.namespace_id IN (:members))))
                           THEN 'INACCESSIBLE'
                         WHEN fixed.status = 'APPROVED' AND skill.status = 'ACTIVE' AND NOT skill.hidden
                              AND skill.github_sync_managed AND version.status = 'PUBLISHED'
                              AND version.download_ready AND version.yanked_at IS NULL
                              AND (skill.visibility = 'PUBLIC'
                                   OR (skill.visibility = 'NAMESPACE_ONLY' AND skill.namespace_id IN (:members)))
                           THEN 'AVAILABLE'
                         WHEN fixed.status IN ('RECEIVED', 'VALIDATING', 'RETRY_WAIT', 'VALIDATED', 'READY_FOR_REVIEW')
                              AND (skill.id IS NULL OR (
                                  skill.status = 'ACTIVE' AND NOT skill.hidden
                                  AND (skill.visibility = 'PUBLIC'
                                       OR (skill.visibility = 'NAMESPACE_ONLY' AND skill.namespace_id IN (:members)))))
                           THEN 'PENDING'
                         ELSE 'UNAVAILABLE'
                       END AS state
                FROM snapshots snapshot
                JOIN github_repository_snapshot_member member ON member.snapshot_id = snapshot.id
                JOIN github_sync_package_ingestion acquired ON acquired.id = member.ingestion_id
                LEFT JOIN github_sync_package_ingestion fixed
                  ON fixed.id = CASE WHEN acquired.status = 'NO_CHANGE'
                        THEN acquired.reused_version_ingestion_id ELSE acquired.id END
                 AND fixed.namespace_id = acquired.namespace_id AND fixed.repository_id = acquired.repository_id
                LEFT JOIN skill_version version
                  ON version.id = fixed.published_version_id AND version.github_sync_ingestion_id = fixed.id
                LEFT JOIN skill ON skill.id = version.skill_id AND skill.namespace_id = fixed.namespace_id
            )
            """;

    private static final String AVAILABLE_MATCH = """
             WHERE snapshot_id = :snapshotId AND state = 'AVAILABLE'
               AND (strpos(LOWER(slug), LOWER(:query)) > 0 OR
                    strpos(LOWER(display_name), LOWER(:query)) > 0 OR
                    strpos(LOWER(COALESCE(summary, '')), LOWER(:query)) > 0)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public GithubRepositorySnapshotQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<GithubRepositorySnapshot> list(long repositoryId, Set<Long> members, Long requestedId) {
        return jdbc.query(SNAPSHOT_MEMBERS + """
                , summary AS (
                    SELECT snapshot.id, snapshot.commit_sha, snapshot.captured_at, COUNT(*) FILTER (WHERE evaluated.state <> 'INACCESSIBLE') AS member_count,
                           COUNT(*) FILTER (WHERE evaluated.state = 'AVAILABLE') AS available_count,
                           CASE
                             WHEN NOT snapshot.current_rules OR bool_or(evaluated.state IN ('UNAVAILABLE', 'INACCESSIBLE'))
                                  OR COUNT(evaluated.id) <> COUNT(DISTINCT evaluated.id)
                               THEN 'UNAVAILABLE'
                             WHEN bool_or(evaluated.state = 'PENDING') THEN 'PENDING'
                             ELSE 'AVAILABLE'
                           END AS status
                    FROM snapshots snapshot
                    JOIN evaluated ON evaluated.snapshot_id = snapshot.id
                    GROUP BY snapshot.id, snapshot.commit_sha, snapshot.captured_at, snapshot.current_rules
                ), recent AS (
                    SELECT id FROM summary ORDER BY captured_at DESC, id DESC LIMIT 12
                ), previous_available AS (
                    SELECT id FROM summary WHERE status = 'AVAILABLE'
                    ORDER BY captured_at DESC, id DESC LIMIT 2
                )
                SELECT * FROM summary
                WHERE id IN (SELECT id FROM recent UNION SELECT id FROM previous_available) OR id = :requestedId
                ORDER BY captured_at DESC, id DESC
                """, Map.of("repositoryId", repositoryId, "members", members,
                             "requestedId", requestedId == null ? -1L : requestedId),
                (row, index) -> new GithubRepositorySnapshot(
                        row.getString("id"), row.getString("commit_sha"),
                        row.getTimestamp("captured_at").toInstant(), row.getString("status"),
                        row.getLong("member_count"), row.getLong("available_count")));
    }

    SnapshotMembers members(long repositoryId, long snapshotId, Set<Long> members, String query, int page, int size) {
        var params = Map.of("repositoryId", repositoryId, "snapshotId", snapshotId, "members", members,
                "query", query, "size", size, "offset", (long) page * size);
        long total = jdbc.queryForObject(SNAPSHOT_MEMBERS + "SELECT COUNT(*) FROM evaluated" + AVAILABLE_MATCH,
                params, Long.class);
        var items = jdbc.query(SNAPSHOT_MEMBERS + """
                SELECT id, slug, display_name, summary, version, github_sync_revision FROM evaluated
                """ + AVAILABLE_MATCH + """
                ORDER BY slug, skill_path
                LIMIT :size OFFSET :offset
                """, params, (row, index) -> new GithubCatalogMember(row.getLong("id"), row.getString("slug"),
                row.getString("display_name"), row.getString("summary"), row.getString("version"),
                row.getObject("github_sync_revision", Integer.class)));
        return new SnapshotMembers(items, total);
    }

    GithubRepositorySnapshotComparison compare(long repositoryId, GithubRepositorySnapshot current,
                                               GithubRepositorySnapshot base, Set<Long> members) {
        return jdbc.query(SNAPSHOT_MEMBERS + """
                , eligible AS (
                    SELECT snapshot.id FROM snapshots snapshot
                    JOIN evaluated member ON member.snapshot_id = snapshot.id
                    WHERE snapshot.id IN (:currentId, :baseId) AND snapshot.current_rules
                    GROUP BY snapshot.id
                    HAVING bool_and(member.state = 'AVAILABLE') AND COUNT(*) = COUNT(DISTINCT member.id)
                ), previous_members AS (
                    SELECT id, slug, version_id, version FROM evaluated WHERE snapshot_id = :baseId
                ), current_members AS (
                    SELECT id, slug, version_id, version FROM evaluated WHERE snapshot_id = :currentId
                )
                SELECT COALESCE(new_member.slug, old_member.slug) AS slug,
                       CASE WHEN old_member.id IS NULL THEN 'ADDED'
                            WHEN new_member.id IS NULL THEN 'REMOVED'
                            ELSE 'UPDATED' END AS kind,
                       old_member.version AS previous_version, new_member.version AS current_version,
                       COUNT(*) OVER() AS total
                FROM previous_members old_member
                FULL JOIN current_members new_member ON new_member.id = old_member.id
                WHERE (SELECT COUNT(*) FROM eligible) = 2
                  AND new_member.version_id IS DISTINCT FROM old_member.version_id
                ORDER BY kind, slug
                LIMIT 20
                """, Map.of("repositoryId", repositoryId, "members", members,
                             "currentId", Long.parseLong(current.id()), "baseId", Long.parseLong(base.id())), rows -> {
            long total = 0;
            var changes = new java.util.ArrayList<GithubRepositorySnapshotChange>();
            while (rows.next()) {
                total = rows.getLong("total");
                changes.add(new GithubRepositorySnapshotChange(rows.getString("slug"), rows.getString("kind"),
                        rows.getString("previous_version"), rows.getString("current_version")));
            }
            return new GithubRepositorySnapshotComparison(base.commit(), total, changes);
        });
    }

    public List<ArchiveMember> archiveMembers(long repositoryId, long snapshotId, Set<Long> members, int limit) {
        return jdbc.query(SNAPSHOT_MEMBERS + """
                SELECT evaluated.id, evaluated.version_id, evaluated.slug, evaluated.version, snapshot.commit_sha
                FROM evaluated
                JOIN snapshots snapshot ON snapshot.id = evaluated.snapshot_id
                WHERE snapshot.id = :snapshotId AND snapshot.current_rules
                  AND NOT EXISTS (
                    SELECT 1 FROM evaluated member
                    WHERE member.snapshot_id = snapshot.id AND member.state <> 'AVAILABLE')
                  AND NOT EXISTS (
                    SELECT 1 FROM evaluated member
                    WHERE member.snapshot_id = snapshot.id
                    GROUP BY member.id HAVING COUNT(*) > 1)
                ORDER BY evaluated.skill_path LIMIT :limit
                """, Map.of("repositoryId", repositoryId, "snapshotId", snapshotId,
                             "limit", limit, "members", members.isEmpty() ? Set.of(-1L) : members),
                (row, index) -> new ArchiveMember(row.getLong("id"), row.getLong("version_id"),
                        row.getString("slug"), row.getString("version"), row.getString("commit_sha")));
    }

    public record ArchiveMember(long skillId, long versionId, String slug, String version, String commit) {}

    record SnapshotMembers(List<GithubCatalogMember> items, long total) {}
}
