package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.dto.GithubRepositoryCatalogResponse.GithubCatalogRepositoryDetail;
import com.iflytek.skillhub.dto.GithubRepositoryCatalogResponse.GithubCatalogMember;
import com.iflytek.skillhub.dto.GithubRepositoryCatalogResponse.GithubCatalogRepositoryPage;
import com.iflytek.skillhub.dto.GithubRepositoryCatalogResponse.RepositorySummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Uses one eligibility query for repository counts and member pages. */
@Repository
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class GithubRepositoryCatalogQueryRepository {
    private static final String VISIBLE_MEMBERS = """
            WITH visible_members AS (
                SELECT source.repository_id, source.owner_login, source.repository_name,
                       skill.id, skill.slug, COALESCE(NULLIF(skill.display_name, ''), skill.slug) AS display_name,
                       skill.summary,
                       version.version, version.github_sync_revision
                FROM github_source_binding source
                JOIN namespace ns ON ns.id = source.namespace_id
                JOIN github_source_skill_binding binding ON binding.source_id = source.id
                JOIN skill ON skill.id = binding.skill_id AND skill.namespace_id = ns.id
                JOIN skill_version version ON version.id = skill.latest_version_id
                                          AND version.skill_id = skill.id
                WHERE ns.slug = 'github' AND ns.status <> 'ARCHIVED'
                  AND (source.visibility = 'PUBLIC'
                       OR (source.visibility = 'NAMESPACE_ONLY' AND ns.id IN (:members)))
                  AND skill.github_sync_managed AND skill.status = 'ACTIVE' AND NOT skill.hidden
                  AND version.status = 'PUBLISHED' AND version.download_ready AND version.yanked_at IS NULL
                  AND (skill.visibility = 'PUBLIC'
                       OR (skill.visibility = 'NAMESPACE_ONLY' AND ns.id IN (:members)))
            )
            """;

    private static final String REPOSITORIES = """
            SELECT repository_id, owner_login, repository_name, COUNT(*) AS member_count
            FROM visible_members
            """;

    private static final String REPOSITORY_MATCH = """
             WHERE strpos(LOWER(owner_login || '/' || repository_name), LOWER(:query)) > 0
            """;

    private static final String MEMBER_MATCH = """
             WHERE repository_id = :repositoryId AND (
               strpos(LOWER(slug), LOWER(:query)) > 0 OR
               strpos(LOWER(display_name), LOWER(:query)) > 0 OR
               strpos(LOWER(COALESCE(summary, '')), LOWER(:query)) > 0
             )
            """;

    private static final String GROUP_BY = " GROUP BY repository_id, owner_login, repository_name";

    private final NamedParameterJdbcTemplate jdbc;
    private final GithubRepositorySnapshotQueryRepository snapshots;

    public GithubRepositoryCatalogQueryRepository(NamedParameterJdbcTemplate jdbc,
                                                  GithubRepositorySnapshotQueryRepository snapshots) {
        this.jdbc = jdbc;
        this.snapshots = snapshots;
    }

    public GithubCatalogRepositoryPage list(String query, Set<Long> memberNamespaceIds, int page, int size) {
        var params = Map.of("members", nonEmpty(memberNamespaceIds), "query", query, "size", size, "offset", (long) page * size);
        var total = jdbc.queryForObject(VISIBLE_MEMBERS + "SELECT COUNT(DISTINCT repository_id) FROM visible_members" + REPOSITORY_MATCH,
                params, Long.class);
        var items = jdbc.query(VISIBLE_MEMBERS + REPOSITORIES + REPOSITORY_MATCH + GROUP_BY + """
                 ORDER BY LOWER(owner_login), LOWER(repository_name), repository_id
                 LIMIT :size OFFSET :offset
                """, params, (row, index) -> readRepository(row));
        return new GithubCatalogRepositoryPage(items, total, page, size);
    }

    public Optional<GithubCatalogRepositoryDetail> detail(long repositoryId, Long snapshotId, String query, Set<Long> memberNamespaceIds, int page, int size) {
        var params = Map.of("members", nonEmpty(memberNamespaceIds), "repositoryId", repositoryId,
                "query", query, "size", size, "offset", (long) page * size);
        var repository = jdbc.query(VISIBLE_MEMBERS + REPOSITORIES + " WHERE repository_id = :repositoryId" + GROUP_BY,
                params, (row, index) -> readRepository(row)).stream().findFirst();
        return repository.flatMap(header -> {
            var memberIds = nonEmpty(memberNamespaceIds);
            var versions = snapshots.list(repositoryId, memberIds, snapshotId);
            var selected = snapshotId == null
                    ? versions.stream().filter(v -> "AVAILABLE".equals(v.status())).findFirst()
                        .or(() -> versions.stream().findFirst())
                    : versions.stream().filter(v -> Long.toString(snapshotId).equals(v.id())).findFirst();
            if (snapshotId != null && selected.isEmpty()) {
                return Optional.empty();
            }
            if (selected.isPresent()) {
                var version = selected.get();
                var pageMembers = snapshots.members(repositoryId, Long.parseLong(version.id()), memberIds, query, page, size);
                var previous = versions.stream().skip(versions.indexOf(version) + 1L)
                        .filter(v -> "AVAILABLE".equals(v.status())).findFirst();
                var comparison = "AVAILABLE".equals(version.status())
                        ? previous.map(base -> snapshots.compare(repositoryId, version, base, memberIds)).orElse(null)
                        : null;
                return Optional.of(new GithubCatalogRepositoryDetail(
                        header, pageMembers.items(), pageMembers.total(), page, size, versions, version.id(), comparison));
            }
            long total = query.isEmpty() ? header.memberCount() : jdbc.queryForObject(
                    VISIBLE_MEMBERS + "SELECT COUNT(*) FROM visible_members" + MEMBER_MATCH, params, Long.class);
            var members = jdbc.query(VISIBLE_MEMBERS + """
                    SELECT id, slug, display_name, summary, version, github_sync_revision
                    FROM visible_members
                    """ + MEMBER_MATCH + """
                    ORDER BY slug, id
                    LIMIT :size OFFSET :offset
                    """, params, (row, index) -> new GithubCatalogMember(
                    row.getLong("id"), row.getString("slug"), row.getString("display_name"),
                    row.getString("summary"), row.getString("version"),
                    row.getObject("github_sync_revision", Integer.class)));
            return Optional.of(new GithubCatalogRepositoryDetail(header, members, total, page, size, versions, null, null));
        });
    }

    private static Set<Long> nonEmpty(Set<Long> members) {
        return members.isEmpty() ? Set.of(-1L) : members;
    }

    private static RepositorySummary readRepository(ResultSet row)
            throws SQLException {
        return new RepositorySummary(
                row.getString("repository_id"), row.getString("owner_login"),
                row.getString("repository_name"), row.getLong("member_count"));
    }
}
