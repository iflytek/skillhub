package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.iflytek.skillhub.githubsync.GithubSyncUploadRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncUploadRepository;
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
class GithubSyncUploadCleanupRepositoryTest {
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
    void localTicketCannotSelectAnotherUploadObjectKey() {
        long namespaceId = jdbc.queryForObject("""
                SELECT id FROM namespace WHERE slug = 'github' AND type = 'CATALOG'
                """, Long.class);
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID firstAdmission = UUID.randomUUID();
        UUID secondAdmission = UUID.randomUUID();
        UUID firstUpload = UUID.randomUUID();
        UUID secondUpload = UUID.randomUUID();
        String firstKey = "github-sync/staging/" + firstUpload + ".zip";
        String secondKey = "github-sync/staging/" + secondUpload + ".zip";
        String leaseHash = "a".repeat(64);
        String commit = "b".repeat(40);
        String digest = "c".repeat(64);
        Instant now = Instant.now();
        jdbc.update("INSERT INTO github_sync_run (id, namespace_id, kind) VALUES (?, ?, 'REPOSITORY_SYNC')",
                runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, kind, mode, status, dedupe_key,
                     snapshot_json, lease_generation, lease_token_hash, lease_expires_at, leased_by)
                VALUES (?, ?, ?, 'REPOSITORY_SYNC', 'FULL', 'LEASED', ?,
                        jsonb_build_object('repositoryId', 930002), 1, ?, ?, 'upload-ticket-test')
                """, taskId, runId, namespaceId, "repository:930002", leaseHash,
                Timestamp.from(now.plusSeconds(300)));
        jdbc.update("""
                INSERT INTO github_sync_package_admission
                    (id, task_id, namespace_id, repository_id, commit_sha, skill_path, lease_generation)
                VALUES (?, ?, ?, 930002, ?, 'skills/first', 1),
                       (?, ?, ?, 930002, ?, 'skills/second', 1)
                """, firstAdmission, taskId, namespaceId, commit,
                secondAdmission, taskId, namespaceId, commit);
        var repository = new JdbcGithubSyncUploadRepository(jdbc);
        assertThat(repository.issue(taskId, 1, leaseHash, firstAdmission, commit,
                "skills/first", firstUpload, firstKey, "LOCAL_STREAM", "d".repeat(64),
                12, digest, now.plusSeconds(120), now)).isTrue();
        assertThat(repository.issue(taskId, 1, leaseHash, secondAdmission, commit,
                "skills/second", secondUpload, secondKey, "LOCAL_STREAM", "e".repeat(64),
                12, digest, now.plusSeconds(120), now)).isTrue();

        assertThat(repository.beginLocalUpload(secondUpload, "d".repeat(64), now.plusSeconds(1)))
                .isEmpty();
        assertThat(repository.beginLocalUpload(UUID.randomUUID(), "d".repeat(64),
                now.plusSeconds(1))).isEmpty();
        assertThat(repository.beginLocalUpload(firstUpload, "d".repeat(64), now.plusSeconds(1)))
                .contains(new GithubSyncUploadRepository.LocalUpload(
                        firstKey, 12, digest));
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_staged_upload WHERE id = ?
                """, String.class, secondUpload)).isEqualTo("ISSUED");
    }

    @Test
    void completedLocalUploadCanBeFailedAndReclaimedForCleanup() {
        long namespaceId = jdbc.queryForObject("""
                SELECT id FROM namespace WHERE slug = 'github' AND type = 'CATALOG'
                """, Long.class);
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID admissionId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        String objectKey = "github-sync/staging/" + uploadId + ".zip";
        String commit = "a".repeat(40);
        String digest = "b".repeat(64);
        jdbc.update("INSERT INTO github_sync_run (id, namespace_id, kind) VALUES (?, ?, 'REPOSITORY_SYNC')",
                runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, kind, mode, dedupe_key, snapshot_json)
                VALUES (?, ?, ?, 'REPOSITORY_SYNC', 'FULL', ?,
                        jsonb_build_object('repositoryId', 930001, 'owner', 'example', 'name', 'repo'))
                """, taskId, runId, namespaceId, "repository:930001");
        jdbc.update("""
                INSERT INTO github_sync_package_admission
                    (id, task_id, namespace_id, repository_id, commit_sha, skill_path, lease_generation)
                VALUES (?, ?, ?, 930001, ?, 'skills/demo', 1)
                """, admissionId, taskId, namespaceId, commit);
        jdbc.update("""
                INSERT INTO github_sync_staged_upload
                    (id, admission_id, object_key, mode, ticket_hash, size_bytes, sha256,
                     status, expires_at)
                VALUES (?, ?, ?, 'LOCAL_STREAM', ?, 12, ?, 'UPLOADED', CURRENT_TIMESTAMP)
                """, uploadId, admissionId, objectKey, digest, digest);
        var repository = new JdbcGithubSyncUploadRepository(jdbc);
        Instant now = Instant.now();

        assertThat(repository.failUpload(uploadId, now)).isTrue();
        var claimed = repository.claimCleanup(now, Duration.ofHours(1), 10);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.getFirst().uploadId()).isEqualTo(uploadId);
        assertThat(claimed.getFirst().objectKey()).isEqualTo(objectKey);
        repository.markCleaned(uploadId, now);
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_staged_upload WHERE id = ?
                """, String.class, uploadId)).isEqualTo("CLEANED");
        assertThat(repository.failUpload(uploadId, now)).isFalse();

        UUID acceptedAdmissionId = UUID.randomUUID();
        UUID acceptedUploadId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO github_sync_package_admission
                    (id, task_id, namespace_id, repository_id, commit_sha, skill_path, lease_generation,
                     status)
                VALUES (?, ?, ?, 930001, ?, 'skills/accepted', 1, 'ACCEPTED')
                """, acceptedAdmissionId, taskId, namespaceId, commit);
        jdbc.update("""
                INSERT INTO github_sync_staged_upload
                    (id, admission_id, object_key, mode, ticket_hash, size_bytes, sha256,
                     status, expires_at)
                VALUES (?, ?, ?, 'LOCAL_STREAM', ?, 12, ?, 'ACCEPTED', CURRENT_TIMESTAMP)
                """, acceptedUploadId, acceptedAdmissionId,
                "github-sync/staging/" + acceptedUploadId + ".zip", digest, digest);

        assertThat(repository.failUpload(acceptedUploadId, now)).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_staged_upload WHERE id = ?
                """, String.class, acceptedUploadId)).isEqualTo("ACCEPTED");
    }
}
