package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.githubsync.GithubRepositoryCompletionResult;
import com.iflytek.skillhub.githubsync.GithubRepositoryPathResult;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSourceIgnoreRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncCapacityGate;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncPathPresenceRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class GithubSyncPathPresenceRepositoryTest {
    private static final String COMMIT = "a".repeat(40);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
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
    void completedPartialScanRecordsRevisionUntilIgnoreRulesChange() {
        Fixture fixture = fixture("presence-partial-checkpoint", "ACTIVE");
        UUID taskId = task(fixture, "QUEUED", List.of());
        String leaseHash = "e".repeat(64);
        Instant now = Instant.now();
        jdbc.update("""
                UPDATE github_sync_task
                SET status = 'LEASED', lease_generation = 1, lease_token_hash = ?,
                    lease_expires_at = ?, leased_by = 'presence-test'
                WHERE id = ?
                """, leaseHash, java.sql.Timestamp.from(now.plusSeconds(300)), taskId);
        var ignores = new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper());
        var tasks = new JdbcGithubSyncTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc),
                new JdbcGithubSyncSourceCheckpointRepository(jdbc), ignores,
                new JdbcGithubSyncPathPresenceRepository(jdbc, ignores));

        assertThat(tasks.completeRepository(taskId, 1, leaseHash, COMMIT,
                true, null, List.of(), List.of(new GithubRepositoryPathResult(
                        "large/skill", GithubRepositoryPathResult.Outcome.TERMINAL_FAILURE,
                        null, "PACKAGE_TOO_LARGE")), null, null, now, now.plusSeconds(86400)))
                .isEqualTo(GithubRepositoryCompletionResult.PARTIAL);
        assertThat(jdbc.queryForObject("""
                SELECT last_partial_commit FROM github_source_binding WHERE id = ?
                """, String.class, fixture.sourceId())).isEqualTo(COMMIT);
        assertThat(jdbc.queryForObject("""
                SELECT last_fully_processed_commit FROM github_source_binding WHERE id = ?
                """, String.class, fixture.sourceId())).isNull();

        assertThat(ignores.replace(fixture.namespaceId(), fixture.sourceId(), List.of("large")))
                .contains(List.of("large"));
        assertThat(jdbc.queryForObject("""
                SELECT last_partial_commit FROM github_source_binding WHERE id = ?
                """, String.class, fixture.sourceId())).isNull();
    }

    @Test
    void ignoreRuleChangedDuringScanKeepsSourceDueForNewFullScan() {
        Fixture fixture = fixture("presence-ignore-during-scan", "ACTIVE");
        UUID taskId = task(fixture, "QUEUED", List.of());
        String leaseHash = "e".repeat(64);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        jdbc.update("""
                UPDATE github_sync_task
                SET status = 'LEASED', lease_generation = 1, lease_token_hash = ?,
                    lease_expires_at = ?, leased_by = 'presence-test'
                WHERE id = ?
                """, leaseHash, java.sql.Timestamp.from(now.plusSeconds(300)), taskId);
        var ignores = new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper());
        assertThat(ignores.replace(fixture.namespaceId(), fixture.sourceId(), List.of("ignored")))
                .contains(List.of("ignored"));
        var tasks = new JdbcGithubSyncTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc),
                new JdbcGithubSyncSourceCheckpointRepository(jdbc), ignores,
                new JdbcGithubSyncPathPresenceRepository(jdbc, ignores));

        assertThat(tasks.completeRepository(taskId, 1, leaseHash, COMMIT,
                true, null, List.of(), List.of(new GithubRepositoryPathResult(
                        "large/skill", GithubRepositoryPathResult.Outcome.TERMINAL_FAILURE,
                        null, "PACKAGE_TOO_LARGE")), null, null, now, now.plusSeconds(86400)))
                .isEqualTo(GithubRepositoryCompletionResult.PARTIAL);
        assertThat(jdbc.queryForObject("""
                SELECT last_partial_commit FROM github_source_binding WHERE id = ?
                """, String.class, fixture.sourceId())).isNull();
        assertThat(jdbc.queryForObject("""
                SELECT next_check_at FROM github_source_binding WHERE id = ?
                """, java.sql.Timestamp.class, fixture.sourceId()).toInstant()).isEqualTo(now);
    }

    @Test
    void syntheticTenThousandPathResultsPersistAtContractLimit() {
        Fixture fixture = fixture("presence-capacity", "ACTIVE");
        UUID taskId = task(fixture, "QUEUED", List.of());
        String leaseHash = "f".repeat(64);
        Instant now = Instant.now();
        jdbc.update("""
                UPDATE github_sync_task
                SET status = 'LEASED', lease_generation = 1, lease_token_hash = ?,
                    lease_expires_at = ?, leased_by = 'presence-capacity'
                WHERE id = ?
                """, leaseHash, java.sql.Timestamp.from(now.plusSeconds(300)), taskId);
        var ignores = new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper());
        var tasks = new JdbcGithubSyncTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc),
                new JdbcGithubSyncSourceCheckpointRepository(jdbc), ignores,
                new JdbcGithubSyncPathPresenceRepository(jdbc, ignores));
        List<GithubRepositoryPathResult> results = IntStream.range(0, 10_000)
                .mapToObj(index -> new GithubRepositoryPathResult(
                        "skills/skill-%05d".formatted(index),
                        GithubRepositoryPathResult.Outcome.TERMINAL_FAILURE,
                        null, "PACKAGE_TOO_LARGE"))
                .toList();

        long started = System.nanoTime();
        var outcome = tasks.completeRepository(taskId, 1, leaseHash, COMMIT,
                true, results.getLast().skillPath(), List.of(), results,
                null, null, now, now.plusSeconds(86400));
        long elapsedMillis = java.time.Duration.ofNanos(System.nanoTime() - started).toMillis();
        assertThat(outcome).isEqualTo(GithubRepositoryCompletionResult.PARTIAL);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_path_result WHERE task_id = ?
                """, Integer.class, taskId)).isEqualTo(10_000);
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_task WHERE id = ?
                """, String.class, taskId)).isEqualTo("PARTIAL");
        System.out.printf("GitHub path completion baseline: paths=10000, elapsedMs=%d%n", elapsedMillis);
    }

    @Test
    void completeScanRecordsMissingAndLaterRecoveryWithoutChangingTheSkill() {
        Fixture fixture = fixture("presence-a", "ACTIVE");
        long skillId = binding(fixture, "old/demo", "demo");
        UUID firstSeenTask = task(fixture, "ACQUISITION_COMPLETE", List.of("old/demo"));
        var presence = repository();

        presence.reconcile(fixture.sourceId(), firstSeenTask, COMMIT, Instant.now());
        jdbc.update("UPDATE github_sync_task SET created_at = CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE id = ?",
                firstSeenTask);
        UUID missingTask = task(fixture, "ACQUISITION_COMPLETE", List.of());
        presence.reconcile(fixture.sourceId(), missingTask, COMMIT, Instant.now());
        assertThat(state(skillId)).isEqualTo("MISSING");
        assertThat(jdbc.queryForObject("""
                SELECT missing_since IS NOT NULL FROM github_source_skill_binding WHERE skill_id = ?
                """, Boolean.class, skillId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM skill WHERE id = ?", String.class, skillId))
                .isEqualTo("ACTIVE");
        var management = new GithubSyncManagementQueryRepository(jdbc,
                new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper()),
                new JdbcGithubSyncCapacityGate(jdbc));
        assertThat(management.skillPaths(fixture.namespaceId(), fixture.sourceId(), 0, 20)
                .items().getFirst().upstreamPresence()).isEqualTo("MISSING");

        jdbc.update("UPDATE github_sync_task SET created_at = CURRENT_TIMESTAMP - INTERVAL '12 hours' WHERE id = ?",
                missingTask);
        UUID recoveredTask = task(fixture, "ACQUISITION_COMPLETE", List.of("old/demo"));
        presence.reconcile(fixture.sourceId(), recoveredTask, COMMIT, Instant.now());
        assertThat(state(skillId)).isEqualTo("PRESENT");
        assertThat(management.skillPaths(fixture.namespaceId(), fixture.sourceId(), 0, 20)
                .items().getFirst().upstreamPresence()).isEqualTo("PRESENT");
        assertThat(jdbc.queryForObject("""
                SELECT missing_since IS NULL FROM github_source_skill_binding WHERE skill_id = ?
                """, Boolean.class, skillId)).isTrue();
    }

    @Test
    void partialScanAndPausedSourceNeverInferMissing() {
        Fixture fixture = fixture("presence-b", "ACTIVE");
        long skillId = binding(fixture, "old/demo", "demo");
        UUID partial = task(fixture, "PARTIAL", List.of());
        repository().reconcile(fixture.sourceId(), partial, COMMIT, Instant.now());
        assertThat(state(skillId)).isEqualTo("UNKNOWN");

        jdbc.update("UPDATE github_source_binding SET status = 'PAUSED' WHERE id = ?", fixture.sourceId());
        UUID complete = task(fixture, "ACQUISITION_COMPLETE", List.of());
        repository().reconcile(fixture.sourceId(), complete, COMMIT, Instant.now());
        assertThat(state(skillId)).isEqualTo("UNKNOWN");
    }

    @Test
    void changedIgnoreRulesAndIgnoredPathsNeverInferMissing() {
        Fixture fixture = fixture("presence-c", "ACTIVE");
        long ordinary = binding(fixture, "old/demo", "demo");
        long ignored = binding(fixture, "test/demo", "ignored");
        UUID task = task(fixture, "ACQUISITION_COMPLETE", List.of());
        jdbc.update("UPDATE github_source_binding SET ignore_patterns = '[\"vendor\"]'::jsonb WHERE id = ?",
                fixture.sourceId());
        repository().reconcile(fixture.sourceId(), task, COMMIT, Instant.now());
        assertThat(state(ordinary)).isEqualTo("UNKNOWN");

        jdbc.update("UPDATE github_source_binding SET ignore_patterns = '[]'::jsonb WHERE id = ?",
                fixture.sourceId());
        repository().reconcile(fixture.sourceId(), task, COMMIT, Instant.now());
        assertThat(state(ordinary)).isEqualTo("MISSING");
        assertThat(state(ignored)).isEqualTo("UNKNOWN");
    }

    @Test
    void olderSnapshotCannotOverwriteNewerPresence() {
        Fixture fixture = fixture("presence-d", "ACTIVE");
        long skillId = binding(fixture, "old/demo", "demo");
        UUID oldTask = task(fixture, "ACQUISITION_COMPLETE", List.of("old/demo"));
        jdbc.update("UPDATE github_sync_task SET created_at = CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE id = ?",
                oldTask);
        UUID newTask = task(fixture, "ACQUISITION_COMPLETE", List.of());

        repository().reconcile(fixture.sourceId(), newTask, COMMIT, Instant.now());
        repository().reconcile(fixture.sourceId(), oldTask, COMMIT, Instant.now());

        assertThat(state(skillId)).isEqualTo("MISSING");
    }

    @Test
    void completedScanRecoversUnreachableSourceWithoutRemovingPublishedSkill() {
        Fixture fixture = fixture("presence-e", "UNREACHABLE");
        long skillId = binding(fixture, "old/demo", "demo");
        UUID taskId = task(fixture, "ACQUISITION_COMPLETE", List.of());
        String leaseHash = "f".repeat(64);
        Instant now = Instant.now();
        jdbc.update("""
                UPDATE github_sync_task
                SET status = 'LEASED', lease_generation = 1, lease_token_hash = ?,
                    lease_expires_at = ?, leased_by = 'presence-test'
                WHERE id = ?
                """, leaseHash, java.sql.Timestamp.from(now.plusSeconds(300)), taskId);
        var ignores = new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper());
        var taskRepository = new JdbcGithubSyncTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc),
                new JdbcGithubSyncSourceCheckpointRepository(jdbc), ignores,
                new JdbcGithubSyncPathPresenceRepository(jdbc, ignores));

        var result = taskRepository.completeRepository(taskId, 1, leaseHash, COMMIT,
                true, null, List.of(), List.of(), null, null, now, now.plusSeconds(86400));

        assertThat(result).isEqualTo(GithubRepositoryCompletionResult.ACQUISITION_COMPLETE);
        assertThat(jdbc.queryForObject("SELECT status FROM github_source_binding WHERE id = ?",
                String.class, fixture.sourceId())).isEqualTo("ACTIVE");
        assertThat(state(skillId)).isEqualTo("MISSING");
        assertThat(jdbc.queryForObject("SELECT status FROM skill WHERE id = ?", String.class, skillId))
                .isEqualTo("ACTIVE");
    }

    @Test
    void failedPackageReleasesItsAdmissionOnlyWhenRepositoryCompletionIsValid() {
        Fixture fixture = fixture("presence-f", "ACTIVE");
        UUID taskId = task(fixture, "QUEUED", List.of());
        String leaseHash = "f".repeat(64);
        Instant now = Instant.now();
        jdbc.update("""
                UPDATE github_sync_task
                SET status = 'LEASED', lease_generation = 1, lease_token_hash = ?,
                    lease_expires_at = ?, leased_by = 'presence-test'
                WHERE id = ?
                """, leaseHash, java.sql.Timestamp.from(now.plusSeconds(300)), taskId);
        UUID failedAdmission = UUID.randomUUID();
        UUID unfinishedAdmission = UUID.randomUUID();
        for (var admission : List.of(
                java.util.Map.entry(failedAdmission, "failed/demo"),
                java.util.Map.entry(unfinishedAdmission, "unfinished/demo"))) {
            jdbc.update("""
                    INSERT INTO github_sync_package_admission
                        (id, task_id, namespace_id, repository_id, commit_sha, skill_path, lease_generation)
                    VALUES (?, ?, ?, 10001, ?, ?, 1)
                    """, admission.getKey(), taskId, fixture.namespaceId(), COMMIT, admission.getValue());
        }
        var ignores = new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper());
        var tasks = new JdbcGithubSyncTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc),
                new JdbcGithubSyncSourceCheckpointRepository(jdbc), ignores,
                new JdbcGithubSyncPathPresenceRepository(jdbc, ignores));
        var failedPath = new GithubRepositoryPathResult("failed/demo",
                GithubRepositoryPathResult.Outcome.TERMINAL_FAILURE, null, "ARCHIVE_TOO_LARGE");

        assertThat(tasks.completeRepository(taskId, 1, leaseHash, COMMIT,
                true, null, List.of(), List.of(failedPath), null, null,
                now, now.plusSeconds(86400))).isEqualTo(GithubRepositoryCompletionResult.COVERAGE_CONFLICT);
        assertThat(jdbc.queryForObject("SELECT status FROM github_sync_package_admission WHERE id = ?",
                String.class, failedAdmission)).isEqualTo("ACTIVE");

        var otherFailedPath = new GithubRepositoryPathResult("unfinished/demo",
                GithubRepositoryPathResult.Outcome.TERMINAL_FAILURE, null, "ARCHIVE_TOO_LARGE");
        assertThat(tasks.completeRepository(taskId, 1, leaseHash, COMMIT,
                true, null, List.of(), List.of(failedPath, otherFailedPath), null, null,
                now, now.plusSeconds(86400))).isEqualTo(GithubRepositoryCompletionResult.PARTIAL);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_admission
                WHERE task_id = ? AND status = 'ACTIVE'
                """, Integer.class, taskId)).isZero();
    }

    @Test
    void terminalRepositoryCompletionWakesPublicationsDeferredDuringScan() {
        Fixture fixture = fixture("presence-publication-wakeup", "ACTIVE");
        binding(fixture, "old/demo", "old-demo");
        UUID taskId = task(fixture, "QUEUED", List.of());
        String leaseHash = "f".repeat(64);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        jdbc.update("""
                UPDATE github_sync_task
                SET status = 'LEASED', lease_generation = 1, lease_token_hash = ?,
                    lease_expires_at = ?, leased_by = 'presence-test'
                WHERE id = ?
                """, leaseHash, java.sql.Timestamp.from(now.plusSeconds(300)), taskId);
        UUID admissionId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        UUID ingestionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO github_sync_package_admission
                    (id, task_id, namespace_id, repository_id, commit_sha, skill_path,
                     lease_generation, status)
                VALUES (?, ?, ?, 10001, ?, 'new/demo', 1, 'ACCEPTED')
                """, admissionId, taskId, fixture.namespaceId(), COMMIT);
        jdbc.update("""
                INSERT INTO github_sync_staged_upload
                    (id, admission_id, object_key, mode, size_bytes, sha256, status, expires_at)
                VALUES (?, ?, ?, 'S3_PRESIGNED_PUT', 1, ?, 'ACCEPTED', ?)
                """, uploadId, admissionId, "publication-wakeup/" + uploadId,
                "a".repeat(64), java.sql.Timestamp.from(now.plusSeconds(300)));
        Instant originalRetry = now.plusSeconds(900);
        jdbc.update("""
                INSERT INTO github_sync_package_ingestion
                    (id, task_id, admission_id, upload_id, namespace_id, repository_id,
                     commit_sha, skill_path, object_key, size_bytes, sha256, status,
                     last_error_code, publication_retry_at)
                VALUES (?, ?, ?, ?, ?, 10001, ?, 'new/demo', ?, 1, ?, 'VALIDATED',
                        'PATH_MOVE_SCAN_INCOMPLETE', ?)
                """, ingestionId, taskId, admissionId, uploadId, fixture.namespaceId(),
                COMMIT, "publication-wakeup/" + uploadId, "a".repeat(64),
                java.sql.Timestamp.from(originalRetry));
        jdbc.update("""
                INSERT INTO github_sync_path_result (task_id, commit_sha, skill_path, outcome)
                VALUES (?, ?, 'old/demo', 'UNCHANGED')
                """, taskId, COMMIT);
        var ignores = new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper());
        var tasks = new JdbcGithubSyncTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc),
                new JdbcGithubSyncSourceCheckpointRepository(jdbc), ignores,
                new JdbcGithubSyncPathPresenceRepository(jdbc, ignores));
        var failedPath = new GithubRepositoryPathResult("too-large/demo",
                GithubRepositoryPathResult.Outcome.TERMINAL_FAILURE, null, "PACKAGE_TOO_LARGE");

        assertThat(tasks.completeRepository(taskId, 1, "0".repeat(64), COMMIT,
                true, "too-large/demo", List.of(), List.of(failedPath), null, null,
                now, now.plusSeconds(86400))).isEqualTo(GithubRepositoryCompletionResult.LEASE_STALE);
        assertThat(jdbc.queryForObject("""
                SELECT publication_retry_at FROM github_sync_package_ingestion WHERE id = ?
                """, java.sql.Timestamp.class, ingestionId).toInstant()).isEqualTo(originalRetry);

        assertThat(tasks.completeRepository(taskId, 1, leaseHash, COMMIT,
                true, "too-large/demo", List.of(), List.of(failedPath), null, null,
                now, now.plusSeconds(86400))).isEqualTo(GithubRepositoryCompletionResult.PARTIAL);
        assertThat(jdbc.queryForObject("""
                SELECT publication_retry_at FROM github_sync_package_ingestion WHERE id = ?
                """, java.sql.Timestamp.class, ingestionId).toInstant()).isEqualTo(now);
    }

    @Test
    void excludingOneSkillPathPreservesSiblingAndPublishedIdentityAndCanResume() {
        Fixture fixture = fixture("presence-one-path-pause", "ACTIVE");
        long ignored = binding(fixture, "skills/paused", "paused");
        long sibling = binding(fixture, "skills/active", "active");
        long version = jdbc.queryForObject("""
                INSERT INTO skill_version (skill_id, version, status, created_by)
                VALUES (?, '1.0.0', 'PUBLISHED', 'presence-owner') RETURNING id
                """, Long.class, ignored);
        jdbc.update("UPDATE skill SET latest_version_id = ? WHERE id = ?", version, ignored);
        var ignores = new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper());
        assertThat(ignores.replace(fixture.namespaceId(), fixture.sourceId(), List.of("skills/paused")))
                .contains(List.of("skills/paused"));
        assertThat(ignores.ignoredForSource(fixture.sourceId(), "skills/paused")).isTrue();
        assertThat(ignores.ignoredForSource(fixture.sourceId(), "skills/active")).isFalse();
        UUID scan = task(fixture, "ACQUISITION_COMPLETE", List.of("skills/active"));
        jdbc.update("UPDATE github_sync_task SET snapshot_json = '{\"ignorePatterns\":[\"skills/paused\"]}'::jsonb WHERE id = ?", scan);
        repository().reconcile(fixture.sourceId(), scan, COMMIT, Instant.now());
        assertThat(state(ignored)).isEqualTo("UNKNOWN");
        assertThat(state(sibling)).isEqualTo("PRESENT");
        assertThat(jdbc.queryForObject("SELECT latest_version_id FROM skill WHERE id = ?",
                Long.class, ignored)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT status FROM skill_version WHERE id = ?",
                String.class, version)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT status FROM github_source_binding WHERE id = ?",
                String.class, fixture.sourceId())).isEqualTo("ACTIVE");
        ignores.replace(fixture.namespaceId(), fixture.sourceId(), List.of());
        assertThat(ignores.ignoredForSource(fixture.sourceId(), "skills/paused")).isFalse();
        UUID resumed = task(fixture, "ACQUISITION_COMPLETE", List.of("skills/paused", "skills/active"));
        repository().reconcile(fixture.sourceId(), resumed, COMMIT, Instant.now());
        assertThat(state(ignored)).isEqualTo("PRESENT");
        assertThat(jdbc.queryForObject("SELECT latest_version_id FROM skill WHERE id = ?",
                Long.class, ignored)).isEqualTo(version);
    }

    private JdbcGithubSyncPathPresenceRepository repository() {
        return new JdbcGithubSyncPathPresenceRepository(jdbc,
                new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper()));
    }

    private String state(long skillId) {
        return jdbc.queryForObject("""
                SELECT upstream_presence FROM github_source_skill_binding WHERE skill_id = ?
                """, String.class, skillId);
    }

    private Fixture fixture(String slug, String status) {
        jdbc.update("""
                INSERT INTO user_account (id, display_name) VALUES ('presence-owner', 'Owner')
                ON CONFLICT (id) DO NOTHING
                """);
        long namespaceId = jdbc.queryForObject("""
                INSERT INTO namespace (slug, display_name, type, status, created_by)
                VALUES (?, 'Team', 'TEAM', 'ACTIVE', 'presence-owner') RETURNING id
                """, Long.class, slug);
        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility, status)
                VALUES (?, 10001, 'upstream', 'skills', 'NAMESPACE_ONLY', ?) RETURNING id
                """, Long.class, namespaceId, status);
        return new Fixture(namespaceId, sourceId);
    }

    private long binding(Fixture fixture, String path, String slug) {
        long skillId = jdbc.queryForObject("""
                INSERT INTO skill (namespace_id, slug, owner_id, visibility, status)
                VALUES (?, ?, 'presence-owner', 'NAMESPACE_ONLY', 'ACTIVE') RETURNING id
                """, Long.class, fixture.namespaceId(), slug);
        jdbc.update("""
                INSERT INTO github_source_skill_binding
                    (source_id, namespace_id, repository_id, skill_path, skill_id)
                VALUES (?, ?, 10001, ?, ?)
                """, fixture.sourceId(), fixture.namespaceId(), path, skillId);
        return skillId;
    }

    private UUID task(Fixture fixture, String status, List<String> presentPaths) {
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO github_sync_run (id, namespace_id, kind)
                VALUES (?, ?, 'REPOSITORY_SYNC')
                """, runId, fixture.namespaceId());
        // These snapshots represent successive scheduler transactions. PostgreSQL now()
        // is constant inside this @DataJpaTest transaction, so give their chronology explicitly.
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, source_id, kind, mode, status,
                     dedupe_key, snapshot_json, fixed_commit_sha, created_at)
                VALUES (?, ?, ?, ?, 'REPOSITORY_SYNC', 'FULL', ?, ?,
                        '{"ignorePatterns":[]}'::jsonb, ?,
                        (SELECT COALESCE(MAX(created_at), CURRENT_TIMESTAMP) + INTERVAL '1 microsecond'
                         FROM github_sync_task WHERE source_id = ?))
                """, taskId, runId, fixture.namespaceId(), fixture.sourceId(),
                status, "presence:" + taskId, COMMIT, fixture.sourceId());
        for (String path : presentPaths) {
            jdbc.update("""
                    INSERT INTO github_sync_path_result (task_id, commit_sha, skill_path, outcome)
                    VALUES (?, ?, ?, 'UNCHANGED')
                    """, taskId, COMMIT, path);
        }
        return taskId;
    }

    private record Fixture(long namespaceId, long sourceId) {}
}
