package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncManualBindingRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JdbcGithubSyncManualBindingRepository.class)
@Testcontainers
class GithubSyncManualBindingRepositoryTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PostgresTestImage.name());

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

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JdbcGithubSyncManualBindingRepository bindings;

    @Test
    void bindsOnlyTheChosenNamespaceSkillAndRetriesTheBlockedIngestion() {
        Fixture fixture = fixture("manual-a", 1001L, "agents/demo", "demo");

        var binding = bindings.bind(fixture.namespaceId(), fixture.ingestionId(), fixture.skillId());

        assertThat(binding.skillId()).isEqualTo(fixture.skillId());
        assertThat(binding.skillPath()).isEqualTo("agents/demo");
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_package_ingestion WHERE id = ?
                """, String.class, fixture.ingestionId())).isEqualTo("VALIDATED");
        assertThat(jdbc.queryForObject("""
                SELECT latest_content_sha256 FROM github_source_skill_binding
                WHERE namespace_id = ? AND skill_id = ?
                """, String.class, fixture.namespaceId(), fixture.skillId())).isNull();
        assertThrows(DomainBadRequestException.class,
                () -> bindings.bind(fixture.namespaceId(), fixture.ingestionId(), fixture.skillId()));
    }

    @Test
    void rejectsAnotherNamespaceAndAnUnrelatedSkillIdentity() {
        Fixture fixture = fixture("manual-b", 1002L, "agents/demo", "demo");
        Long otherSkillId = jdbc.queryForObject("""
                INSERT INTO skill (namespace_id, slug, owner_id, visibility, status)
                VALUES (?, 'other', 'manual-owner', 'NAMESPACE_ONLY', 'ACTIVE') RETURNING id
                """, Long.class, fixture.namespaceId());

        assertThrows(DomainNotFoundException.class,
                () -> bindings.bind(fixture.namespaceId() + 1000, fixture.ingestionId(), fixture.skillId()));
        assertThrows(DomainBadRequestException.class,
                () -> bindings.bind(fixture.namespaceId(), fixture.ingestionId(), otherSkillId));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_source_skill_binding WHERE namespace_id = ?
                """, Integer.class, fixture.namespaceId())).isZero();
    }

    @Test
    void confirmsMissingPathOnTheSameSourceAndRetriesIntoExistingSkill() {
        PathFixture fixture = pathFixture("manual-path-a", 1101L);

        var result = bindings.bind(fixture.namespaceId(), fixture.ingestionId(), fixture.skillId());

        assertThat(result.previousPath()).isEqualTo("old/demo");
        assertThat(result.skillPath()).isEqualTo("new/demo");
        assertThat(jdbc.queryForObject("""
                SELECT skill_path FROM github_source_skill_binding WHERE skill_id = ?
                """, String.class, fixture.skillId())).isEqualTo("new/demo");
        assertThat(jdbc.queryForObject("""
                SELECT status FROM github_sync_package_ingestion WHERE id = ?
                """, String.class, fixture.ingestionId())).isEqualTo("VALIDATED");
        assertThat(jdbc.queryForObject("""
                SELECT latest_content_sha256 FROM github_source_skill_binding WHERE skill_id = ?
                """, String.class, fixture.skillId())).isEqualTo("b".repeat(64));
    }

    @Test
    void pathConfirmationRejectsOtherNamespaceAndOldPathStillPresent() {
        PathFixture fixture = pathFixture("manual-path-b", 1102L);
        Fixture other = fixture("manual-other", 1103L, "other/demo", "demo");

        assertThrows(DomainBadRequestException.class,
                () -> bindings.bind(fixture.namespaceId(), fixture.ingestionId(), other.skillId()));
        jdbc.update("""
                INSERT INTO github_sync_path_result (task_id, commit_sha, skill_path, outcome)
                VALUES (?, ?, 'old/demo', 'UNCHANGED')
                """, fixture.taskId(), "a".repeat(40));
        assertThrows(DomainBadRequestException.class,
                () -> bindings.bind(fixture.namespaceId(), fixture.ingestionId(), fixture.skillId()));
        assertThat(jdbc.queryForObject("""
                SELECT skill_path FROM github_source_skill_binding WHERE skill_id = ?
                """, String.class, fixture.skillId())).isEqualTo("old/demo");
    }

    @Test
    void pathConfirmationRejectsPartialScan() {
        PathFixture fixture = pathFixture("manual-path-c", 1104L);
        jdbc.update("UPDATE github_sync_task SET status = 'PARTIAL' WHERE id = ?", fixture.taskId());

        assertThrows(DomainBadRequestException.class,
                () -> bindings.bind(fixture.namespaceId(), fixture.ingestionId(), fixture.skillId()));
    }

    @Test
    void changedContentKeepsPreviousFingerprintUntilPublication() {
        PathFixture fixture = pathFixture("manual-path-d", 1105L);
        jdbc.update("UPDATE github_sync_package_ingestion SET content_sha256 = ? WHERE id = ?",
                "d".repeat(64), fixture.ingestionId());

        bindings.bind(fixture.namespaceId(), fixture.ingestionId(), fixture.skillId());

        assertThat(jdbc.queryForObject("""
                SELECT latest_content_sha256 FROM github_source_skill_binding WHERE skill_id = ?
                """, String.class, fixture.skillId())).isEqualTo("b".repeat(64));
        assertThat(jdbc.queryForObject("""
                SELECT latest_ingestion_id FROM github_source_skill_binding WHERE skill_id = ?
                """, UUID.class, fixture.skillId())).isNull();
    }

    @Test
    void pathConfirmationRejectsAnotherSourceInSameNamespace() {
        PathFixture fixture = pathFixture("manual-path-e", 1106L);
        Long otherSkillId = jdbc.queryForObject("""
                INSERT INTO skill (namespace_id, slug, owner_id, visibility, status, github_sync_managed)
                VALUES (?, 'other', 'github-sync-publisher', 'NAMESPACE_ONLY', 'ACTIVE', TRUE)
                RETURNING id
                """, Long.class, fixture.namespaceId());
        Long otherSourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility)
                VALUES (?, 999999, 'other', 'skills', 'NAMESPACE_ONLY') RETURNING id
                """, Long.class, fixture.namespaceId());
        jdbc.update("""
                INSERT INTO github_source_skill_binding
                    (source_id, namespace_id, repository_id, skill_path, skill_id,
                     latest_content_sha256)
                VALUES (?, ?, 999999, 'old/other', ?, ?)
                """, otherSourceId, fixture.namespaceId(), otherSkillId, "b".repeat(64));

        assertThrows(DomainBadRequestException.class,
                () -> bindings.bind(fixture.namespaceId(), fixture.ingestionId(), otherSkillId));
    }

    @Test
    void pathConfirmationRejectsAConflictFromAnOlderFullScan() {
        PathFixture fixture = pathFixture("manual-path-f", 1107L);
        Long sourceId = jdbc.queryForObject("""
                SELECT source_id FROM github_sync_task WHERE id = ?
                """, Long.class, fixture.taskId());
        jdbc.update("UPDATE github_sync_task SET created_at = CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE id = ?",
                fixture.taskId());
        UUID runId = UUID.randomUUID();
        jdbc.update("INSERT INTO github_sync_run (id, namespace_id, kind) VALUES (?, ?, 'REPOSITORY_SYNC')",
                runId, fixture.namespaceId());
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, source_id, kind, mode, dedupe_key, snapshot_json)
                VALUES (?, ?, ?, ?, 'REPOSITORY_SYNC', 'FULL', 'new-scan', '{}'::jsonb)
                """, UUID.randomUUID(), runId, fixture.namespaceId(), sourceId);

        assertThrows(DomainBadRequestException.class,
                () -> bindings.bind(fixture.namespaceId(), fixture.ingestionId(), fixture.skillId()));
    }

    private PathFixture pathFixture(String suffix, long repositoryId) {
        Fixture fixture = fixture(suffix, repositoryId, "new/demo", "demo");
        Long sourceId = jdbc.queryForObject("""
                SELECT id FROM github_source_binding WHERE namespace_id = ? AND repository_id = ?
                """, Long.class, fixture.namespaceId(), repositoryId);
        UUID taskId = jdbc.queryForObject("""
                SELECT task_id FROM github_sync_package_ingestion WHERE id = ?
                """, UUID.class, fixture.ingestionId());
        jdbc.update("""
                UPDATE skill SET github_sync_managed = TRUE, owner_id = 'github-sync-publisher'
                WHERE id = ?
                """, fixture.skillId());
        jdbc.update("""
                INSERT INTO github_source_skill_binding
                    (source_id, namespace_id, repository_id, skill_path, skill_id,
                     latest_content_sha256)
                VALUES (?, ?, ?, 'old/demo', ?, ?)
                """, sourceId, fixture.namespaceId(), repositoryId, fixture.skillId(), "b".repeat(64));
        jdbc.update("""
                UPDATE github_sync_package_ingestion
                SET last_error_code = 'PATH_MOVE_AMBIGUOUS', target_skill_slug = NULL
                WHERE id = ?
                """, fixture.ingestionId());
        jdbc.update("""
                UPDATE github_sync_task
                SET status = 'ACQUISITION_COMPLETE', fixed_commit_sha = ? WHERE id = ?
                """, "a".repeat(40), taskId);
        jdbc.update("""
                INSERT INTO github_sync_path_result
                    (task_id, commit_sha, skill_path, outcome, ingestion_id)
                VALUES (?, ?, 'new/demo', 'ACCEPTED', ?)
                """, taskId, "a".repeat(40), fixture.ingestionId());
        return new PathFixture(fixture.namespaceId(), fixture.ingestionId(),
                fixture.skillId(), taskId);
    }

    private Fixture fixture(String suffix, long repositoryId, String path, String slug) {
        jdbc.update("""
                INSERT INTO user_account (id, display_name) VALUES ('manual-owner', 'Owner')
                ON CONFLICT (id) DO NOTHING
                """);
        Long namespaceId = jdbc.queryForObject("""
                INSERT INTO namespace (slug, display_name, type, status, created_by)
                VALUES (?, 'Team', 'TEAM', 'ACTIVE', 'manual-owner') RETURNING id
                """, Long.class, suffix);
        Long skillId = jdbc.queryForObject("""
                INSERT INTO skill (namespace_id, slug, owner_id, visibility, status)
                VALUES (?, ?, 'manual-owner', 'NAMESPACE_ONLY', 'ACTIVE') RETURNING id
                """, Long.class, namespaceId, slug);
        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility)
                VALUES (?, ?, 'upstream', 'skills', 'NAMESPACE_ONLY') RETURNING id
                """, Long.class, namespaceId, repositoryId);
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID admissionId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        UUID ingestionId = UUID.randomUUID();
        String commit = "a".repeat(40);
        String digest = "b".repeat(64);
        jdbc.update("INSERT INTO github_sync_run (id, namespace_id, kind) VALUES (?, ?, 'REPOSITORY_SYNC')",
                runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, source_id, kind, mode, dedupe_key, snapshot_json)
                VALUES (?, ?, ?, ?, 'REPOSITORY_SYNC', 'FULL', ?, '{}'::jsonb)
                """, taskId, runId, namespaceId, sourceId, suffix);
        jdbc.update("""
                INSERT INTO github_sync_package_admission
                    (id, task_id, namespace_id, repository_id, commit_sha, skill_path, lease_generation)
                VALUES (?, ?, ?, ?, ?, ?, 1)
                """, admissionId, taskId, namespaceId, repositoryId, commit, path);
        jdbc.update("""
                INSERT INTO github_sync_staged_upload
                    (id, admission_id, object_key, mode, ticket_hash, size_bytes, sha256,
                     status, expires_at)
                VALUES (?, ?, ?, 'LOCAL_STREAM', ?, 1, ?, 'ACCEPTED', CURRENT_TIMESTAMP + INTERVAL '1 day')
                """, uploadId, admissionId, "manual/" + uploadId, "c".repeat(64), digest);
        jdbc.update("""
                INSERT INTO github_sync_package_ingestion
                    (id, task_id, admission_id, upload_id, namespace_id, repository_id,
                     commit_sha, skill_path, object_key, size_bytes, sha256,
                     status, content_sha256, last_error_code, target_skill_slug)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?,
                        'BLOCKED', ?, 'SLUG_CONFLICT', ?)
                """, ingestionId, taskId, admissionId, uploadId, namespaceId, repositoryId,
                commit, path, "manual/" + uploadId, digest, digest, slug);
        return new Fixture(namespaceId, ingestionId, skillId);
    }

    private record Fixture(long namespaceId, UUID ingestionId, long skillId) {}
    private record PathFixture(long namespaceId, UUID ingestionId, long skillId, UUID taskId) {}
}
