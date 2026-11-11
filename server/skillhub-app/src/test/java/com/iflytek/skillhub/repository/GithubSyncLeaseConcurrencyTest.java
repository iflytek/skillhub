package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.support.PostgresTestImage;
import static com.iflytek.skillhub.support.GithubSyncWorkerCapabilities.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.githubsync.GithubDiscoveryBatch;
import com.iflytek.skillhub.githubsync.GithubDiscoveryBatchResult;
import com.iflytek.skillhub.githubsync.GithubDiscoveryCandidate;
import com.iflytek.skillhub.githubsync.GithubDiscoveryCompletionResult;
import com.iflytek.skillhub.githubsync.GithubSyncTaskKind;
import com.iflytek.skillhub.githubsync.GithubSyncTaskLease;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSourceIgnoreRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncCapacityGate;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncPathPresenceRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncSourceCheckpointRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncTaskRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class GithubSyncLeaseConcurrencyTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PostgresTestImage.name());

    @Test
    void concurrentWorkersClaimOnlyOneLeaseAndDiscoveryReplayDoesNotDuplicateRows() throws Exception {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        long namespaceId = jdbc.queryForObject(
                "SELECT id FROM namespace WHERE slug = 'github'", Long.class);
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        jdbc.update("INSERT INTO github_sync_run (id, namespace_id, kind) VALUES (?, ?, 'DISCOVERY')",
                runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, kind, status, dedupe_key, snapshot_json)
                VALUES (?, ?, ?, 'DISCOVERY', 'QUEUED', ?,
                        jsonb_build_object('entryType', 'SEARCH_RULE', 'ruleId', ?::text,
                                           'candidateBudget', 10))
                """, taskId, runId, namespaceId, "discovery:" + taskId, ruleId.toString());

        var firstRepository = repository(new JdbcTemplate(dataSource));
        var secondRepository = repository(new JdbcTemplate(dataSource));
        Instant now = Instant.now();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return firstRepository.claim(GithubSyncTaskKind.DISCOVERY, "worker-a",
                        "a".repeat(64), now, now.plusSeconds(300), platform(java.util.List.of()));
            });
            var second = executor.submit(() -> {
                start.await();
                return secondRepository.claim(GithubSyncTaskKind.DISCOVERY, "worker-b",
                        "b".repeat(64), now, now.plusSeconds(300), platform(java.util.List.of()));
            });
            start.countDown();
            Optional<GithubSyncTaskLease> firstLease = first.get(30, TimeUnit.SECONDS);
            Optional<GithubSyncTaskLease> secondLease = second.get(30, TimeUnit.SECONDS);
            assertThat(firstLease.isPresent() == secondLease.isPresent()).isFalse();
            GithubSyncTaskLease lease = firstLease.orElseGet(secondLease::orElseThrow);
            String token = firstLease.isPresent() ? "a".repeat(64) : "b".repeat(64);
            assertThat(lease.taskId()).isEqualTo(taskId);
            assertThat(lease.leaseGeneration()).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM github_sync_task
                    WHERE id = ? AND status = 'LEASED' AND lease_generation = 1
                    """, Integer.class, taskId)).isEqualTo(1);

            var batch = new GithubDiscoveryBatch(null, "page-1", List.of(
                    new GithubDiscoveryCandidate(930003, "public-owner", "skills", "main")),
                    0, null);
            assertThat(firstRepository.recordDiscoveryBatch(taskId, 1, token, batch,
                    "c".repeat(64), now)).isEqualTo(GithubDiscoveryBatchResult.ACCEPTED);
            assertThat(secondRepository.recordDiscoveryBatch(taskId, 1, token, batch,
                    "c".repeat(64), now)).isEqualTo(GithubDiscoveryBatchResult.ACCEPTED);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM github_discovery_batch WHERE task_id = ?",
                    Integer.class, taskId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM github_discovery_candidate
                    WHERE namespace_id = ? AND repository_id = 930003
                    """, Integer.class, namespaceId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM github_discovery_task_hit WHERE task_id = ?
                    """, Integer.class, taskId)).isEqualTo(1);

            Instant afterExpiry = now.plusSeconds(301);
            assertThat(firstRepository.renew(taskId, 1, token, afterExpiry,
                    afterExpiry.plusSeconds(300))).isEmpty();
            assertThat(firstRepository.recordDiscoveryBatch(taskId, 1, token, batch,
                    "c".repeat(64), afterExpiry)).isEqualTo(GithubDiscoveryBatchResult.LEASE_STALE);
            var replacement = secondRepository.claim(GithubSyncTaskKind.DISCOVERY, "worker-replacement",
                    "d".repeat(64), afterExpiry, afterExpiry.plusSeconds(300), platform(java.util.List.of())).orElseThrow();
            assertThat(replacement.taskId()).isEqualTo(taskId);
            assertThat(replacement.leaseGeneration()).isEqualTo(2);
            assertThat(firstRepository.completeDiscovery(taskId, 1, token, "page-1", true,
                    null, null, afterExpiry.plusSeconds(1)))
                    .isEqualTo(GithubDiscoveryCompletionResult.LEASE_STALE);
            assertThat(secondRepository.recordDiscoveryBatch(taskId, 2, "d".repeat(64), batch,
                    "c".repeat(64), afterExpiry.plusSeconds(1)))
                    .isEqualTo(GithubDiscoveryBatchResult.ACCEPTED);
            assertThat(secondRepository.completeDiscovery(taskId, 2, "d".repeat(64),
                    "page-1", true, null, null, afterExpiry.plusSeconds(1)))
                    .isEqualTo(GithubDiscoveryCompletionResult.ACQUISITION_COMPLETE);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM github_discovery_batch WHERE task_id = ?",
                    Integer.class, taskId)).isEqualTo(1);
        }
    }

    private JdbcGithubSyncTaskRepository repository(JdbcTemplate jdbc) {
        var ignores = new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper());
        return new JdbcGithubSyncTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc),
                new JdbcGithubSyncSourceCheckpointRepository(jdbc), ignores,
                new JdbcGithubSyncPathPresenceRepository(jdbc, ignores));
    }
}
