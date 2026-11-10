package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.iflytek.skillhub.githubsync.GithubSyncPublicationRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncPublicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JdbcGithubSyncPublicationRepository.class)
@Testcontainers
class GithubSyncPathMoveRepositoryTest {
    private static final String COMMIT = "a".repeat(40);
    private static final String DIGEST = "b".repeat(64);

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
    @Autowired private JdbcGithubSyncPublicationRepository publications;

    @Test
    void movesOnlyAfterCompleteScanAndPreservesSkillIdentity() {
        Fixture fixture = fixture("move-a");
        var claim = claim();
        assertThat(publications.inspectPathMove(claim).outcome())
                .isEqualTo(GithubSyncPublicationRepository.PathMoveOutcome.RETRY);

        complete(fixture);
        var decision = publications.inspectPathMove(claim);
        assertThat(decision.outcome()).isEqualTo(GithubSyncPublicationRepository.PathMoveOutcome.CANDIDATE);
        assertThat(decision.skillId()).isEqualTo(fixture.skillId());
        assertThat(decision.previousPath()).isEqualTo("old/skill");
        assertThat(publications.movePath(claim, decision, Instant.now())).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT skill_path FROM github_source_skill_binding WHERE skill_id = ?
                """, String.class, fixture.skillId())).isEqualTo("new/skill");
        assertThat(jdbc.queryForObject("""
                SELECT published_skill_id FROM github_sync_package_ingestion WHERE id = ?
                """, Long.class, fixture.ingestionId())).isEqualTo(fixture.skillId());
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_package_ingestion WHERE id = ?
                """, String.class, fixture.ingestionId())).isEqualTo("NO_CHANGE");
    }

    @Test
    void copyAtNewPathDoesNotMoveExistingPath() {
        Fixture fixture = fixture("move-b");
        complete(fixture);
        jdbc.update("""
                INSERT INTO github_sync_path_result (task_id, commit_sha, skill_path, outcome)
                VALUES (?, ?, 'old/skill', 'UNCHANGED')
                """, fixture.taskId(), COMMIT);

        assertThat(publications.inspectPathMove(claim()).outcome())
                .isEqualTo(GithubSyncPublicationRepository.PathMoveOutcome.NONE);
    }

    @Test
    void twoNewPathsWithSameContentRemainAmbiguous() {
        Fixture fixture = fixture("move-c");
        addIngestion(fixture.taskId(), fixture.namespaceId(), fixture.repositoryId(), "other/skill");
        complete(fixture);

        assertThat(publications.inspectPathMove(claim()).outcome())
                .isEqualTo(GithubSyncPublicationRepository.PathMoveOutcome.AMBIGUOUS);
    }

    @Test
    void terminalPartialScanDoesNotInferPathMove() {
        Fixture fixture = fixture("move-d");
        jdbc.update("UPDATE github_sync_task SET status = 'PARTIAL' WHERE id = ?", fixture.taskId());

        assertThat(publications.inspectPathMove(claim()).outcome())
                .isEqualTo(GithubSyncPublicationRepository.PathMoveOutcome.INCOMPLETE);
    }

    @Test
    void terminalFailureInAnotherPathDoesNotBlockObservedNewSkill() {
        Fixture fixture = fixture("move-partial-observed");
        jdbc.update("""
                UPDATE github_sync_task SET status = 'PARTIAL', fixed_commit_sha = ?,
                    completed_at = CURRENT_TIMESTAMP, incomplete_subtrees = '[]'::jsonb
                WHERE id = ?
                """, COMMIT, fixture.taskId());
        jdbc.update("""
                INSERT INTO github_sync_path_result (task_id, commit_sha, skill_path, outcome)
                VALUES (?, ?, 'old/skill', 'UNCHANGED')
                """, fixture.taskId(), COMMIT);
        jdbc.update("""
                INSERT INTO github_sync_path_result (task_id, commit_sha, skill_path, outcome, reason)
                VALUES (?, ?, 'failed/skill', 'TERMINAL_FAILURE', 'PACKAGE_TOO_LARGE')
                """, fixture.taskId(), COMMIT);

        assertThat(publications.inspectPathMove(claim()).outcome())
                .isEqualTo(GithubSyncPublicationRepository.PathMoveOutcome.NONE);
    }

    @Test
    void activeScanAllowsNewSkillAfterEveryExistingBindingWasObserved() {
        Fixture fixture = fixture("move-active-observed");
        jdbc.update("""
                UPDATE github_sync_task
                SET status = 'LEASED', lease_generation = 1,
                    lease_token_hash = ?, lease_expires_at = CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    leased_by = 'path-move-test'
                WHERE id = ?
                """, "c".repeat(64), fixture.taskId());
        jdbc.update("""
                INSERT INTO github_sync_path_result (task_id, commit_sha, skill_path, outcome)
                VALUES (?, ?, 'old/skill', 'UNCHANGED')
                """, fixture.taskId(), COMMIT);

        assertThat(publications.inspectPathMove(claim()).outcome())
                .isEqualTo(GithubSyncPublicationRepository.PathMoveOutcome.NONE);
    }

    @Test
    void backpressuredScanAllowsObservedNewSkillButWaitsForMissingOldPath() {
        Fixture fixture = fixture("move-paused-observed");
        jdbc.update("UPDATE github_sync_task SET status = 'RETRY_WAIT' WHERE id = ?", fixture.taskId());
        var publication = claim();

        assertThat(publications.inspectPathMove(publication).outcome())
                .isEqualTo(GithubSyncPublicationRepository.PathMoveOutcome.RETRY);
        jdbc.update("""
                INSERT INTO github_sync_path_result (task_id, commit_sha, skill_path, outcome)
                VALUES (?, ?, 'old/skill', 'UNCHANGED')
                """, fixture.taskId(), COMMIT);
        assertThat(publications.inspectPathMove(publication).outcome())
                .isEqualTo(GithubSyncPublicationRepository.PathMoveOutcome.NONE);
    }

    @Test
    void missingOldPathWithDifferentContentRequiresConfirmation() {
        Fixture fixture = fixture("move-e");
        jdbc.update("""
                UPDATE github_sync_package_ingestion SET content_sha256 = ? WHERE id = ?
                """, "d".repeat(64), fixture.ingestionId());
        complete(fixture);

        assertThat(publications.inspectPathMove(claim()).outcome())
                .isEqualTo(GithubSyncPublicationRepository.PathMoveOutcome.AMBIGUOUS);
    }

    @Test
    void identicalContentInAnotherNamespaceCannotBecomeMoveCandidate() {
        Fixture fixture = fixture("move-f");
        var claim = claim();
        jdbc.update("DELETE FROM github_source_skill_binding WHERE skill_id = ?", fixture.skillId());
        fixture("move-g");
        complete(fixture);

        assertThat(publications.inspectPathMove(claim).outcome())
                .isEqualTo(GithubSyncPublicationRepository.PathMoveOutcome.NONE);
    }

    @Test
    void changedRulesMakeTheOldSnapshotIneligibleForAutomaticMove() {
        Fixture fixture = fixture("move-h");
        complete(fixture);
        jdbc.update("""
                UPDATE github_source_binding SET ignore_patterns = '["vendor"]'::jsonb
                WHERE namespace_id = ? AND repository_id = ?
                """, fixture.namespaceId(), fixture.repositoryId());

        assertThat(publications.inspectPathMove(claim()).outcome())
                .isEqualTo(GithubSyncPublicationRepository.PathMoveOutcome.STALE);
    }

    private GithubSyncPublicationRepository.ClaimedPublication claim() {
        return publications.claim(UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(300))
                .orElseThrow();
    }

    private void complete(Fixture fixture) {
        jdbc.update("""
                UPDATE github_sync_task
                SET status = 'ACQUISITION_COMPLETE', fixed_commit_sha = ?
                WHERE id = ?
                """, COMMIT, fixture.taskId());
    }

    private Fixture fixture(String namespaceSlug) {
        jdbc.update("""
                INSERT INTO user_account (id, display_name) VALUES ('path-move-owner', 'Owner')
                ON CONFLICT (id) DO NOTHING
                """);
        Long namespaceId = jdbc.queryForObject("""
                INSERT INTO namespace (slug, display_name, type, status, created_by)
                VALUES (?, 'Team', 'TEAM', 'ACTIVE', 'path-move-owner') RETURNING id
                """, Long.class, namespaceSlug);
        Long skillId = jdbc.queryForObject("""
                INSERT INTO skill (namespace_id, slug, owner_id, visibility, status)
                VALUES (?, 'skill', 'path-move-owner', 'NAMESPACE_ONLY', 'ACTIVE') RETURNING id
                """, Long.class, namespaceId);
        long repositoryId = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1000000) + 1;
        Long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility)
                VALUES (?, ?, 'upstream', 'skills', 'NAMESPACE_ONLY') RETURNING id
                """, Long.class, namespaceId, repositoryId);
        jdbc.update("""
                INSERT INTO github_source_skill_binding
                    (source_id, namespace_id, repository_id, skill_path, skill_id, latest_content_sha256)
                VALUES (?, ?, ?, 'old/skill', ?, ?)
                """, sourceId, namespaceId, repositoryId, skillId, DIGEST);
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        jdbc.update("INSERT INTO github_sync_run (id, namespace_id, kind) VALUES (?, ?, 'REPOSITORY_SYNC')",
                runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, source_id, kind, mode, dedupe_key, snapshot_json)
                VALUES (?, ?, ?, ?, 'REPOSITORY_SYNC', 'FULL', ?, '{}'::jsonb)
                """, taskId, runId, namespaceId, sourceId, namespaceSlug);
        UUID ingestionId = addIngestion(taskId, namespaceId, repositoryId, "new/skill");
        return new Fixture(namespaceId, repositoryId, taskId, ingestionId, skillId);
    }

    private UUID addIngestion(UUID taskId, long namespaceId, long repositoryId, String path) {
        UUID admissionId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        UUID ingestionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO github_sync_package_admission
                    (id, task_id, namespace_id, repository_id, commit_sha, skill_path, lease_generation)
                VALUES (?, ?, ?, ?, ?, ?, 1)
                """, admissionId, taskId, namespaceId, repositoryId, COMMIT, path);
        jdbc.update("""
                INSERT INTO github_sync_staged_upload
                    (id, admission_id, object_key, mode, ticket_hash, size_bytes, sha256,
                     status, expires_at)
                VALUES (?, ?, ?, 'LOCAL_STREAM', ?, 1, ?, 'ACCEPTED', CURRENT_TIMESTAMP + INTERVAL '1 day')
                """, uploadId, admissionId, "path-move/" + uploadId, "c".repeat(64), DIGEST);
        jdbc.update("""
                INSERT INTO github_sync_package_ingestion
                    (id, task_id, admission_id, upload_id, namespace_id, repository_id,
                     commit_sha, skill_path, object_key, size_bytes, sha256,
                     status, content_sha256, validated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, 'VALIDATED', ?, CURRENT_TIMESTAMP)
                """, ingestionId, taskId, admissionId, uploadId, namespaceId, repositoryId,
                COMMIT, path, "path-move/" + uploadId, DIGEST, DIGEST);
        jdbc.update("""
                INSERT INTO github_sync_path_result
                    (task_id, commit_sha, skill_path, outcome, ingestion_id)
                VALUES (?, ?, ?, 'ACCEPTED', ?)
                """, taskId, COMMIT, path, ingestionId);
        return ingestionId;
    }

    private record Fixture(long namespaceId, long repositoryId, UUID taskId,
                           UUID ingestionId, long skillId) {}
}
