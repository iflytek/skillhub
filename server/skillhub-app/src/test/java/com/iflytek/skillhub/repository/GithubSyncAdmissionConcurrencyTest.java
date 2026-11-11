package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.githubsync.GithubPackageAdmissionResult;
import com.iflytek.skillhub.githubsync.GithubSyncTaskKind;
import com.iflytek.skillhub.githubsync.GithubSyncTaskMode;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSourceTaskRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSourceIgnoreRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncCapacityGate;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncPathPresenceRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncSourceCheckpointRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncTaskRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class GithubSyncAdmissionConcurrencyTest {
    private static final String COMMIT = "a".repeat(40);
    private static final String LEASE_TOKEN_HASH = "b".repeat(64);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PostgresTestImage.name());

    @Test
    void concurrentReservationsRespectNamespaceAndGlobalHighWatermarks() throws Exception {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        long catalogId = jdbc.queryForObject(
                "SELECT id FROM namespace WHERE slug = 'github'", Long.class);
        long teamId = jdbc.queryForObject("""
                INSERT INTO namespace (slug, display_name, type, created_by)
                VALUES ('admission-team', 'Admission Team', 'TEAM', 'github-sync-publisher')
                RETURNING id
                """, Long.class);
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 3, global_low = 1, namespace_high = 1, namespace_low = 0
                WHERE id = TRUE
                """);
        UUID first = leasedTask(jdbc, catalogId, 940001);
        UUID second = leasedTask(jdbc, catalogId, 940002);

        List<GithubPackageAdmissionResult> namespaceResults = concurrentAdmit(
                dataSource, transactions, first, second);
        assertThat(namespaceResults.stream().filter(result ->
                result.status() == GithubPackageAdmissionResult.Status.ADMITTED)).hasSize(1);
        assertThat(namespaceResults.stream().filter(result ->
                result.status() == GithubPackageAdmissionResult.Status.BACKPRESSURE_ACTIVE)).hasSize(1);
        assertThat(jdbc.queryForObject("""
                SELECT paused FROM github_sync_namespace_admission_state WHERE namespace_id = ?
                """, Boolean.class, catalogId)).isTrue();

        UUID teamTask = leasedTask(jdbc, teamId, 940003);
        var teamResult = transactions.execute(status -> repository(new JdbcTemplate(dataSource))
                .admitPackage(teamTask, 1, LEASE_TOKEN_HASH, COMMIT, "skills/team", Instant.now()));
        assertThat(teamResult.status()).isEqualTo(GithubPackageAdmissionResult.Status.ADMITTED);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_admission WHERE status = 'ACTIVE'
                """, Integer.class)).isEqualTo(2);
        jdbc.update("""
                INSERT INTO github_discovery_candidate
                    (namespace_id, repository_id, owner_login, repository_name, status)
                VALUES (?, 940010, 'fixture', 'pending', 'PENDING'),
                       (?, 940011, 'fixture', 'rejected', 'REJECTED'),
                       (?, 940012, 'fixture', 'team-pending', 'PENDING')
                """, catalogId, catalogId, teamId);
        var overview = new GithubSyncManagementQueryRepository(jdbc,
                new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper()),
                new JdbcGithubSyncCapacityGate(jdbc)).admissionSnapshot();
        assertThat(overview.globalUnresolvedCount()).isEqualTo(2);
        assertThat(overview.platformUnresolvedCount()).isEqualTo(1);
        assertThat(overview.platformPendingCandidateCount()).isEqualTo(1);
        assertThat(overview.platformPaused()).isTrue();

        jdbc.update("UPDATE github_sync_package_admission SET status = 'RELEASED' WHERE status = 'ACTIVE'");
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 1, global_low = 0, namespace_high = 10, namespace_low = 0,
                    global_paused = FALSE WHERE id = TRUE
                """);
        jdbc.update("UPDATE github_sync_namespace_admission_state SET paused = FALSE");
        UUID globalFirst = leasedTask(jdbc, catalogId, 940004);
        UUID globalSecond = leasedTask(jdbc, teamId, 940005);

        List<GithubPackageAdmissionResult> globalResults = concurrentAdmit(
                dataSource, transactions, globalFirst, globalSecond);
        assertThat(globalResults.stream().filter(result ->
                result.status() == GithubPackageAdmissionResult.Status.ADMITTED)).hasSize(1);
        assertThat(globalResults.stream().filter(result ->
                result.status() == GithubPackageAdmissionResult.Status.BACKPRESSURE_ACTIVE)).hasSize(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_admission WHERE status = 'ACTIVE'
                """, Integer.class)).isEqualTo(1);

        assertThat(jdbc.queryForObject("""
                SELECT global_paused FROM github_sync_admission_policy WHERE id = TRUE
                """, Boolean.class)).isTrue();

        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility)
                VALUES (?, 940006, 'public-owner', 'registered', 'NAMESPACE_ONLY') RETURNING id
                """, Long.class, teamId);
        UUID registeredTask = queuedTask(jdbc, teamId, sourceId, 940006);
        UUID candidateTask = queuedTask(jdbc, teamId, null, 940007);
        var tasks = repository(new JdbcTemplate(dataSource));
        Instant claimTime = Instant.now();
        var headOnly = tasks.claim(GithubSyncTaskKind.REPOSITORY_SYNC, "head-worker",
                "c".repeat(64), claimTime, claimTime.plusSeconds(300));
        assertThat(headOnly).isPresent();
        assertThat(headOnly.orElseThrow().taskId()).isEqualTo(registeredTask);
        assertThat(headOnly.orElseThrow().mode()).isEqualTo(GithubSyncTaskMode.HEAD_ONLY);
        assertThat(tasks.claim(GithubSyncTaskKind.REPOSITORY_SYNC, "candidate-worker",
                "d".repeat(64), claimTime, claimTime.plusSeconds(300))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM github_sync_task WHERE id = ?",
                String.class, candidateTask)).isEqualTo("RETRY_WAIT");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_admission WHERE status = 'ACTIVE'
                """, Integer.class)).isEqualTo(1);

        String newestCommit = "e".repeat(40);
        assertThat(tasks.completeHeadOnly(registeredTask, 1, "c".repeat(64),
                newestCommit, claimTime.plusSeconds(1), claimTime.plusSeconds(86400))).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT last_observed_commit FROM github_source_binding WHERE id = ?
                """, String.class, sourceId)).isEqualTo(newestCommit);
        assertThat(jdbc.queryForObject("""
                SELECT last_fully_processed_commit FROM github_source_binding WHERE id = ?
                """, String.class, sourceId)).isNull();

        jdbc.update("UPDATE github_sync_package_admission SET status = 'RELEASED' WHERE status = 'ACTIVE'");
        var sourceScheduler = new JdbcGithubSourceTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc));
        assertThat(sourceScheduler.enqueueDue(claimTime.plusSeconds(2), 10)).isEqualTo(1);
        var resumed = tasks.claim(GithubSyncTaskKind.REPOSITORY_SYNC, "resumed-worker",
                "f".repeat(64), claimTime.plusSeconds(3), claimTime.plusSeconds(30 * 60)).orElseThrow();
        assertThat(resumed.mode()).isEqualTo(GithubSyncTaskMode.FULL);
        assertThat(resumed.sourceId()).isEqualTo(sourceId);
        Instant candidateRetry = claimTime.plusSeconds(20 * 60);
        jdbc.update("""
                UPDATE github_sync_task SET lease_expires_at = ? WHERE status = 'LEASED'
                """, Timestamp.from(candidateRetry.plusSeconds(3600)));
        var recheckedCandidate = tasks.claim(GithubSyncTaskKind.REPOSITORY_SYNC,
                "candidate-recheck-worker", "1".repeat(64), candidateRetry,
                candidateRetry.plusSeconds(300)).orElseThrow();
        assertThat(recheckedCandidate.taskId()).isEqualTo(candidateTask);
        assertThat(recheckedCandidate.mode()).isEqualTo(GithubSyncTaskMode.FULL);
        assertThat(recheckedCandidate.fixedCommitSha()).isNull();
    }

    private List<GithubPackageAdmissionResult> concurrentAdmit(DriverManagerDataSource dataSource,
            TransactionTemplate transactions, UUID first, UUID second) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(() -> {
                start.await();
                return transactions.execute(status -> repository(new JdbcTemplate(dataSource))
                        .admitPackage(first, 1, LEASE_TOKEN_HASH, COMMIT, "skills/first", Instant.now()));
            });
            var secondResult = executor.submit(() -> {
                start.await();
                return transactions.execute(status -> repository(new JdbcTemplate(dataSource))
                        .admitPackage(second, 1, LEASE_TOKEN_HASH, COMMIT, "skills/second", Instant.now()));
            });
            start.countDown();
            return List.of(firstResult.get(30, TimeUnit.SECONDS),
                    secondResult.get(30, TimeUnit.SECONDS));
        }
    }

    private UUID leasedTask(JdbcTemplate jdbc, long namespaceId, long repositoryId) {
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO github_sync_run (id, namespace_id, kind)
                VALUES (?, ?, 'REPOSITORY_SYNC')
                """, runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, kind, mode, status, dedupe_key,
                     snapshot_json, lease_generation, lease_token_hash, lease_expires_at, leased_by)
                VALUES (?, ?, ?, 'REPOSITORY_SYNC', 'FULL', 'LEASED', ?,
                        jsonb_build_object('repositoryId', ?::text, 'fixedCommitSha', ?::text),
                        1, ?, ?, 'admission-worker')
                """, taskId, runId, namespaceId, "repository:" + repositoryId,
                Long.toString(repositoryId), COMMIT, LEASE_TOKEN_HASH,
                Timestamp.from(Instant.now().plusSeconds(300)));
        return taskId;
    }

    private UUID queuedTask(JdbcTemplate jdbc, long namespaceId, Long sourceId, long repositoryId) {
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO github_sync_run (id, namespace_id, kind)
                VALUES (?, ?, 'REPOSITORY_SYNC')
                """, runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, source_id, kind, mode, status,
                     dedupe_key, snapshot_json)
                VALUES (?, ?, ?, ?, 'REPOSITORY_SYNC', 'FULL', 'QUEUED', ?,
                        jsonb_build_object('repositoryId', ?::text))
                """, taskId, runId, namespaceId, sourceId, "repository:" + repositoryId,
                Long.toString(repositoryId));
        return taskId;
    }

    private JdbcGithubSyncTaskRepository repository(JdbcTemplate jdbc) {
        var ignores = new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper());
        return new JdbcGithubSyncTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc),
                new JdbcGithubSyncSourceCheckpointRepository(jdbc), ignores,
                new JdbcGithubSyncPathPresenceRepository(jdbc, ignores));
    }
}
