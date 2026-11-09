package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.githubsync.GithubDiscoveryBatch;
import com.iflytek.skillhub.githubsync.GithubDiscoveryBatchResult;
import com.iflytek.skillhub.githubsync.GithubDiscoveryCandidate;
import com.iflytek.skillhub.infra.jpa.JdbcGithubCandidateTaskRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubDiscoveryEntryRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSourceIgnoreRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncCapacityGate;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncPathPresenceRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncIngestionRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncHistoryRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncSchedulePolicyRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncSourceCheckpointRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncTaskRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class GithubCandidateOriginRepositoryTest {
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
    void syntheticThousandRepositoryCandidatesScheduleWithoutDuplicates() {
        long namespaceId = platformNamespaceId();
        Instant now = Instant.now();
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 20000, global_low = 10000,
                    namespace_high = 20000, namespace_low = 10000
                WHERE id = TRUE
                """);
        jdbc.update("""
                INSERT INTO github_discovery_candidate
                    (namespace_id, repository_id, owner_login, repository_name, next_validation_at)
                SELECT ?, 2000000 + series.id, 'fixture-owner', 'fixture-repo-' || series.id, ?
                FROM generate_series(1, 1000) AS series(id)
                """, namespaceId, Timestamp.from(now));
        var candidates = new JdbcGithubCandidateTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc));

        long started = System.nanoTime();
        int scheduled = 0;
        for (int batch = 0; batch < 10; batch++) {
            scheduled += candidates.enqueueDue(now.plusSeconds(1), 100);
        }
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
        assertThat(scheduled).isEqualTo(1000);
        assertThat(candidates.enqueueDue(now.plusSeconds(1), 100)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_candidate
                WHERE namespace_id = ? AND status = 'VALIDATING'
                """, Integer.class, namespaceId)).isEqualTo(1000);
        assertThat(jdbc.queryForObject("""
                SELECT count(DISTINCT validation_task_id) FROM github_discovery_candidate
                WHERE namespace_id = ?
                """, Integer.class, namespaceId)).isEqualTo(1000);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_task
                WHERE namespace_id = ? AND kind = 'REPOSITORY_SYNC' AND mode = 'FULL'
                """, Integer.class, namespaceId)).isEqualTo(1000);
        System.out.printf("GitHub candidate scheduling baseline: repositories=1000, batches=10, elapsedMs=%d%n",
                elapsedMillis);
    }

    @Test
    void existingSourceCanBeRediscoveredWhenCandidateBudgetIsFull() {
        long namespaceId = platformNamespaceId();
        long repositoryId = 930010L;
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET candidate_pending_limit = 1, candidate_per_lease_limit = 1
                WHERE id = TRUE
                """);
        jdbc.update("""
                INSERT INTO github_discovery_candidate
                    (namespace_id, repository_id, owner_login, repository_name)
                VALUES (?, 930011, 'other-owner', 'unverified')
                """, namespaceId);
        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility)
                VALUES (?, ?, 'public-owner', 'skills', 'PUBLIC') RETURNING id
                """, Long.class, namespaceId, repositoryId);
        UUID taskId = leasedDiscovery(namespaceId, "SEARCH_RULE", "");
        var ignores = new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper());
        var discovery = new JdbcGithubSyncTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc),
                new JdbcGithubSyncSourceCheckpointRepository(jdbc), ignores,
                new JdbcGithubSyncPathPresenceRepository(jdbc, ignores));

        assertThat(discovery.recordDiscoveryBatch(taskId, 1, "a".repeat(64),
                new GithubDiscoveryBatch(null, "page-1", List.of(
                        new GithubDiscoveryCandidate(repositoryId, "public-owner", "skills", "main")),
                        0, null), "b".repeat(64), Instant.now()))
                .isEqualTo(GithubDiscoveryBatchResult.ACCEPTED);
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_discovery_candidate
                WHERE namespace_id = ? AND repository_id = ?
                """, String.class, namespaceId, repositoryId)).isEqualTo("BOUND");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_candidate
                WHERE namespace_id = ? AND status IN ('PENDING', 'VALIDATING')
                """, Integer.class, namespaceId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_source_origin
                WHERE source_id = ? AND origin_type = 'SEARCH_RULE'
                """, Integer.class, sourceId)).isEqualTo(1);
    }

    @Test
    void curatedRepositoryDeduplicatesWithinNamespaceButKeepsTeamBindingIndependent() {
        long platformId = platformNamespaceId();
        long teamId = jdbc.queryForObject("""
                INSERT INTO namespace (slug, display_name, type, created_by)
                VALUES ('github-source-team', 'GitHub Source Team', 'TEAM', 'github-sync-publisher')
                RETURNING id
                """, Long.class);
        var entries = new JdbcGithubDiscoveryEntryRepository(jdbc, new ObjectMapper());
        var platformEntry = entries.createCuratedRepository("public-owner", "skills");
        var teamEntry = entries.createTeamRepository(teamId, "public-owner", "skills", "NAMESPACE_ONLY");
        var renamedEntry = entries.createCuratedRepository("renamed-owner", "skills");
        var ignores = new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper());
        var discovery = new JdbcGithubSyncTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc),
                new JdbcGithubSyncSourceCheckpointRepository(jdbc), ignores,
                new JdbcGithubSyncPathPresenceRepository(jdbc, ignores));
        long repositoryId = 930002L;

        recordCuratedRepository(discovery, platformId, platformEntry.id(), repositoryId,
                "public-owner");
        recordCuratedRepository(discovery, teamId, teamEntry.id(), repositoryId,
                "public-owner");
        recordCuratedRepository(discovery, platformId, renamedEntry.id(), repositoryId,
                "renamed-owner");

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_source_binding WHERE repository_id = ?
                """, Integer.class, repositoryId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT visibility FROM github_source_binding
                WHERE namespace_id = ? AND repository_id = ?
                """, String.class, platformId, repositoryId)).isEqualTo("PUBLIC");
        assertThat(jdbc.queryForObject("""
                SELECT visibility FROM github_source_binding
                WHERE namespace_id = ? AND repository_id = ?
                """, String.class, teamId, repositoryId)).isEqualTo("NAMESPACE_ONLY");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_source_origin origin
                JOIN github_source_binding source ON source.id = origin.source_id
                WHERE source.namespace_id = ? AND source.repository_id = ?
                """, Integer.class, platformId, repositoryId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_source_origin origin
                JOIN github_source_binding source ON source.id = origin.source_id
                WHERE source.namespace_id = ? AND source.repository_id = ?
                  AND origin.origin_type = 'TEAM_CONFIG'
                """, Integer.class, teamId, repositoryId)).isEqualTo(1);

        jdbc.update("UPDATE github_source_binding SET status = 'PAUSED' WHERE namespace_id = ? AND repository_id = ?",
                platformId, repositoryId);
        recordCuratedRepository(discovery, platformId, platformEntry.id(), repositoryId, "renamed-owner");
        assertThat(jdbc.queryForObject("SELECT status FROM github_source_binding WHERE namespace_id = ? AND repository_id = ?",
                String.class, platformId, repositoryId)).isEqualTo("PAUSED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_source_binding WHERE repository_id = ?",
                Integer.class, repositoryId)).isEqualTo(2);

        entries.setTeamRepositoryVisibility(teamId, teamEntry.id(), "PUBLIC");
        assertThat(jdbc.queryForObject("""
                SELECT visibility FROM github_source_binding
                WHERE namespace_id = ? AND repository_id = ?
                """, String.class, teamId, repositoryId)).isEqualTo("PUBLIC");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_source_origin origin
                JOIN github_source_binding source ON source.id = origin.source_id
                WHERE source.namespace_id = ? AND source.repository_id = ?
                """, Integer.class, platformId, repositoryId)).isEqualTo(2);
    }

    private void recordCuratedRepository(JdbcGithubSyncTaskRepository discovery, long namespaceId,
                                         UUID entryId, long repositoryId, String owner) {
        UUID taskId = leasedCuratedDiscovery(namespaceId, entryId);
        assertThat(discovery.recordDiscoveryBatch(taskId, 1, "a".repeat(64),
                new GithubDiscoveryBatch(null, "page-1", List.of(
                        new GithubDiscoveryCandidate(repositoryId, owner, "skills", "main")),
                        0, null), "b".repeat(64), Instant.now()))
                .isEqualTo(GithubDiscoveryBatchResult.ACCEPTED);
    }

    private UUID leasedCuratedDiscovery(long namespaceId, UUID entryId) {
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        jdbc.update("INSERT INTO github_sync_run (id, namespace_id, kind) VALUES (?, ?, 'DISCOVERY')",
                runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, kind, status, dedupe_key, snapshot_json,
                     lease_generation, lease_token_hash, lease_expires_at, leased_by)
                VALUES (?, ?, ?, 'DISCOVERY', 'LEASED', ?,
                        jsonb_build_object('entryType', 'CURATED_REPOSITORY',
                                           'ruleId', ?::text, 'candidateBudget', 1),
                        1, ?, ?, 'origin-test')
                """, taskId, runId, namespaceId, "discovery:" + taskId,
                entryId.toString(), "a".repeat(64),
                Timestamp.from(Instant.now().plusSeconds(300)));
        return taskId;
    }

    @Test
    void curatedOwnerPrioritySurvivesRemovalOfDiscoveryTaskHits() {
        long namespaceId = platformNamespaceId();
        UUID taskId = leasedDiscovery(namespaceId, "CURATED_OWNER", "source-owner");
        var ignores = new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper());
        var discovery = new JdbcGithubSyncTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc),
                new JdbcGithubSyncSourceCheckpointRepository(jdbc), ignores,
                new JdbcGithubSyncPathPresenceRepository(jdbc, ignores));
        Instant now = Instant.now();
        assertThat(discovery.recordDiscoveryBatch(taskId, 1, "a".repeat(64),
                new GithubDiscoveryBatch(null, "page-1", List.of(
                        new GithubDiscoveryCandidate(930001, "source-owner", "skills", "main")),
                        0, null), "b".repeat(64), now))
                .isEqualTo(GithubDiscoveryBatchResult.ACCEPTED);
        long candidateId = jdbc.queryForObject("""
                SELECT id FROM github_discovery_candidate WHERE repository_id = 930001
                """, Long.class);
        assertThat(jdbc.queryForObject("""
                SELECT origin_key FROM github_discovery_candidate_origin
                WHERE candidate_id = ? AND origin_type = 'CURATED_OWNER'
                """, String.class, candidateId)).isEqualTo("source-owner");

        jdbc.update("DELETE FROM github_discovery_task_hit WHERE task_id = ?", taskId);
        jdbc.update("DELETE FROM github_discovery_batch WHERE task_id = ?", taskId);
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 100, global_low = 50,
                    namespace_high = 100, namespace_low = 50 WHERE id = TRUE
                """);
        var candidates = new JdbcGithubCandidateTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc));
        assertThat(candidates.enqueueDue(now.plusSeconds(1), 10)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT task.priority FROM github_sync_task task
                JOIN github_discovery_candidate candidate ON candidate.validation_task_id = task.id
                WHERE candidate.id = ?
                """, Integer.class, candidateId)).isEqualTo(70);

        UUID validationTaskId = jdbc.queryForObject("""
                SELECT validation_task_id FROM github_discovery_candidate WHERE id = ?
                """, UUID.class, candidateId);
        UUID admissionId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        UUID ingestionId = UUID.randomUUID();
        UUID validationToken = UUID.randomUUID();
        String commit = "c".repeat(40);
        String digest = "d".repeat(64);
        jdbc.update("""
                INSERT INTO github_sync_package_admission
                    (id, task_id, namespace_id, repository_id, commit_sha, skill_path, lease_generation)
                VALUES (?, ?, ?, 930001, ?, 'skills/demo', 1)
                """, admissionId, validationTaskId, namespaceId, commit);
        jdbc.update("""
                INSERT INTO github_sync_staged_upload
                    (id, admission_id, object_key, mode, ticket_hash, size_bytes, sha256,
                     status, expires_at)
                VALUES (?, ?, ?, 'LOCAL_STREAM', ?, 1, ?, 'ACCEPTED', ?)
                """, uploadId, admissionId, "origin-test/" + uploadId, digest, digest,
                Timestamp.from(now.plusSeconds(3600)));
        jdbc.update("""
                INSERT INTO github_sync_package_ingestion
                    (id, task_id, admission_id, upload_id, namespace_id, repository_id,
                     commit_sha, skill_path, object_key, size_bytes, sha256,
                     status, validation_token, validation_expires_at)
                VALUES (?, ?, ?, ?, ?, 930001, ?, 'skills/demo', ?, 1, ?,
                        'VALIDATING', ?, ?)
                """, ingestionId, validationTaskId, admissionId, uploadId, namespaceId,
                commit, "origin-test/" + uploadId, digest, validationToken,
                Timestamp.from(now.plusSeconds(3600)));
        var ingestions = new JdbcGithubSyncIngestionRepository(jdbc,
                new JdbcGithubSyncSourceCheckpointRepository(jdbc),
                new JdbcGithubSyncSchedulePolicyRepository(jdbc), Duration.ofHours(24));
        assertThat(ingestions.validated(ingestionId, validationToken, digest, 0, "{}", now))
                .isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT origin.origin_key FROM github_source_origin origin
                JOIN github_source_binding source ON source.id = origin.source_id
                WHERE source.namespace_id = ? AND source.repository_id = 930001
                  AND origin.origin_type = 'CURATED_OWNER'
                """, String.class, namespaceId)).isEqualTo("source-owner");

        UUID discoveryRunId = jdbc.queryForObject("""
                SELECT run_id FROM github_sync_task WHERE id = ?
                """, UUID.class, taskId);
        Instant old = now.minus(Duration.ofDays(91));
        jdbc.update("""
                INSERT INTO github_discovery_task_hit
                    (task_id, repository_id, candidate_id, lease_generation)
                VALUES (?, 930001, ?, 1)
                """, taskId, candidateId);
        jdbc.update("""
                INSERT INTO github_discovery_batch (task_id, checkpoint, request_sha256)
                VALUES (?, 'old-page', ?)
                """, taskId, "e".repeat(64));
        jdbc.update("""
                UPDATE github_sync_task
                SET status = 'ACQUISITION_COMPLETE', completed_at = ?,
                    lease_token_hash = NULL, lease_expires_at = NULL, leased_by = NULL
                WHERE id = ?
                """, Timestamp.from(old), taskId);
        jdbc.update("UPDATE github_sync_run SET finished_at = ? WHERE id = ?",
                Timestamp.from(old), discoveryRunId);
        UUID unfinished = leasedDiscovery(namespaceId, "CURATED_OWNER", "another-owner");
        UUID recent = leasedDiscovery(namespaceId, "CURATED_OWNER", "recent-owner");
        UUID recentRun = jdbc.queryForObject("SELECT run_id FROM github_sync_task WHERE id = ?",
                UUID.class, recent);
        Instant recentFinished = now.minus(Duration.ofDays(89));
        jdbc.update("""
                UPDATE github_sync_task
                SET status = 'ACQUISITION_COMPLETE', completed_at = ?,
                    lease_token_hash = NULL, lease_expires_at = NULL, leased_by = NULL
                WHERE id = ?
                """, Timestamp.from(recentFinished), recent);
        jdbc.update("UPDATE github_sync_run SET finished_at = ? WHERE id = ?",
                Timestamp.from(recentFinished), recentRun);
        var cleaned = new JdbcGithubSyncHistoryRepository(jdbc)
                .deleteExpiredDiscoveryHistory(now.minus(Duration.ofDays(90)), 10);
        assertThat(cleaned.tasks()).isEqualTo(1);
        assertThat(cleaned.runs()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_sync_task WHERE id = ?",
                Integer.class, unfinished)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_sync_task WHERE id = ?",
                Integer.class, recent)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_discovery_task_hit WHERE task_id = ?",
                Integer.class, taskId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_discovery_batch WHERE task_id = ?",
                Integer.class, taskId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_discovery_candidate_origin",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_source_origin origin
                JOIN github_source_binding source ON source.id = origin.source_id
                WHERE source.namespace_id = ? AND source.repository_id = 930001
                """, Integer.class, namespaceId)).isEqualTo(1);
    }

    private UUID leasedDiscovery(long namespaceId, String entryType, String owner) {
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        jdbc.update("INSERT INTO github_sync_run (id, namespace_id, kind) VALUES (?, ?, 'DISCOVERY')",
                runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, kind, status, dedupe_key, snapshot_json,
                     lease_generation, lease_token_hash, lease_expires_at, leased_by)
                VALUES (?, ?, ?, 'DISCOVERY', 'LEASED', ?,
                        jsonb_build_object('entryType', ?::text, 'ruleId', ?::text,
                                           'owner', ?::text, 'candidateBudget', 10),
                        1, ?, ?, 'origin-test')
                """, taskId, runId, namespaceId, "discovery:" + taskId,
                entryType, UUID.randomUUID().toString(), owner,
                "a".repeat(64), Timestamp.from(Instant.now().plusSeconds(300)));
        return taskId;
    }

    @Test
    void candidateWithoutValidSkillStaysUnboundAndIsRetriedAtLowFrequency() {
        long namespaceId = platformNamespaceId();
        long repositoryId = 930009L;
        Instant now = Instant.now();
        long candidateId = jdbc.queryForObject("""
                INSERT INTO github_discovery_candidate
                    (namespace_id, repository_id, owner_login, repository_name,
                     next_validation_at)
                VALUES (?, ?, 'public-owner', 'no-skill-repo', ?) RETURNING id
                """, Long.class, namespaceId, repositoryId, Timestamp.from(now));
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 100, global_low = 50,
                    namespace_high = 50, namespace_low = 25 WHERE id = TRUE
                """);
        var candidates = new JdbcGithubCandidateTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc));

        assertThat(candidates.enqueueDue(now.plusSeconds(1), 10)).isEqualTo(1);
        UUID validationTaskId = jdbc.queryForObject("""
                SELECT validation_task_id FROM github_discovery_candidate WHERE id = ?
                """, UUID.class, candidateId);
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_discovery_candidate WHERE id = ?
                """, String.class, candidateId)).isEqualTo("VALIDATING");
        jdbc.update("""
                UPDATE github_sync_task
                SET status = 'ACQUISITION_COMPLETE', completed_at = ? WHERE id = ?
                """, Timestamp.from(now.plusSeconds(2)), validationTaskId);

        assertThat(candidates.reconcileCompleted(now.plusSeconds(3), 10)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_discovery_candidate WHERE id = ?
                """, String.class, candidateId)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("""
                SELECT last_error_code FROM github_discovery_candidate WHERE id = ?
                """, String.class, candidateId)).isEqualTo("NO_VALID_SKILL");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_source_binding
                WHERE namespace_id = ? AND repository_id = ?
                """, Integer.class, namespaceId, repositoryId)).isZero();
        Instant retryAt = jdbc.queryForObject("""
                SELECT next_validation_at FROM github_discovery_candidate WHERE id = ?
                """, Timestamp.class, candidateId).toInstant();
        assertThat(retryAt).isAfter(now.plus(Duration.ofDays(6)));
        assertThat(candidates.enqueueDue(now.plus(Duration.ofDays(1)), 10)).isZero();
        assertThat(candidates.enqueueDue(retryAt.plusSeconds(1), 10)).isEqualTo(1);
    }

    private long platformNamespaceId() {
        return jdbc.queryForObject("SELECT id FROM namespace WHERE slug = 'github' AND type = 'CATALOG'",
                Long.class);
    }
}
