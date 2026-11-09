package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncHistoryRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSourceIgnoreRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncCapacityGate;
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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class GithubRepositoryHistoryCleanupTest {
    private static final String COMMIT = "a".repeat(40);
    private static final String DIGEST = "b".repeat(64);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());

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

    @Test
    void sourceAndRunSummariesShowCurrentPackageFailureAfterAcquisitionCompletes() {
        long namespaceId = platformNamespaceId();
        long sourceId = source(namespaceId, 950003);
        Task task = completedTask(namespaceId, sourceId, 950003, Instant.now());
        UUID ingestionId = ingestion(task.id(), namespaceId, 950003, "skills/demo", "BLOCKED");
        jdbc.update("""
                UPDATE github_sync_package_ingestion
                SET last_error_code = 'SLUG_CONFLICT' WHERE id = ?
                """, ingestionId);
        var management = new GithubSyncManagementQueryRepository(jdbc,
                new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper()),
                new JdbcGithubSyncCapacityGate(jdbc));

        assertThat(management.runs(namespaceId, 0, 10).items())
                .singleElement().satisfies(run -> {
                    assertThat(run.id()).isEqualTo(task.runId());
                    assertThat(run.lastErrorCode()).isEqualTo("SLUG_CONFLICT");
                    assertThat(run.blockedPackages()).isEqualTo(1);
                });
        jdbc.update("UPDATE github_sync_package_ingestion SET status = 'FAILED' WHERE id = ?", ingestionId);
        assertThat(management.sources(namespaceId, 0, 10).items())
                .singleElement().satisfies(source -> {
                    assertThat(source.blockedPackages()).isZero();
                    assertThat(source.failedPackages()).isEqualTo(1);
                });
        assertThat(management.runs(namespaceId, 0, 10).items())
                .singleElement().satisfies(run -> assertThat(run.failedPackages()).isEqualTo(1));
        Task retry = completedTask(namespaceId, sourceId, 950003, Instant.now().plusSeconds(1));
        UUID recoveredId = ingestion(retry.id(), namespaceId, 950003,
                "skills/demo", "READY_FOR_REVIEW");
        jdbc.update("""
                UPDATE github_sync_package_ingestion
                SET created_at = CURRENT_TIMESTAMP + INTERVAL '1 second' WHERE id = ?
                """, recoveredId);
        assertThat(management.sources(namespaceId, 0, 10).items())
                .singleElement().satisfies(source -> {
                    assertThat(source.failedPackages()).isZero();
                    assertThat(source.pendingReviewPackages()).isEqualTo(1);
                });
        jdbc.update("""
                UPDATE github_sync_package_ingestion
                SET status = 'READY_FOR_REVIEW' WHERE id = ?
                """, ingestionId);
        assertThat(management.sources(namespaceId, 0, 10).items())
                .singleElement().satisfies(source -> {
                    assertThat(source.failedPackages()).isZero();
                    assertThat(source.pendingReviewPackages()).isEqualTo(1);
                });
        assertThat(management.runs(namespaceId, 0, 10).items())
                .filteredOn(run -> run.id().equals(task.runId()))
                .singleElement().satisfies(run -> assertThat(run.lastErrorCode()).isNull());
    }

    @Test
    void keepsAuditAndLatestSourcePathWhileRemovingExpiredOperationalDetails() {
        Instant now = Instant.now();
        long namespaceId = platformNamespaceId();
        long sourceId = source(namespaceId, 950001);
        Task old = completedTask(namespaceId, sourceId, 950001, now.minus(Duration.ofDays(91)));
        Task recent = completedTask(namespaceId, sourceId, 950001, now.minus(Duration.ofDays(1)));
        path(old.id(), "skills/demo");
        path(old.id(), "skills/only-old");
        path(recent.id(), "skills/demo");
        UUID oldIngestion = ingestion(old.id(), namespaceId, 950001, "skills/demo", "APPROVED");
        long skillId = jdbc.queryForObject("""
                INSERT INTO skill (namespace_id, slug, owner_id, visibility, status)
                VALUES (?, 'history-demo', 'github-sync-publisher', 'PUBLIC', 'ACTIVE') RETURNING id
                """, Long.class, namespaceId);
        long versionId = jdbc.queryForObject("""
                INSERT INTO skill_version
                    (skill_id, version, status, published_at, github_sync_ingestion_id)
                VALUES (?, '1.0.0', 'PUBLISHED', CURRENT_TIMESTAMP, ?) RETURNING id
                """, Long.class, skillId, oldIngestion);
        jdbc.update("""
                UPDATE github_sync_package_ingestion SET published_version_id = ? WHERE id = ?
                """, versionId, oldIngestion);

        long pendingSource = source(namespaceId, 950002);
        Task pending = completedTask(namespaceId, pendingSource, 950002,
                now.minus(Duration.ofDays(91)));
        path(pending.id(), "skills/pending");
        ingestion(pending.id(), namespaceId, 950002, "skills/pending", "READY_FOR_REVIEW");

        var cleaned = new JdbcGithubSyncHistoryRepository(jdbc).purgeExpiredRepositoryDetails(
                now.minus(Duration.ofDays(90)), now, 10);

        assertThat(cleaned.tasks()).isEqualTo(1);
        assertThat(cleaned.runs()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT details_purged_at IS NOT NULL FROM github_sync_task WHERE id = ?
                """, Boolean.class, old.id())).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT jsonb_exists(snapshot_json, 'ignorePatterns')
                FROM github_sync_task WHERE id = ?
                """, Boolean.class, old.id())).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT snapshot_json ->> 'owner' FROM github_sync_task WHERE id = ?
                """, String.class, old.id())).isEqualTo("oldowner");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_path_result WHERE task_id = ?
                """, Integer.class, old.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT skill_path FROM github_sync_path_result WHERE task_id = ?
                """, String.class, old.id())).isEqualTo("skills/only-old");
        assertThat(jdbc.queryForObject("""
                SELECT details_purged_at IS NULL FROM github_sync_task WHERE id = ?
                """, Boolean.class, recent.id())).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT details_purged_at IS NULL FROM github_sync_task WHERE id = ?
                """, Boolean.class, pending.id())).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT details_purged_at IS NOT NULL FROM github_sync_run WHERE id = ?
                """, Boolean.class, old.runId())).isTrue();
        assertThat(new GithubSkillSourceQueryRepository(jdbc, new ObjectMapper())
                .findPublishedVersion(versionId)).isPresent();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion WHERE id = ? AND task_id = ?
                """, Integer.class, oldIngestion, old.id())).isEqualTo(1);
        var management = new GithubSyncManagementQueryRepository(jdbc,
                new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper()),
                new JdbcGithubSyncCapacityGate(jdbc));
        assertThat(management.runs(namespaceId, 0, 10).items())
                .extracting(run -> run.id()).contains(recent.runId(), pending.runId())
                .doesNotContain(old.runId());
        assertThat(management.skillPaths(namespaceId, sourceId, 0, 10).items())
                .extracting(path -> path.skillPath())
                .containsExactlyInAnyOrder("skills/demo", "skills/only-old");
    }

    private Task completedTask(long namespaceId, long sourceId, long repositoryId, Instant completedAt) {
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        jdbc.update("INSERT INTO github_sync_run (id, namespace_id, kind) VALUES (?, ?, 'REPOSITORY_SYNC')",
                runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, source_id, kind, mode, status, dedupe_key,
                     snapshot_json, fixed_commit_sha, completed_at)
                VALUES (?, ?, ?, ?, 'REPOSITORY_SYNC', 'FULL', 'ACQUISITION_COMPLETE', ?,
                        jsonb_build_object('repositoryId', ?::bigint, 'owner', 'oldowner',
                                           'name', 'oldrepo', 'ignorePatterns',
                                           jsonb_build_array('docs')),
                        ?, ?)
                """, taskId, runId, namespaceId, sourceId,
                "repository:" + repositoryId, repositoryId, COMMIT, Timestamp.from(completedAt));
        jdbc.update("UPDATE github_sync_run SET finished_at = ? WHERE id = ?",
                Timestamp.from(completedAt), runId);
        jdbc.update("UPDATE github_sync_task SET created_at = ? WHERE id = ?",
                Timestamp.from(completedAt), taskId);
        return new Task(taskId, runId);
    }

    private void path(UUID taskId, String skillPath) {
        jdbc.update("""
                INSERT INTO github_sync_path_result
                    (task_id, commit_sha, skill_path, outcome, sha256)
                VALUES (?, ?, ?, 'ACCEPTED', ?)
                """, taskId, COMMIT, skillPath, DIGEST);
    }

    private UUID ingestion(UUID taskId, long namespaceId, long repositoryId,
                           String skillPath, String status) {
        UUID admissionId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        UUID ingestionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO github_sync_package_admission
                    (id, task_id, namespace_id, repository_id, commit_sha, skill_path, lease_generation)
                VALUES (?, ?, ?, ?, ?, ?, 1)
                """, admissionId, taskId, namespaceId, repositoryId, COMMIT, skillPath);
        jdbc.update("""
                INSERT INTO github_sync_staged_upload
                    (id, admission_id, object_key, mode, ticket_hash, size_bytes, sha256,
                     status, expires_at)
                VALUES (?, ?, ?, 'LOCAL_STREAM', ?, 1, ?, 'CLEANED', CURRENT_TIMESTAMP)
                """, uploadId, admissionId, "history/" + uploadId, DIGEST, DIGEST);
        jdbc.update("""
                INSERT INTO github_sync_package_ingestion
                    (id, task_id, admission_id, upload_id, namespace_id, repository_id,
                     commit_sha, skill_path, object_key, size_bytes, sha256, status,
                     object_cleaned_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, CURRENT_TIMESTAMP)
                """, ingestionId, taskId, admissionId, uploadId, namespaceId, repositoryId,
                COMMIT, skillPath, "history/" + uploadId, DIGEST, status);
        return ingestionId;
    }

    private long source(long namespaceId, long repositoryId) {
        return jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility)
                VALUES (?, ?, 'oldowner', 'oldrepo', 'PUBLIC') RETURNING id
                """, Long.class, namespaceId, repositoryId);
    }

    private long platformNamespaceId() {
        return jdbc.queryForObject("SELECT id FROM namespace WHERE slug = 'github' AND type = 'CATALOG'",
                Long.class);
    }

    private record Task(UUID id, UUID runId) {}
}
