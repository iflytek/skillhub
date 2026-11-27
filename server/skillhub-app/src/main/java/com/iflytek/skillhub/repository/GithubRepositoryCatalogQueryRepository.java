package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.dto.GithubRepositoryCatalogResponse.GithubCatalogRepositoryDetail;
import com.iflytek.skillhub.dto.GithubRepositoryCatalogResponse.GithubCatalogMember;
import com.iflytek.skillhub.dto.GithubRepositoryCatalogResponse.GithubCatalogRepositoryPage;
import com.iflytek.skillhub.dto.GithubRepositoryCatalogResponse.RepositorySummary;
import com.iflytek.skillhub.dto.GithubRepositoryInstallPlanResponse;
import com.iflytek.skillhub.domain.skill.SkillFingerprint;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
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
                       version.id AS version_id, version.version, version.github_sync_revision
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
        if (!items.isEmpty()) {
            var previewParams = Map.of("members", nonEmpty(memberNamespaceIds),
                    "repositoryIds", items.stream().map(item -> Long.parseLong(item.repositoryId())).toList());
            var previews = new java.util.HashMap<String, java.util.List<GithubCatalogMember>>();
            jdbc.query(VISIBLE_MEMBERS + """
                    , ranked AS (
                        SELECT *, row_number() OVER (PARTITION BY repository_id ORDER BY slug, id) AS position
                        FROM visible_members WHERE repository_id IN (:repositoryIds)
                    )
                    SELECT * FROM ranked WHERE position <= 3 ORDER BY repository_id, position
                    """, previewParams, row -> {
                previews.computeIfAbsent(row.getString("repository_id"), key -> new java.util.ArrayList<>())
                        .add(new GithubCatalogMember(row.getLong("id"), row.getString("slug"),
                                row.getString("display_name"), row.getString("summary"),
                                row.getString("version"), row.getObject("github_sync_revision", Integer.class)));
            });
            items = items.stream().map(item -> new RepositorySummary(item.repositoryId(), item.owner(),
                    item.name(), item.memberCount(), previews.getOrDefault(item.repositoryId(), java.util.List.of()))).toList();
        }
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

    /** Current published pointers, deliberately independent of catalog pages and complete snapshots. */
    public Optional<GithubRepositoryInstallPlanResponse> installPlan(long repositoryId, Set<Long> memberNamespaceIds) {
        var params = Map.of("members", nonEmpty(memberNamespaceIds), "repositoryId", repositoryId);
        var members = jdbc.query(VISIBLE_MEMBERS + """
                SELECT * FROM visible_members WHERE repository_id = :repositoryId
                ORDER BY slug, id LIMIT 1001
                """, params, (row, index) -> new InstallMember(
                row.getString("owner_login"), row.getString("repository_name"),
                row.getLong("id"), row.getString("slug"), row.getString("display_name"),
                row.getString("version"), row.getLong("version_id")));
        if (members.isEmpty()) return Optional.empty();
        if (members.size() > 1000) throw new DomainBadRequestException("error.githubSync.installPlan.tooLarge", 1000);

        // Batch file metadata avoids one resolve query per member. The surrounding read-only
        // repeatable-read transaction binds membership, version pointers and hashes together.
        var hashes = new java.util.HashMap<Long, java.util.List<SkillFingerprint.FileHash>>();
        jdbc.query("SELECT version_id, file_path, sha256 FROM skill_file WHERE version_id IN (:versions)",
                Map.of("versions", members.stream().map(InstallMember::versionId).toList()), row -> {
                    hashes.computeIfAbsent(row.getLong("version_id"), key -> new java.util.ArrayList<>())
                            .add(new SkillFingerprint.FileHash(row.getString("file_path"), row.getString("sha256")));
                });
        var first = members.getFirst();
        return Optional.of(new GithubRepositoryInstallPlanResponse(Long.toString(repositoryId), first.owner(), first.name(),
                members.stream().map(member -> new GithubRepositoryInstallPlanResponse.Member(
                        member.skillId(), "github", member.slug(), member.displayName(), member.version(), member.versionId(),
                        SkillFingerprint.compute(hashes.getOrDefault(member.versionId(), java.util.List.of())),
                        "/api/cli/v1/skills/github/" + encodeSegment(member.slug()) + "/versions/"
                                + encodeSegment(member.version()) + "/download")).toList()));
    }

    private record InstallMember(String owner, String name, long skillId, String slug,
                                 String displayName, String version, long versionId) {}

    private static String encodeSegment(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static Set<Long> nonEmpty(Set<Long> members) {
        return members.isEmpty() ? Set.of(-1L) : members;
    }

    private static RepositorySummary readRepository(ResultSet row)
            throws SQLException {
        return new RepositorySummary(
                row.getString("repository_id"), row.getString("owner_login"),
                row.getString("repository_name"), row.getLong("member_count"), java.util.List.of());
    }
}
