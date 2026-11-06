package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.githubsync.GithubRepositoryMetadataResult;
import com.iflytek.skillhub.infra.jpa.JdbcGithubDiscoveryEntryRepository;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class GithubRepositoryMetadataRepositoryTest {
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
    void stableIdUpdatesRegisteredSourceButRejectsStaleLeaseAndReusedName() {
        long namespaceId = platformNamespaceId();
        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility)
                VALUES (?, 920001, 'old-owner', 'old-name', 'PUBLIC') RETURNING id
                """, Long.class, namespaceId);
        UUID taskId = leasedTask(namespaceId, sourceId, 920001);
        var repository = repository();
        Instant now = Instant.now();
        var entries = new JdbcGithubDiscoveryEntryRepository(jdbc, new ObjectMapper());
        var entry = entries.createCuratedRepository("old-owner", "old-name");
        jdbc.update("""
                UPDATE github_discovery_entry SET repository_id = ?, next_due_at = ? WHERE id = ?
                """, 920001L, Timestamp.from(now.minusSeconds(1)), entry.id());
        jdbc.update("""
                INSERT INTO github_source_origin (source_id, origin_type, origin_key)
                VALUES (?, 'CURATED_REPOSITORY', ?)
                """, sourceId, entry.id().toString());

        assertThat(repository.updateRepositoryMetadata(taskId, 1, "a".repeat(64), 920002,
                "wrong-owner", "reused-name", "main", now))
                .isEqualTo(GithubRepositoryMetadataResult.REPOSITORY_MISMATCH);
        assertThat(repository.updateRepositoryMetadata(taskId, 1, "b".repeat(64), 920001,
                "new-owner", "new-name", "main", now))
                .isEqualTo(GithubRepositoryMetadataResult.LEASE_STALE);
        assertThat(repository.updateRepositoryMetadata(taskId, 1, "a".repeat(64), 920001,
                "new-owner", "new-name", "main", now))
                .isEqualTo(GithubRepositoryMetadataResult.UPDATED);
        assertThat(jdbc.queryForObject("""
                SELECT owner_login || '/' || repository_name FROM github_source_binding WHERE id = ?
                """, String.class, sourceId)).isEqualTo("new-owner/new-name");
        assertThat(jdbc.queryForObject("""
                SELECT snapshot_json ->> 'owner' FROM github_sync_task WHERE id = ?
                """, String.class, taskId)).isEqualTo("new-owner");
        assertThat(entries.enqueueDue(now, 10)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT snapshot_json ->> 'owner' FROM github_sync_task
                WHERE dedupe_key = ? AND status = 'QUEUED'
                """, String.class, "discovery:" + entry.id())).isEqualTo("new-owner");
        assertThat(jdbc.queryForObject("""
                SELECT snapshot_json ->> 'repositoryName' FROM github_sync_task
                WHERE dedupe_key = ? AND status = 'QUEUED'
                """, String.class, "discovery:" + entry.id())).isEqualTo("new-name");
    }

    @Test
    void stableIdUpdatesCandidateBeforeItBecomesARegisteredSource() {
        long namespaceId = platformNamespaceId();
        UUID taskId = leasedTask(namespaceId, null, 920003);
        jdbc.update("""
                INSERT INTO github_discovery_candidate
                    (namespace_id, repository_id, owner_login, repository_name,
                     status, validation_task_id, next_validation_at)
                VALUES (?, 920003, 'old-owner', 'old-name', 'VALIDATING', ?, CURRENT_TIMESTAMP)
                """, namespaceId, taskId);

        assertThat(repository().updateRepositoryMetadata(taskId, 1, "a".repeat(64), 920003,
                "new-owner", "new-name", "main", Instant.now()))
                .isEqualTo(GithubRepositoryMetadataResult.UPDATED);
        assertThat(jdbc.queryForObject("""
                SELECT owner_login || '/' || repository_name FROM github_discovery_candidate
                WHERE validation_task_id = ?
                """, String.class, taskId)).isEqualTo("new-owner/new-name");
    }

    @Test
    void pinnedRepositorySnapshotUsesOnlyItsNamespaceSourceCoordinate() {
        long platformId = platformNamespaceId();
        long teamId = jdbc.queryForObject("""
                INSERT INTO namespace (slug, display_name, type, created_by)
                VALUES ('github-coordinate-team', 'GitHub Coordinate Team', 'TEAM', 'github-sync-publisher')
                RETURNING id
                """, Long.class);
        long repositoryId = 920004L;
        jdbc.update("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility)
                VALUES (?, ?, 'platform-owner', 'platform-name', 'PUBLIC'),
                       (?, ?, 'team-owner', 'team-name', 'NAMESPACE_ONLY')
                """, platformId, repositoryId, teamId, repositoryId);
        var entries = new JdbcGithubDiscoveryEntryRepository(jdbc, new ObjectMapper());
        var platformEntry = entries.createCuratedRepository("old-owner", "old-name");
        var teamEntry = entries.createTeamRepository(teamId, "old-owner", "old-name", "NAMESPACE_ONLY");
        Instant now = Instant.now();
        jdbc.update("""
                UPDATE github_discovery_entry
                SET repository_id = ?, next_due_at = ? WHERE id IN (?, ?)
                """, repositoryId, Timestamp.from(now.minusSeconds(1)),
                platformEntry.id(), teamEntry.id());

        assertThat(entries.enqueueDue(now, 10)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT snapshot_json ->> 'owner' FROM github_sync_task
                WHERE namespace_id = ? AND dedupe_key = ?
                """, String.class, platformId, "discovery:" + platformEntry.id()))
                .isEqualTo("platform-owner");
        assertThat(jdbc.queryForObject("""
                SELECT snapshot_json ->> 'owner' FROM github_sync_task
                WHERE namespace_id = ? AND dedupe_key = ?
                """, String.class, teamId, "discovery:" + teamEntry.id()))
                .isEqualTo("team-owner");
    }

    private UUID leasedTask(long namespaceId, Long sourceId, long repositoryId) {
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        jdbc.update("INSERT INTO github_sync_run (id, namespace_id, kind) VALUES (?, ?, 'REPOSITORY_SYNC')",
                runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, source_id, kind, mode, status, dedupe_key,
                     snapshot_json, lease_generation, lease_token_hash, lease_expires_at, leased_by)
                VALUES (?, ?, ?, ?, 'REPOSITORY_SYNC', 'FULL', 'LEASED', ?,
                        jsonb_build_object('repositoryId', ?::bigint, 'owner', 'old-owner',
                                           'name', 'old-name'), 1, ?, ?, 'metadata-test')
                """, taskId, runId, namespaceId, sourceId, "repository:" + repositoryId,
                repositoryId, "a".repeat(64), Timestamp.from(Instant.now().plusSeconds(300)));
        return taskId;
    }

    private JdbcGithubSyncTaskRepository repository() {
        var ignores = new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper());
        return new JdbcGithubSyncTaskRepository(jdbc, new ObjectMapper(),
                new JdbcGithubSyncCapacityGate(jdbc),
                new JdbcGithubSyncSourceCheckpointRepository(jdbc), ignores,
                new JdbcGithubSyncPathPresenceRepository(jdbc, ignores));
    }

    private long platformNamespaceId() {
        return jdbc.queryForObject("SELECT id FROM namespace WHERE slug = 'github' AND type = 'CATALOG'",
                Long.class);
    }
}
