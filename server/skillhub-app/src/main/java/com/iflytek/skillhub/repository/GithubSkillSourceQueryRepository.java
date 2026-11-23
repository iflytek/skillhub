package com.iflytek.skillhub.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.dto.GithubSkillSourceResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class GithubSkillSourceQueryRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public GithubSkillSourceQueryRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * Call only after SkillQueryService has authorized the viewer for this published version.
     * Returns empty for versions that did not come from GitHub sync.
     */
    public Optional<GithubSkillSourceResponse> findPublishedVersion(long versionId) {
        return jdbc.query("""
                SELECT COALESCE(source.owner_login, task.snapshot_json ->> 'owner') AS owner_login,
                       COALESCE(source.repository_name, task.snapshot_json ->> 'name') AS repository_name,
                       ingestion.skill_path, ingestion.commit_sha, version.github_sync_revision,
                       ingestion.license_evidence_json::text AS evidence_json,
                       ingestion.source_tag_json::text AS source_tag_json,
                       version.parsed_metadata_json ->> 'upstreamVersion' AS declared_version
                FROM skill_version version
                JOIN github_sync_package_ingestion ingestion
                  ON ingestion.id = version.github_sync_ingestion_id
                 AND ingestion.published_version_id = version.id
                JOIN github_sync_task task ON task.id = ingestion.task_id
                LEFT JOIN github_source_binding source
                  ON source.namespace_id = ingestion.namespace_id
                 AND source.repository_id = ingestion.repository_id
                WHERE version.id = ? AND version.status = 'PUBLISHED'
                """, result -> {
            if (!result.next()) {
                return Optional.empty();
            }
            JsonNode evidence = evidence(result.getString("evidence_json"));
            return Optional.of(new GithubSkillSourceResponse(
                    result.getString("owner_login"), result.getString("repository_name"),
                    result.getString("skill_path"), result.getString("commit_sha"),
                    result.getInt("github_sync_revision"),
                    evidence.path("status").asText("UNKNOWN"), licenseDocuments(evidence),
                    upstreamTag(result.getString("source_tag_json")), result.getString("declared_version")));
        }, versionId);
    }

    /**
     * Same as {@link #findPublishedVersion} for callers that resolve arbitrary versions
     * (version detail, CLI resolve): most versions are not synced, so a portable
     * primary-key lookup skips the provenance join for them.
     */
    public Optional<GithubSkillSourceResponse> findPublishedSyncedVersion(long versionId) {
        Boolean synced = jdbc.query("""
                SELECT github_sync_ingestion_id IS NOT NULL AS synced
                FROM skill_version WHERE id = ? AND status = 'PUBLISHED'
                """, result -> result.next() ? result.getBoolean("synced") : Boolean.FALSE, versionId);
        return Boolean.TRUE.equals(synced) ? findPublishedVersion(versionId) : Optional.empty();
    }

    private JsonNode evidence(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException exception) {
            throw new IllegalStateException("Invalid stored GitHub source evidence", exception);
        }
    }

    /** Missing provenance (older ingestions or workers) is reported as UNRECORDED, never guessed. */
    private GithubSkillSourceResponse.UpstreamTag upstreamTag(String json) {
        if (json == null) {
            return GithubSkillSourceResponse.UpstreamTag.unrecorded();
        }
        JsonNode stored = evidence(json);
        return new GithubSkillSourceResponse.UpstreamTag(
                stored.path("status").asText(GithubSkillSourceResponse.UpstreamTag.UNRECORDED),
                GithubSkillSourceResponse.UpstreamTag.REPOSITORY,
                text(stored, "tag"), text(stored, "tagCommitSha"),
                stored.hasNonNull("commitsAfterTag") ? stored.get("commitsAfterTag").asInt() : null,
                text(stored, "reason"));
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private List<GithubSkillSourceResponse.LicenseDocument> licenseDocuments(JsonNode evidence) {
        List<GithubSkillSourceResponse.LicenseDocument> documents = new ArrayList<>();
        for (JsonNode document : evidence.path("documents")) {
            documents.add(new GithubSkillSourceResponse.LicenseDocument(
                    document.path("scope").asText(), document.path("path").asText(),
                    document.path("bundled").asBoolean()));
        }
        return List.copyOf(documents);
    }
}
