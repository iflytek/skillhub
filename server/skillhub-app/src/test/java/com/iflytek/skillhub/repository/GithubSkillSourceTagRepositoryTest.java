package com.iflytek.skillhub.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.dto.GithubSkillSourceResponse;
import com.iflytek.skillhub.githubsync.GithubPackageReportResult;
import com.iflytek.skillhub.githubsync.GithubSourceTag;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncUploadRepository;
import com.iflytek.skillhub.support.PostgresTestImage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Source tags are captured once with the ingestion and projected unchanged for every published Skill. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class GithubSkillSourceTagRepositoryTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());

    private static final String COMMIT = "4ae2317ddb22b046e26455e4f9c9b3aaaf62212d";
    private static final String TAG_COMMIT = "d1478fcb140db0b93c0c8df17bcfd311844749d0";
    private static final String LEASE_HASH = "a".repeat(64);
    private static final String DIGEST = "c".repeat(64);

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired private JdbcTemplate jdbc;
    private final Map<String, UUID[]> uploadIds = new HashMap<>();

    @Test
    void reportedTagIsStoredOnceAndSharedByEverySkillOfTheRepository() {
        long namespaceId = platformNamespaceId();
        UUID taskId = leasedTask(namespaceId, 970001);
        var uploads = new JdbcGithubSyncUploadRepository(jdbc);
        GithubSourceTag afterTag = new GithubSourceTag("AFTER_TAG", "v1.3.2", TAG_COMMIT, 3, null);

        UUID first = accept(uploads, taskId, namespaceId, 970001, "skills/grill-me", afterTag);
        UUID second = accept(uploads, taskId, namespaceId, 970001, "skills/tdd", afterTag);
        // A replayed report with a different tag must not rewrite the captured provenance.
        UUID replayed = replay(uploads, taskId, "skills/grill-me",
                new GithubSourceTag("NO_TAG", null, null, null, null));
        assertThat(replayed).isEqualTo(first);

        long declared = publish(namespaceId, first, "grill-me", "{\"upstreamVersion\":\"2.1.0\"}");
        long undeclared = publish(namespaceId, second, "tdd", "{\"upstreamVersion\":null}");
        var query = new GithubSkillSourceQueryRepository(jdbc, new ObjectMapper());

        GithubSkillSourceResponse withVersion = query.findPublishedVersion(declared).orElseThrow();
        GithubSkillSourceResponse withoutVersion = query.findPublishedVersion(undeclared).orElseThrow();
        GithubSkillSourceResponse.UpstreamTag expected = new GithubSkillSourceResponse.UpstreamTag(
                "AFTER_TAG", "REPOSITORY", "v1.3.2", TAG_COMMIT, 3, null);
        assertThat(withVersion.upstreamTag()).isEqualTo(expected);
        assertThat(withoutVersion.upstreamTag()).isEqualTo(expected);
        assertThat(withVersion.declaredVersion()).isEqualTo("2.1.0");
        assertThat(withoutVersion.declaredVersion()).isNull();
        assertThat(withVersion.commitSha()).isEqualTo(COMMIT);
        assertThat(query.findPublishedSyncedVersion(declared)).contains(withVersion);
    }

    @Test
    void manuallyUploadedVersionHasNoGithubSource() {
        long namespaceId = platformNamespaceId();
        long skillId = jdbc.queryForObject("""
                INSERT INTO skill (namespace_id, slug, owner_id, visibility, status)
                VALUES (?, 'source-tag-manual', 'github-sync-publisher', 'PUBLIC', 'ACTIVE') RETURNING id
                """, Long.class, namespaceId);
        long versionId = jdbc.queryForObject("""
                INSERT INTO skill_version (skill_id, version, status, published_at)
                VALUES (?, '1.0.0', 'PUBLISHED', CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, skillId);

        var query = new GithubSkillSourceQueryRepository(jdbc, new ObjectMapper());
        assertThat(query.findPublishedSyncedVersion(versionId)).isEmpty();
        assertThat(query.findPublishedSyncedVersion(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void ingestionWithoutReportedTagIsUnrecorded() {
        long namespaceId = platformNamespaceId();
        UUID taskId = leasedTask(namespaceId, 970002);
        UUID ingestion = accept(new JdbcGithubSyncUploadRepository(jdbc), taskId, namespaceId, 970002,
                "skills/legacy", null);
        assertThat(jdbc.queryForObject("SELECT source_tag_json IS NULL FROM github_sync_package_ingestion WHERE id = ?",
                Boolean.class, ingestion)).isTrue();
        long version = publish(namespaceId, ingestion, "legacy", "{}");

        var source = new GithubSkillSourceQueryRepository(jdbc, new ObjectMapper())
                .findPublishedVersion(version).orElseThrow();

        assertThat(source.upstreamTag()).isEqualTo(GithubSkillSourceResponse.UpstreamTag.unrecorded());
        assertThat(source.declaredVersion()).isNull();
    }

    @Test
    void unknownAndExactTagsKeepTheirStoredShape() {
        long namespaceId = platformNamespaceId();
        UUID taskId = leasedTask(namespaceId, 970003);
        var uploads = new JdbcGithubSyncUploadRepository(jdbc);
        UUID unknown = accept(uploads, taskId, namespaceId, 970003, "skills/unknown",
                new GithubSourceTag("UNKNOWN", null, null, null, "SHALLOW_HISTORY"));
        UUID exact = accept(uploads, taskId, namespaceId, 970003, "skills/exact",
                new GithubSourceTag("EXACT", "v7.0.1", COMMIT, 0, null));

        assertThat(jdbc.queryForObject("SELECT source_tag_json::text FROM github_sync_package_ingestion WHERE id = ?",
                String.class, unknown))
                .isEqualTo("{\"reason\": \"SHALLOW_HISTORY\", \"status\": \"UNKNOWN\", \"schemaVersion\": 1}");
        var query = new GithubSkillSourceQueryRepository(jdbc, new ObjectMapper());
        assertThat(query.findPublishedVersion(publish(namespaceId, unknown, "unknown", "{}")).orElseThrow()
                .upstreamTag()).isEqualTo(new GithubSkillSourceResponse.UpstreamTag(
                        "UNKNOWN", "REPOSITORY", null, null, null, "SHALLOW_HISTORY"));
        assertThat(query.findPublishedVersion(publish(namespaceId, exact, "exact", "{}")).orElseThrow()
                .upstreamTag()).isEqualTo(new GithubSkillSourceResponse.UpstreamTag(
                        "EXACT", "REPOSITORY", "v7.0.1", COMMIT, 0, null));
    }

    private UUID accept(JdbcGithubSyncUploadRepository uploads, UUID taskId, long namespaceId,
                        long repositoryId, String skillPath, GithubSourceTag tag) {
        UUID admissionId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO github_sync_package_admission
                    (id, task_id, namespace_id, repository_id, commit_sha, skill_path, lease_generation)
                VALUES (?, ?, ?, ?, ?, ?, 1)
                """, admissionId, taskId, namespaceId, repositoryId, COMMIT, skillPath);
        jdbc.update("""
                INSERT INTO github_sync_staged_upload
                    (id, admission_id, object_key, mode, ticket_hash, size_bytes, sha256, status, expires_at)
                VALUES (?, ?, ?, 'LOCAL_STREAM', ?, 12, ?, 'UPLOADED', CURRENT_TIMESTAMP + INTERVAL '1 hour')
                """, uploadId, admissionId, "github-sync/staging/" + uploadId + ".zip", DIGEST, DIGEST);
        uploadIds.put(skillPath, new UUID[] {admissionId, uploadId});
        GithubPackageReportResult result = uploads.acceptReport(taskId, 1, LEASE_HASH, admissionId, COMMIT,
                skillPath, uploadId, 12, DIGEST, tag, Instant.now());
        assertThat(result.status()).isEqualTo(GithubPackageReportResult.Status.RECEIVED);
        return result.ingestionId();
    }

    private UUID replay(JdbcGithubSyncUploadRepository uploads, UUID taskId, String skillPath, GithubSourceTag tag) {
        UUID[] ids = uploadIds.get(skillPath);
        GithubPackageReportResult result = uploads.acceptReport(taskId, 1, LEASE_HASH, ids[0], COMMIT,
                skillPath, ids[1], 12, DIGEST, tag, Instant.now());
        assertThat(result.status()).isEqualTo(GithubPackageReportResult.Status.RECEIVED);
        assertThat(jdbc.queryForObject("""
                SELECT source_tag_json ->> 'status' FROM github_sync_package_ingestion WHERE id = ?
                """, String.class, result.ingestionId())).isEqualTo("AFTER_TAG");
        return result.ingestionId();
    }

    private long publish(long namespaceId, UUID ingestionId, String slug, String parsedMetadata) {
        long skillId = jdbc.queryForObject("""
                INSERT INTO skill (namespace_id, slug, owner_id, visibility, status)
                VALUES (?, ?, 'github-sync-publisher', 'PUBLIC', 'ACTIVE') RETURNING id
                """, Long.class, namespaceId, "source-tag-" + slug);
        long versionId = jdbc.queryForObject("""
                INSERT INTO skill_version
                    (skill_id, version, status, published_at, github_sync_ingestion_id,
                     github_sync_revision, parsed_metadata_json)
                VALUES (?, '0.0.0-gfixture', 'PUBLISHED', CURRENT_TIMESTAMP, ?, 1, ?::jsonb) RETURNING id
                """, Long.class, skillId, ingestionId, parsedMetadata);
        jdbc.update("UPDATE github_sync_package_ingestion SET published_version_id = ? WHERE id = ?",
                versionId, ingestionId);
        return versionId;
    }

    private UUID leasedTask(long namespaceId, long repositoryId) {
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        jdbc.update("INSERT INTO github_sync_run (id, namespace_id, kind) VALUES (?, ?, 'REPOSITORY_SYNC')",
                runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, kind, mode, status, dedupe_key, snapshot_json,
                     lease_generation, lease_token_hash, lease_expires_at, leased_by)
                VALUES (?, ?, ?, 'REPOSITORY_SYNC', 'FULL', 'LEASED', ?,
                        jsonb_build_object('repositoryId', ?::bigint, 'owner', 'mattpocock', 'name', 'skills'),
                        1, ?, ?, 'source-tag-test')
                """, taskId, runId, namespaceId, "repository:" + repositoryId, repositoryId, LEASE_HASH,
                Timestamp.from(Instant.now().plusSeconds(300)));
        return taskId;
    }

    private long platformNamespaceId() {
        return jdbc.queryForObject("SELECT id FROM namespace WHERE slug = 'github' AND type = 'CATALOG'", Long.class);
    }
}
