package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.iflytek.skillhub.domain.event.ReviewApprovedEvent;
import com.iflytek.skillhub.domain.event.ReviewRejectedEvent;
import com.iflytek.skillhub.githubsync.GithubSyncPublicationRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncManualBindingRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncPublicationRepository;
import com.iflytek.skillhub.listener.GithubSyncReviewResolutionListener;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the production BEFORE_COMMIT listener after committed path moves, without mocking SQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JdbcGithubSyncPublicationRepository.class, JdbcGithubSyncManualBindingRepository.class,
        GithubSyncReviewResolutionListener.class})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GithubSyncPathMoveReviewTransactionTest {
    enum Move { AUTO, MANUAL }
    private static final String COMMIT = "a".repeat(40);
    private static final String DIGEST = "b".repeat(64);
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired JdbcGithubSyncPublicationRepository publications;
    @Autowired JdbcGithubSyncManualBindingRepository bindings;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactions;

    @ParameterizedTest
    @CsvSource({"AUTO, APPROVED", "AUTO, REJECTED", "MANUAL, APPROVED", "MANUAL, REJECTED"})
    void pendingReviewRemainsDecidableAfterSameContentMove(Move move, String decision) {
        Fixture fixture = fixture();
        move(fixture, move);
        decide(fixture, decision);
        assertThat(status("review_task", fixture.reviewId())).isEqualTo(decision);
        assertThat(status("github_sync_package_ingestion", fixture.original())).isEqualTo(decision);
        assertThat(jdbc.queryForObject("SELECT skill_path FROM github_sync_package_ingestion WHERE id=?", String.class, fixture.original())).isEqualTo("old/skill");
        assertThat(jdbc.queryForObject("SELECT github_sync_revision FROM skill_version WHERE id=?", Integer.class, fixture.versionId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review_task WHERE skill_id=?", Integer.class, fixture.skillId())).isEqualTo(1);
        if (decision.equals("APPROVED")) {
            assertThat(jdbc.queryForObject("SELECT latest_version_id FROM skill WHERE id=?", Long.class, fixture.skillId())).isEqualTo(fixture.versionId());
        }
    }

    @ParameterizedTest @EnumSource(Move.class)
    void rejectionStillAllowsExplicitRetryAtTheNewPath(Move move) {
        Fixture fixture = fixture();
        decide(fixture, "REJECTED");
        move(fixture, move);
        Boolean requested = new TransactionTemplate(transactions).execute(tx -> publications.requestRejectedRetry(
                fixture.namespaceId(), fixture.sourceId(), "new/skill", Instant.now()));
        assertThat(requested).isTrue();
        assertThat(jdbc.queryForObject("SELECT retry_requested_at IS NOT NULL FROM github_source_skill_binding WHERE skill_id=?", Boolean.class, fixture.skillId())).isTrue();
        assertThat(publications.currentTarget(claimFor(fixture.moved())).retryRequested()).isFalse();
        // A fresh acquisition after the explicit request is eligible to retry the same content.
        UUID fresh = new TransactionTemplate(transactions).execute(tx -> ingestion(newTask(fixture), fixture.namespaceId(), fixture.repositoryId(), "new/skill", DIGEST));
        assertThat(publications.currentTarget(claimFor(fresh)).retryRequested()).isTrue();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            long retryVersion = jdbc.queryForObject("INSERT INTO skill_version(skill_id,version,status,github_sync_ingestion_id) VALUES (?,'0.0.0-retry','PENDING_REVIEW',?) RETURNING id", Long.class, fixture.skillId(), fresh);
            assertThat(publications.readyForReview(claimFor(fresh), fixture.skillId(), retryVersion, Instant.now())).isTrue();
            jdbc.update("INSERT INTO review_task(skill_version_id,skill_id,skill_version,namespace_id,subject_type,subject_id,subject_version_id,subject_version,submitted_by,origin_type,github_sync_ingestion_id) VALUES (?,?,'0.0.0-retry',?,'SKILL_VERSION',?,?,'0.0.0-retry','github-sync-publisher','GITHUB_SYNC',?)", retryVersion, fixture.skillId(), fixture.namespaceId(), fixture.skillId(), retryVersion, fresh);
            assertThat(jdbc.queryForObject("SELECT github_sync_revision FROM skill_version WHERE id=?", Integer.class, retryVersion)).isEqualTo(1);
        });
        assertThat(status("github_sync_package_ingestion", fresh)).isEqualTo("READY_FOR_REVIEW");
        assertThat(status("review_task", fixture.reviewId())).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review_task WHERE skill_id=? AND status='PENDING'", Integer.class, fixture.skillId())).isEqualTo(1);
    }

    @ParameterizedTest @EnumSource(Move.class)
    void newContentReplacesTargetAndStaleDecisionRollsBack(Move move) {
        Fixture fixture = fixture();
        move(fixture, move);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            // A -> B -> A must not revive the original A review merely because hashes match.
            for (String digest : new String[]{"d".repeat(64), DIGEST}) {
                UUID newer = ingestion(newTask(fixture), fixture.namespaceId(), fixture.repositoryId(), "new/skill", digest);
                long version = jdbc.queryForObject("INSERT INTO skill_version(skill_id,version,status,github_sync_ingestion_id) VALUES (?,?,'PENDING_REVIEW',?) RETURNING id", Long.class, fixture.skillId(), "0.0.0-new-" + digest.charAt(0), newer);
                assertThat(publications.readyForReview(claimFor(newer), fixture.skillId(), version, Instant.now())).isTrue();
            }
            jdbc.update("UPDATE review_task SET status='SUPERSEDED' WHERE id=?", fixture.reviewId());
        });
        assertThatThrownBy(() -> decide(fixture, "APPROVED"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no longer the latest target");
        assertThat(status("review_task", fixture.reviewId())).isEqualTo("SUPERSEDED");
        assertThat(status("github_sync_package_ingestion", fixture.original())).isEqualTo("SUPERSEDED");
        assertThat(jdbc.queryForObject("SELECT latest_revision FROM github_source_skill_binding WHERE skill_id=?", Integer.class, fixture.skillId())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT latest_version_id FROM skill WHERE id=?", Long.class, fixture.skillId())).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM skill_version WHERE id=?", String.class, fixture.versionId())).isEqualTo("PENDING_REVIEW");
    }

    private void decide(Fixture fixture, String decision) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.update("UPDATE review_task SET status=?,review_comment='Reviewed fixture' WHERE id=?", decision, fixture.reviewId());
            jdbc.update("UPDATE skill_version SET status=? WHERE id=?", decision.equals("APPROVED") ? "PUBLISHED" : "REJECTED", fixture.versionId());
            if (decision.equals("APPROVED")) {
                jdbc.update("UPDATE skill SET latest_version_id=? WHERE id=?", fixture.versionId(), fixture.skillId());
                events.publishEvent(new ReviewApprovedEvent(fixture.reviewId(), fixture.skillId(), fixture.versionId(), "reviewer", "github-sync-publisher"));
            } else {
                events.publishEvent(new ReviewRejectedEvent(fixture.reviewId(), fixture.skillId(), fixture.versionId(), "reviewer", "github-sync-publisher", "Reviewed fixture"));
            }
        });
    }

    private void move(Fixture fixture, Move mode) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            if (mode == Move.AUTO) {
                var claim = claimFor(fixture.moved());
                var decision = publications.inspectPathMove(claim);
                assertThat(decision.outcome()).isEqualTo(GithubSyncPublicationRepository.PathMoveOutcome.CANDIDATE);
                assertThat(publications.movePath(claim, decision, Instant.now())).isTrue();
            } else {
                jdbc.update("UPDATE github_sync_package_ingestion SET status='BLOCKED',last_error_code='PATH_MOVE_AMBIGUOUS' WHERE id=?", fixture.moved());
                bindings.bind(fixture.namespaceId(), fixture.moved(), fixture.skillId());
                assertThat(publications.noChange(claimFor(fixture.moved()), Instant.now())).isTrue();
            }
        });
        assertThat(jdbc.queryForObject("SELECT skill_path FROM github_source_skill_binding WHERE skill_id=?", String.class, fixture.skillId())).isEqualTo("new/skill");
        assertThat(jdbc.queryForObject("SELECT latest_ingestion_id FROM github_source_skill_binding WHERE skill_id=?", UUID.class, fixture.skillId())).isEqualTo(fixture.original());
        assertThat(status("github_sync_package_ingestion", fixture.moved())).isEqualTo("NO_CHANGE");
    }

    private Fixture fixture() {
        return new TransactionTemplate(transactions).execute(tx -> {
            String suffix = UUID.randomUUID().toString();
            long namespace = jdbc.queryForObject("INSERT INTO namespace(slug,display_name,type,created_by) VALUES (?,'Team','TEAM','github-sync-publisher') RETURNING id", Long.class, "move-" + suffix);
            long skill = jdbc.queryForObject("INSERT INTO skill(namespace_id,slug,owner_id,visibility,github_sync_managed) VALUES (?,'skill','github-sync-publisher','NAMESPACE_ONLY',TRUE) RETURNING id", Long.class, namespace);
            long repository = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1000000000) + 1;
            long source = jdbc.queryForObject("INSERT INTO github_source_binding(namespace_id,repository_id,owner_login,repository_name,visibility) VALUES (?,?,'fixture','skills','NAMESPACE_ONLY') RETURNING id", Long.class, namespace, repository);
            UUID run = UUID.randomUUID(), task = UUID.randomUUID();
            jdbc.update("INSERT INTO github_sync_run(id,namespace_id,kind) VALUES (?,?,'REPOSITORY_SYNC')", run, namespace);
            jdbc.update("INSERT INTO github_sync_task(id,run_id,namespace_id,source_id,kind,mode,status,fixed_commit_sha,dedupe_key,snapshot_json) VALUES (?,?,?,?,'REPOSITORY_SYNC','FULL','ACQUISITION_COMPLETE',?,?,'{}')", task, run, namespace, source, COMMIT, suffix);
            UUID original = ingestion(task, namespace, repository, "old/skill", DIGEST);
            long version = jdbc.queryForObject("INSERT INTO skill_version(skill_id,version,status,github_sync_ingestion_id,github_sync_revision) VALUES (?,'0.0.0-original','PENDING_REVIEW',?,1) RETURNING id", Long.class, skill, original);
            jdbc.update("UPDATE github_sync_package_ingestion SET status='READY_FOR_REVIEW',publication_token=NULL,publication_expires_at=NULL,published_skill_id=?,published_version_id=? WHERE id=?", skill, version, original);
            long review = jdbc.queryForObject("INSERT INTO review_task(skill_version_id,skill_id,skill_version,namespace_id,subject_type,subject_id,subject_version_id,subject_version,submitted_by,origin_type,github_sync_ingestion_id) VALUES (?,?,'0.0.0-original',?,'SKILL_VERSION',?,?,'0.0.0-original','github-sync-publisher','GITHUB_SYNC',?) RETURNING id", Long.class, version, skill, namespace, skill, version, original);
            jdbc.update("INSERT INTO github_source_skill_binding(source_id,namespace_id,repository_id,skill_path,skill_id,latest_content_sha256,latest_ingestion_id,latest_revision) VALUES (?,?,?,'old/skill',?,?,?,1)", source, namespace, repository, skill, DIGEST, original);
            // The complete new scan no longer observes the original path.
            jdbc.update("DELETE FROM github_sync_path_result WHERE task_id=?", task);
            UUID moved = ingestion(task, namespace, repository, "new/skill", DIGEST);
            return new Fixture(namespace, repository, source, task, original, moved, skill, version, review);
        });
    }

    private UUID newTask(Fixture fixture) {
        UUID run = UUID.randomUUID(), task = UUID.randomUUID();
        jdbc.update("INSERT INTO github_sync_run(id,namespace_id,kind) VALUES (?,?,'REPOSITORY_SYNC')", run, fixture.namespaceId());
        jdbc.update("INSERT INTO github_sync_task(id,run_id,namespace_id,source_id,kind,mode,status,fixed_commit_sha,dedupe_key,snapshot_json) VALUES (?,?,?,?,'REPOSITORY_SYNC','FULL','ACQUISITION_COMPLETE',?,?,'{}')", task, run, fixture.namespaceId(), fixture.sourceId(), COMMIT, task.toString());
        return task;
    }

    private UUID ingestion(UUID task, long namespace, long repository, String path, String digest) {
        UUID admission = UUID.randomUUID(), upload = UUID.randomUUID(), ingestion = UUID.randomUUID(), token = UUID.randomUUID();
        jdbc.update("INSERT INTO github_sync_package_admission(id,task_id,namespace_id,repository_id,commit_sha,skill_path,lease_generation) VALUES (?,?,?,?,?,?,1)", admission, task, namespace, repository, COMMIT, path);
        jdbc.update("INSERT INTO github_sync_staged_upload(id,admission_id,object_key,mode,ticket_hash,size_bytes,sha256,status,expires_at) VALUES (?,?,?,'LOCAL_STREAM',?,1,?,'ACCEPTED',now()+interval '1 day')", upload, admission, upload.toString(), DIGEST, digest);
        jdbc.update("INSERT INTO github_sync_package_ingestion(id,task_id,admission_id,upload_id,namespace_id,repository_id,commit_sha,skill_path,object_key,size_bytes,sha256,status,content_sha256,validated_at,publication_token,publication_expires_at) VALUES (?,?,?,?,?,?,?,?,?,1,?,'VALIDATED',?,now(),?,now()+interval '1 hour')", ingestion, task, admission, upload, namespace, repository, COMMIT, path, upload.toString(), digest, digest, token);
        jdbc.update("INSERT INTO github_sync_path_result(task_id,commit_sha,skill_path,outcome,ingestion_id) VALUES (?,?,?,'ACCEPTED',?) ON CONFLICT(task_id,commit_sha,skill_path) DO UPDATE SET ingestion_id=EXCLUDED.ingestion_id", task, COMMIT, path, ingestion);
        return ingestion;
    }

    private GithubSyncPublicationRepository.ClaimedPublication claimFor(UUID ingestion) {
        return jdbc.query("SELECT i.*,s.id AS source_id FROM github_sync_package_ingestion i JOIN github_source_binding s ON s.namespace_id=i.namespace_id AND s.repository_id=i.repository_id WHERE i.id=?", result -> {
            result.next();
            return new GithubSyncPublicationRepository.ClaimedPublication(ingestion, result.getObject("publication_token", UUID.class), result.getLong("namespace_id"), "fixture", result.getLong("repository_id"), result.getLong("source_id"), result.getString("skill_path"), "ACTIVE", "NAMESPACE_ONLY", result.getString("object_key"), 1, result.getString("sha256"), result.getString("content_sha256"), null, 1);
        }, ingestion);
    }
    private String status(String table, Object id) {
        return jdbc.queryForObject("SELECT status FROM " + table + " WHERE id=?", String.class, id);
    }
    private record Fixture(long namespaceId, long repositoryId, long sourceId, UUID taskId,
                           UUID original, UUID moved, long skillId, long versionId, long reviewId) {}
}
