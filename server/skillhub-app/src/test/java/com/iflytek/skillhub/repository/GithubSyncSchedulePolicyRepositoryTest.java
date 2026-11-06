package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.support.PostgresTestImage;
import static com.iflytek.skillhub.support.GithubSyncWorkerCapabilities.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.githubsync.GithubSyncDeferralReason;
import com.iflytek.skillhub.githubsync.GithubDiscoveryCompletionResult;
import com.iflytek.skillhub.githubsync.GithubDiscoveryBatchResult;
import com.iflytek.skillhub.githubsync.GithubDiscoveryBatch;
import com.iflytek.skillhub.infra.jpa.JdbcGithubDiscoveryEntryRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSourceTaskRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSourceIgnoreRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncCapacityGate;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncPathPresenceRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncSchedulePolicyRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncSourceCheckpointRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncTaskRepository;
import com.iflytek.skillhub.githubsync.GithubSyncTaskKind;
import com.iflytek.skillhub.githubsync.GithubSyncTaskMode;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class GithubSyncSchedulePolicyRepositoryTest {
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
    void partialRevisionUsesHeadChecksUntilUpstreamCommitChanges() {
        long namespaceId = platformNamespaceId();
        long repositoryId = 847204L;
        String oldCommit = "a".repeat(40);
        String newCommit = "b".repeat(40);
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 100, global_low = 50, namespace_high = 50, namespace_low = 25
                WHERE id = TRUE
                """);
        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility,
                     last_observed_commit, last_partial_commit, next_check_at)
                VALUES (?, ?, 'source', 'partial', 'PUBLIC', ?, ?, CURRENT_TIMESTAMP - INTERVAL '1 hour')
                RETURNING id
                """, Long.class, namespaceId, repositoryId, oldCommit, oldCommit);
        var sources = new JdbcGithubSourceTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc));
        Instant now = Instant.now();

        assertThat(sources.enqueueDue(now, 10)).isEqualTo(1);
        UUID headTaskId = jdbc.queryForObject("""
                SELECT id FROM github_sync_task WHERE source_id = ? AND status = 'QUEUED'
                """, UUID.class, sourceId);
        assertThat(jdbc.queryForObject("SELECT mode FROM github_sync_task WHERE id = ?",
                String.class, headTaskId)).isEqualTo("HEAD_ONLY");
        jdbc.update("""
                UPDATE github_sync_task
                SET status = 'ACQUISITION_COMPLETE', completed_at = ? WHERE id = ?
                """, Timestamp.from(now), headTaskId);
        jdbc.update("""
                UPDATE github_source_binding SET next_check_at = ? WHERE id = ?
                """, Timestamp.from(now.plusSeconds(86400)), sourceId);
        assertThat(sources.enqueueDue(now.plusSeconds(1), 10)).isZero();

        jdbc.update("""
                UPDATE github_source_binding SET last_observed_commit = ? WHERE id = ?
                """, newCommit, sourceId);
        assertThat(sources.enqueueDue(now.plusSeconds(2), 10)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT mode FROM github_sync_task WHERE source_id = ? AND status = 'QUEUED'
                """, String.class, sourceId)).isEqualTo("FULL");
    }

    @Test
    void manualRescanDuringHeadCheckForcesOneFullScanAfterItFinishes() {
        long namespaceId = platformNamespaceId();
        long repositoryId = 847205L;
        String commit = "c".repeat(40);
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 100, global_low = 50, namespace_high = 50, namespace_low = 25
                WHERE id = TRUE
                """);
        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility,
                     last_observed_commit, last_partial_commit, next_check_at)
                VALUES (?, ?, 'source', 'manual-partial', 'PUBLIC', ?, ?, CURRENT_TIMESTAMP - INTERVAL '1 hour')
                RETURNING id
                """, Long.class, namespaceId, repositoryId, commit, commit);
        var sources = new JdbcGithubSourceTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc));
        Instant now = Instant.now();

        assertThat(sources.enqueueDue(now, 10)).isEqualTo(1);
        UUID headTaskId = jdbc.queryForObject("""
                SELECT id FROM github_sync_task WHERE source_id = ? AND status = 'QUEUED'
                """, UUID.class, sourceId);
        assertThat(sources.requestRescan(namespaceId, sourceId, now).orElseThrow().status())
                .isEqualTo("MERGED");
        assertThat(jdbc.queryForObject("""
                SELECT manual_rescan_requested_at IS NOT NULL FROM github_source_binding WHERE id = ?
                """, Boolean.class, sourceId)).isTrue();
        jdbc.update("""
                UPDATE github_sync_task
                SET status = 'ACQUISITION_COMPLETE', completed_at = ? WHERE id = ?
                """, Timestamp.from(now), headTaskId);
        jdbc.update("""
                UPDATE github_source_binding SET next_check_at = ? WHERE id = ?
                """, Timestamp.from(now.plusSeconds(86400)), sourceId);

        assertThat(sources.enqueueDue(now.plusSeconds(1), 10)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT mode FROM github_sync_task WHERE source_id = ? AND status = 'QUEUED'
                """, String.class, sourceId)).isEqualTo("FULL");
        assertThat(jdbc.queryForObject("""
                SELECT manual_rescan_requested_at IS NOT NULL FROM github_source_binding WHERE id = ?
                """, Boolean.class, sourceId)).isTrue();

        // A full task may become HEAD_ONLY at claim time if the backlog fills meanwhile.
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = NULL, global_low = NULL WHERE id = TRUE
                """);
        var ignores = new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper());
        var tasks = new JdbcGithubSyncTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc),
                new JdbcGithubSyncSourceCheckpointRepository(jdbc), ignores,
                new JdbcGithubSyncPathPresenceRepository(jdbc, ignores));
        String leaseHash = "d".repeat(64);
        var downgraded = tasks.claim(GithubSyncTaskKind.REPOSITORY_SYNC,
                "schedule-worker", leaseHash, now.plusSeconds(2), now.plusSeconds(300), platform(java.util.List.of())).orElseThrow();
        assertThat(downgraded.mode()).isEqualTo(GithubSyncTaskMode.HEAD_ONLY);
        assertThat(jdbc.queryForObject("""
                SELECT manual_rescan_requested_at IS NOT NULL FROM github_source_binding WHERE id = ?
                """, Boolean.class, sourceId)).isTrue();
        assertThat(tasks.completeHeadOnly(downgraded.taskId(), downgraded.leaseGeneration(),
                leaseHash, commit, now.plusSeconds(3), now.plusSeconds(86400))).isTrue();

        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 100, global_low = 50 WHERE id = TRUE
                """);
        assertThat(sources.enqueueDue(now.plusSeconds(4), 10)).isEqualTo(1);
        var full = tasks.claim(GithubSyncTaskKind.REPOSITORY_SYNC,
                "schedule-worker", "e".repeat(64), now.plusSeconds(5), now.plusSeconds(300), platform(java.util.List.of())).orElseThrow();
        assertThat(full.mode()).isEqualTo(GithubSyncTaskMode.FULL);
        assertThat(jdbc.queryForObject("""
                SELECT manual_rescan_requested_at FROM github_source_binding WHERE id = ?
                """, Timestamp.class, sourceId)).isNull();
    }

    @Test
    void categoryChangeUpdatesExistingAndNewEntriesWithoutDelayingEarlierWork() {
        long namespaceId = platformNamespaceId();
        long repositoryId = 847201L;
        jdbc.update("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility,
                     status, next_check_at)
                VALUES (?, ?, 'source', 'skills', 'PUBLIC', 'ACTIVE', CURRENT_TIMESTAMP + INTERVAL '20 days')
                """, namespaceId, repositoryId);
        jdbc.update("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility,
                     status, next_check_at)
                VALUES (?, ?, 'source', 'paused', 'PUBLIC', 'PAUSED', CURRENT_TIMESTAMP + INTERVAL '20 days')
                """, namespaceId, repositoryId + 1);
        jdbc.update("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility,
                     status, next_check_at)
                VALUES (?, ?, 'source', 'already-due', 'PUBLIC', 'ACTIVE', CURRENT_TIMESTAMP + INTERVAL '1 hour')
                """, namespaceId, repositoryId + 2);
        var entries = new JdbcGithubDiscoveryEntryRepository(jdbc, new ObjectMapper());
        var before = entries.createCuratedRepository("source", "initial-skill-repo");
        var owner = entries.createCuratedOwner("source-owner", 24 * 3600);
        Instant now = Instant.now();
        var repository = new JdbcGithubSyncSchedulePolicyRepository(jdbc);

        assertThat(repository.get().repositoryHours()).isNull();
        var updated = repository.update(6, 12, 48);

        assertThat(updated.repositoryHours()).isEqualTo(6);
        assertThat(updated.curatedOwnerHours()).isEqualTo(12);
        assertThat(updated.searchRuleHours()).isEqualTo(48);
        assertThat(jdbc.queryForObject("""
                SELECT target_interval_seconds FROM github_discovery_entry WHERE id = ?
                """, Integer.class, before.id())).isEqualTo(6 * 3600);
        assertThat(entries.createCuratedRepository("source", "next-skill-repo")
                .targetIntervalSeconds()).isEqualTo(6 * 3600);
        assertThat(jdbc.queryForObject("""
                SELECT target_interval_seconds FROM github_discovery_entry WHERE id = ?
                """, Integer.class, owner.id())).isEqualTo(12 * 3600);
        assertThat(jdbc.queryForObject("""
                SELECT target_interval_seconds FROM github_discovery_entry
                WHERE entry_type = 'SEARCH_RULE' LIMIT 1
                """, Integer.class)).isEqualTo(48 * 3600);
        assertThat(jdbc.queryForObject("""
                SELECT next_check_at FROM github_source_binding WHERE repository_id = ?
                """, Timestamp.class, repositoryId).toInstant()).isBefore(now.plusSeconds(7 * 3600));
        assertThat(jdbc.queryForObject("""
                SELECT next_check_at FROM github_source_binding WHERE repository_id = ?
                """, Timestamp.class, repositoryId + 1).toInstant()).isAfter(now.plusSeconds(19 * 86400));
        assertThat(jdbc.queryForObject("""
                SELECT next_check_at FROM github_source_binding WHERE repository_id = ?
                """, Timestamp.class, repositoryId + 2).toInstant()).isBefore(now.plusSeconds(2 * 3600));
    }

    @Test
    void rescanMergesQueuedAndRetryingTaskWithoutChangingRetryTime() {
        long namespaceId = platformNamespaceId();
        long repositoryId = 847203L;
        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility,
                     next_check_at)
                VALUES (?, ?, 'source', 'rescan', 'PUBLIC', CURRENT_TIMESTAMP + INTERVAL '20 days')
                RETURNING id
                """, Long.class, namespaceId, repositoryId);
        var sources = new JdbcGithubSourceTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc));
        Instant now = Instant.now();

        assertThat(sources.requestRescan(namespaceId, sourceId, now).orElseThrow().status())
                .isEqualTo("SCHEDULED");
        assertThat(sources.requestRescan(namespaceId + 1, sourceId, now)).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT next_check_at FROM github_source_binding WHERE id = ?
                """, Timestamp.class, sourceId).toInstant().toEpochMilli()).isEqualTo(now.toEpochMilli());
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        jdbc.update("INSERT INTO github_sync_run (id, namespace_id, kind) VALUES (?, ?, 'REPOSITORY_SYNC')",
                runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, source_id, kind, mode, dedupe_key,
                     snapshot_json, due_at)
                VALUES (?, ?, ?, ?, 'REPOSITORY_SYNC', 'HEAD_ONLY', ?, '{}'::jsonb, ?)
                """, taskId, runId, namespaceId, sourceId, "repository:" + repositoryId,
                Timestamp.from(now.plusSeconds(3600)));

        var merged = sources.requestRescan(namespaceId, sourceId, now).orElseThrow();
        assertThat(merged.status()).isEqualTo("MERGED");
        assertThat(merged.taskId()).isEqualTo(taskId);
        assertThat(jdbc.queryForObject("SELECT priority FROM github_sync_task WHERE id = ?",
                Integer.class, taskId)).isEqualTo(200);
        Instant retryAt = now.plusSeconds(7200);
        jdbc.update("""
                UPDATE github_sync_task SET status = 'RETRY_WAIT', retry_at = ? WHERE id = ?
                """, Timestamp.from(retryAt), taskId);

        assertThat(sources.requestRescan(namespaceId, sourceId, now).orElseThrow().taskStatus())
                .isEqualTo("RETRY_WAIT");
        assertThat(jdbc.queryForObject("SELECT retry_at FROM github_sync_task WHERE id = ?",
                Timestamp.class, taskId).toInstant().toEpochMilli()).isEqualTo(retryAt.toEpochMilli());
    }

    @Test
    void sourceBatchAndLeasesGiveDueTeamSourceAPlaceAmongPlatformSources() {
        long platform = platformNamespaceId();
        jdbc.update("""
                INSERT INTO user_account (id, display_name) VALUES ('fairness-owner', 'Owner')
                ON CONFLICT (id) DO NOTHING
                """);
        long team = jdbc.queryForObject("""
                INSERT INTO namespace (slug, display_name, type, status, created_by)
                VALUES ('fairness-team', 'Team', 'TEAM', 'ACTIVE', 'fairness-owner') RETURNING id
                """, Long.class);
        for (int index = 0; index < 12; index++) {
            jdbc.update("""
                    INSERT INTO github_source_binding
                        (namespace_id, repository_id, owner_login, repository_name, visibility,
                         next_check_at)
                    VALUES (?, ?, 'platform', ?, 'PUBLIC', CURRENT_TIMESTAMP - INTERVAL '2 days')
                    """, platform, 900000L + index, "repo-" + index);
        }
        jdbc.update("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility,
                     next_check_at)
                VALUES (?, 910000, 'team', 'repo', 'NAMESPACE_ONLY', CURRENT_TIMESTAMP - INTERVAL '1 day')
                """, team);
        var capacity = new JdbcGithubSyncCapacityGate(jdbc);
        var scheduler = new JdbcGithubSourceTaskRepository(jdbc, new ObjectMapper(), capacity);
        Instant now = Instant.now();

        assertThat(scheduler.enqueueDue(now, 10)).isEqualTo(10);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_task
                WHERE namespace_id = ? AND status = 'QUEUED'
                """, Integer.class, team)).isEqualTo(1);

        var ignores = new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper());
        var tasks = new JdbcGithubSyncTaskRepository(jdbc, new ObjectMapper(), capacity,
                new JdbcGithubSyncSourceCheckpointRepository(jdbc), ignores,
                new JdbcGithubSyncPathPresenceRepository(jdbc, ignores));
        var first = tasks.claim(GithubSyncTaskKind.REPOSITORY_SYNC, "fairness-worker", "a".repeat(64),
                now, now.plusSeconds(300), platform(java.util.List.of())).orElseThrow();
        var second = tasks.claim(GithubSyncTaskKind.REPOSITORY_SYNC, "fairness-worker", "b".repeat(64),
                now.plusMillis(1), now.plusSeconds(300), platform(java.util.List.of())).orElseThrow();

        assertThat(java.util.List.of(first.namespaceId(), second.namespaceId()))
                .containsExactlyInAnyOrder(platform, team);
        assertThat(scheduler.enqueueDue(now.plusSeconds(86400), 10)).isEqualTo(3);
        assertThat(scheduler.enqueueDue(now.plusSeconds(172800), 10)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_task
                WHERE kind = 'REPOSITORY_SYNC' AND status IN ('QUEUED', 'LEASED', 'RETRY_WAIT')
                """, Integer.class)).isEqualTo(13);
    }

    @Test
    void syntheticThousandRegisteredSourcesScheduleOnceInBatches() {
        long namespaceId = platformNamespaceId();
        Instant now = Instant.now();
        String commit = "a".repeat(40);
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 20000, global_low = 10000,
                    namespace_high = 20000, namespace_low = 10000
                WHERE id = TRUE
                """);
        jdbc.update("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility,
                     last_observed_commit, next_check_at)
                SELECT ?, 3000000 + series.id, 'fixture-owner', 'registered-' || series.id,
                       'PUBLIC', ?, ?
                FROM generate_series(1, 1000) AS series(id)
                """, namespaceId, commit, Timestamp.from(now.minusSeconds(3600)));
        var scheduler = new JdbcGithubSourceTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc));

        long started = System.nanoTime();
        int scheduled = 0;
        for (int batch = 0; batch < 10; batch++) {
            scheduled += scheduler.enqueueDue(now, 100);
        }
        long elapsedMillis = java.time.Duration.ofNanos(System.nanoTime() - started).toMillis();
        assertThat(scheduled).isEqualTo(1000);
        assertThat(scheduler.enqueueDue(now.plusSeconds(86400), 100)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(DISTINCT task.source_id) FROM github_sync_task task
                WHERE task.namespace_id = ? AND task.kind = 'REPOSITORY_SYNC'
                  AND task.mode = 'FULL' AND task.status = 'QUEUED'
                """, Integer.class, namespaceId)).isEqualTo(1000);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_task task
                WHERE task.namespace_id = ? AND task.kind = 'REPOSITORY_SYNC'
                  AND task.status IN ('QUEUED', 'LEASED', 'RETRY_WAIT')
                """, Integer.class, namespaceId)).isEqualTo(1000);
        System.out.printf("GitHub registered source scheduling baseline: sources=1000, batches=10, elapsedMs=%d%n",
                elapsedMillis);
    }

    @Test
    void reclaimedRepositoryTaskReturnsReceivedAndRetryablePaths() {
        long namespaceId = platformNamespaceId();
        long repositoryId = 920001L;
        String commit = "a".repeat(40);
        String digest = "b".repeat(64);
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID admissionId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        UUID ingestionId = UUID.randomUUID();
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 100, global_low = 50, namespace_high = 50, namespace_low = 25
                WHERE id = TRUE
                """);
        jdbc.update("INSERT INTO github_sync_run (id, namespace_id, kind) VALUES (?, ?, 'REPOSITORY_SYNC')",
                runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, kind, mode, status, dedupe_key,
                     snapshot_json, fixed_commit_sha, lease_generation, lease_token_hash,
                     lease_expires_at, leased_by)
                VALUES (?, ?, ?, 'REPOSITORY_SYNC', 'FULL', 'LEASED', ?,
                        jsonb_build_object('repositoryId', ?::bigint, 'owner', 'example', 'name', 'repo'),
                        ?, 1, ?, CURRENT_TIMESTAMP - INTERVAL '1 minute', 'stopped-worker')
                """, taskId, runId, namespaceId, "repository:" + repositoryId,
                repositoryId, commit, "d".repeat(64));
        jdbc.update("""
                INSERT INTO github_sync_package_admission
                    (id, task_id, namespace_id, repository_id, commit_sha, skill_path, lease_generation,
                     status)
                VALUES (?, ?, ?, ?, ?, 'skills/received', 1, 'ACCEPTED')
                """, admissionId, taskId, namespaceId, repositoryId, commit);
        jdbc.update("""
                INSERT INTO github_sync_staged_upload
                    (id, admission_id, object_key, mode, ticket_hash, size_bytes, sha256,
                     status, expires_at)
                VALUES (?, ?, ?, 'LOCAL_STREAM', ?, 1, ?, 'ACCEPTED', CURRENT_TIMESTAMP)
                """, uploadId, admissionId, "resume/" + uploadId, digest, digest);
        jdbc.update("""
                INSERT INTO github_sync_package_ingestion
                    (id, task_id, admission_id, upload_id, namespace_id, repository_id,
                     commit_sha, skill_path, object_key, size_bytes, sha256, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'skills/received', ?, 1, ?, 'RECEIVED')
                """, ingestionId, taskId, admissionId, uploadId, namespaceId, repositoryId,
                commit, "resume/" + uploadId, digest);
        jdbc.update("""
                INSERT INTO github_sync_path_result (task_id, commit_sha, skill_path, outcome)
                VALUES (?, ?, 'skills/retry', 'RETRYABLE_FAILURE')
                """, taskId, commit);
        var capacity = new JdbcGithubSyncCapacityGate(jdbc);
        var ignores = new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper());
        var tasks = new JdbcGithubSyncTaskRepository(jdbc, new ObjectMapper(), capacity,
                new JdbcGithubSyncSourceCheckpointRepository(jdbc), ignores,
                new JdbcGithubSyncPathPresenceRepository(jdbc, ignores));
        Instant now = Instant.now();

        var claimed = tasks.claim(GithubSyncTaskKind.REPOSITORY_SYNC, "resume-worker",
                "c".repeat(64), now, now.plusSeconds(300), platform(java.util.List.of())).orElseThrow();

        assertThat(claimed.taskId()).isEqualTo(taskId);
        assertThat(claimed.leaseGeneration()).isEqualTo(2);
        assertThat(claimed.fixedCommitSha()).isEqualTo(commit);
        assertThat(claimed.acceptedPaths()).containsExactly("skills/received");
        assertThat(claimed.retryPaths()).containsExactly("skills/retry");
        assertThat(tasks.renew(taskId, 1, "d".repeat(64), now, now.plusSeconds(300)))
                .isEmpty();
    }

    @Test
    void pausedDiscoveryEntryIsNotReclaimedUntilEnabledAgain() {
        jdbc.update("INSERT INTO user_account (id, display_name) VALUES ('pause-claim-owner', 'Owner')");
        long namespaceId = jdbc.queryForObject("""
                INSERT INTO namespace (slug, display_name, type, status, created_by)
                VALUES ('pause-claim-team', 'Pause Claim Team', 'TEAM', 'ACTIVE', 'pause-claim-owner')
                RETURNING id
                """, Long.class);
        var entries = new JdbcGithubDiscoveryEntryRepository(jdbc, new ObjectMapper());
        var entry = entries.createTeamRepository(namespaceId, "pause-owner", "pause-repo",
                "NAMESPACE_ONLY");
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        jdbc.update("INSERT INTO github_sync_run (id, namespace_id, kind) VALUES (?, ?, 'DISCOVERY')",
                runId, entry.namespaceId());
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, kind, status, dedupe_key,
                     snapshot_json, due_at, retry_at)
                VALUES (?, ?, ?, 'DISCOVERY', 'RETRY_WAIT', ?,
                        jsonb_build_object('entryType', 'CURATED_REPOSITORY', 'ruleId', ?::text),
                        CURRENT_TIMESTAMP - INTERVAL '1 minute',
                        CURRENT_TIMESTAMP - INTERVAL '1 minute')
                """, taskId, runId, entry.namespaceId(), "discovery:" + entry.id(), entry.id().toString());
        var ignores = new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper());
        var tasks = new JdbcGithubSyncTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc),
                new JdbcGithubSyncSourceCheckpointRepository(jdbc), ignores,
                new JdbcGithubSyncPathPresenceRepository(jdbc, ignores));
        Instant now = Instant.now();

        entries.setTeamRepositoryEnabled(namespaceId, entry.id(), false);
        assertThat(tasks.claim(GithubSyncTaskKind.DISCOVERY, "paused-worker",
                "a".repeat(64), now, now.plusSeconds(300), platform(java.util.List.of()))).isEmpty();

        entries.setTeamRepositoryEnabled(namespaceId, entry.id(), true);
        assertThat(tasks.claim(GithubSyncTaskKind.DISCOVERY, "resumed-worker",
                "b".repeat(64), now, now.plusSeconds(300), platform(java.util.List.of())))
                .get().extracting(lease -> lease.taskId()).isEqualTo(taskId);
    }

    @Test
    void departedConfigurationAdministratorDoesNotOwnOrBlockTeamSourceWork() {
        jdbc.update("INSERT INTO user_account (id, display_name) VALUES ('departed-sync-admin', 'Admin')");
        long namespace = jdbc.queryForObject("""
                INSERT INTO namespace (slug, display_name, type, status, created_by)
                VALUES ('departed-sync-team', 'Team', 'TEAM', 'ACTIVE', 'departed-sync-admin') RETURNING id
                """, Long.class);
        jdbc.update("""
                INSERT INTO namespace_member (namespace_id, user_id, role)
                VALUES (?, 'departed-sync-admin', 'ADMIN')
                """, namespace);
        var entries = new JdbcGithubDiscoveryEntryRepository(jdbc, new ObjectMapper());
        var entry = entries.createTeamRepository(namespace, "public-owner", "skills", "NAMESPACE_ONLY");
        long source = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility, next_check_at)
                VALUES (?, 930007, 'public-owner', 'skills', 'NAMESPACE_ONLY', CURRENT_TIMESTAMP - INTERVAL '1 hour')
                RETURNING id
                """, Long.class, namespace);
        jdbc.update("INSERT INTO github_source_origin (source_id, origin_type, origin_key) VALUES (?, 'TEAM_CONFIG', ?)",
                source, entry.id().toString());
        jdbc.update("DELETE FROM namespace_member WHERE namespace_id = ? AND user_id = 'departed-sync-admin'", namespace);
        var capacity = new JdbcGithubSyncCapacityGate(jdbc);
        var scheduler = new JdbcGithubSourceTaskRepository(jdbc, new ObjectMapper(), capacity);
        Instant now = Instant.now();
        assertThat(scheduler.enqueueDue(now, 10)).isEqualTo(1);
        var ignores = new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper());
        var tasks = new JdbcGithubSyncTaskRepository(jdbc, new ObjectMapper(), capacity,
                new JdbcGithubSyncSourceCheckpointRepository(jdbc), ignores,
                new JdbcGithubSyncPathPresenceRepository(jdbc, ignores));
        var lease = tasks.claim(GithubSyncTaskKind.REPOSITORY_SYNC, "team-worker", "c".repeat(64),
                now, now.plusSeconds(300), java.util.List.of()).orElseThrow();
        assertThat(lease.namespaceId()).isEqualTo(namespace);
        assertThat(jdbc.queryForObject("SELECT source_id FROM github_sync_task WHERE id = ?",
                Long.class, lease.taskId())).isEqualTo(source);
        assertThat(jdbc.queryForObject("SELECT enabled FROM github_discovery_entry WHERE id = ?",
                Boolean.class, entry.id())).isTrue();
    }

    @Test
    void weeklyFullRecheckRateLimitKeepsFrozenWindowAndAcknowledgedPage() throws Exception {
        var mapper = new ObjectMapper();
        var entries = new JdbcGithubDiscoveryEntryRepository(jdbc, mapper);
        var entry = entries.createSearchRule("Weekly recheck", "{\"selectors\":[{\"kind\":\"KEYWORD\",\"value\":\"skills\",\"priority\":\"CORE\"}]}", 86400);
        entries.setEnabled(entry.id(), true);
        Instant now = Instant.now();
        Instant previous = now.minusSeconds(8 * 86400L);
        jdbc.update("UPDATE github_discovery_entry SET next_due_at = ? WHERE id = ?", Timestamp.from(previous), entry.id());
        assertThat(entries.enqueueDue(previous.plusSeconds(1), 10)).isEqualTo(1);
        jdbc.update("UPDATE github_sync_task SET status = 'ACQUISITION_COMPLETE' WHERE dedupe_key = ?", "discovery:" + entry.id());
        assertThat(entries.enqueueDue(now, 10)).isEqualTo(1);
        var ignores = new JdbcGithubSourceIgnoreRepository(jdbc, mapper);
        var tasks = new JdbcGithubSyncTaskRepository(jdbc, mapper, new JdbcGithubSyncCapacityGate(jdbc),
                new JdbcGithubSyncSourceCheckpointRepository(jdbc), ignores,
                new JdbcGithubSyncPathPresenceRepository(jdbc, ignores));
        String token = "a".repeat(64);
        var lease = tasks.claim(GithubSyncTaskKind.DISCOVERY, "weekly-worker", token,
                now.plusSeconds(1), now.plusSeconds(300), platform(java.util.List.of())).orElseThrow();
        String snapshot = jdbc.queryForObject("SELECT snapshot_json::text FROM github_sync_task WHERE id = ?", String.class, lease.taskId());
        assertThat(mapper.readTree(snapshot).path("searchWindow").path("mode").asText()).isEqualTo("FULL_RECHECK");
        var batch = new GithubDiscoveryBatch(null, "page-1", java.util.List.of(), 0, null);
        assertThat(tasks.recordDiscoveryBatch(lease.taskId(), lease.leaseGeneration(), token, batch,
                "b".repeat(64), now.plusSeconds(2)))
                .isEqualTo(GithubDiscoveryBatchResult.ACCEPTED);
        Instant retry = now.plusSeconds(60);
        assertThat(tasks.completeDiscovery(lease.taskId(), lease.leaseGeneration(), token, "page-1", false,
                retry, GithubSyncDeferralReason.RATE_LIMIT, now.plusSeconds(3)))
                .isEqualTo(GithubDiscoveryCompletionResult.RETRY_WAIT);
        assertThat(entries.enqueueDue(now.plusSeconds(86400), 10)).isZero();
        assertThat(tasks.claim(GithubSyncTaskKind.DISCOVERY, "early-worker", "c".repeat(64),
                now.plusSeconds(4), now.plusSeconds(300), platform(java.util.List.of()))).isEmpty();
        var resumed = tasks.claim(GithubSyncTaskKind.DISCOVERY, "resumed-weekly-worker", "d".repeat(64),
                retry.plusSeconds(1), retry.plusSeconds(300), platform(java.util.List.of())).orElseThrow();
        assertThat(resumed.taskId()).isEqualTo(lease.taskId());
        assertThat(resumed.leaseGeneration()).isEqualTo(lease.leaseGeneration() + 1);
        assertThat(jdbc.queryForObject("SELECT snapshot_json::text FROM github_sync_task WHERE id = ?", String.class, resumed.taskId())).isEqualTo(snapshot);
        assertThat(jdbc.queryForObject("SELECT discovery_checkpoint FROM github_sync_task WHERE id = ?", String.class, resumed.taskId())).isEqualTo("page-1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_sync_task WHERE dedupe_key = ?", Integer.class, "discovery:" + entry.id())).isEqualTo(2);
    }

    private long platformNamespaceId() {
        return jdbc.queryForObject("SELECT id FROM namespace WHERE slug = 'github' AND type = 'CATALOG'",
                Long.class);
    }
}
