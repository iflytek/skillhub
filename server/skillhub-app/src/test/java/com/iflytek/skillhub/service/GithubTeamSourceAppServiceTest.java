package com.iflytek.skillhub.service;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.GithubTeamRepositoryRequest;
import com.iflytek.skillhub.infra.jpa.JdbcGithubDiscoveryEntryRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSourceIgnoreRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSyncCapacityGate;
import com.iflytek.skillhub.infra.jpa.NamespaceJpaRepository;
import com.iflytek.skillhub.infra.jpa.NamespaceMemberJpaRepository;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.GithubSyncManagementQueryRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class GithubTeamSourceAppServiceTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PostgresTestImage.name());

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
    @Autowired private NamespaceJpaRepository namespaces;
    @Autowired private NamespaceMemberJpaRepository members;

    @Test
    void teamRunsAndSourcesAreVisibleOnlyToThatTeamAdministrators() {
        jdbc.update("""
                INSERT INTO user_account (id, display_name)
                VALUES ('github-run-owner-one', 'Owner One'),
                       ('github-run-member-one', 'Member One'),
                       ('github-run-owner-two', 'Owner Two')
                """);
        Namespace first = namespaces.saveAndFlush(new Namespace(
                "github-runs-first", "GitHub Runs First", "github-run-owner-one"));
        Namespace second = namespaces.saveAndFlush(new Namespace(
                "github-runs-second", "GitHub Runs Second", "github-run-owner-two"));
        members.saveAndFlush(new NamespaceMember(first.getId(), "github-run-owner-one", NamespaceRole.OWNER));
        members.saveAndFlush(new NamespaceMember(first.getId(), "github-run-member-one", NamespaceRole.MEMBER));
        members.saveAndFlush(new NamespaceMember(second.getId(), "github-run-owner-two", NamespaceRole.OWNER));
        long firstSource = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility)
                VALUES (?, 930005, 'first-owner', 'first-repository', 'NAMESPACE_ONLY')
                RETURNING id
                """, Long.class, first.getId());
        long secondSource = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility)
                VALUES (?, 930006, 'second-owner', 'second-repository', 'NAMESPACE_ONLY')
                RETURNING id
                """, Long.class, second.getId());
        UUID firstRun = UUID.randomUUID();
        UUID secondRun = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO github_sync_run (id, namespace_id, kind)
                VALUES (?, ?, 'REPOSITORY_SYNC'), (?, ?, 'DISCOVERY')
                """, firstRun, first.getId(), secondRun, second.getId());
        UUID discoveryTask = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, kind, status, dedupe_key, snapshot_json,
                     retry_at, last_error_code)
                VALUES (?, ?, ?, 'DISCOVERY', 'RETRY_WAIT', 'discovery-run-error', '{}'::jsonb,
                        CURRENT_TIMESTAMP, 'GITHUB_RATE_LIMITED')
                """, discoveryTask, secondRun, second.getId());
        var management = new GithubSyncManagementQueryRepository(jdbc,
                new JdbcGithubSourceIgnoreRepository(jdbc, new ObjectMapper()),
                new JdbcGithubSyncCapacityGate(jdbc));
        var service = new GithubTeamSourceAppService(namespaces, members,
                new JdbcGithubDiscoveryEntryRepository(jdbc, new ObjectMapper()), management,
                mock(AuditLogService.class), mock(RequestIdAccessor.class));

        assertThat(service.resolvedSources(first.getSlug(), "github-run-owner-one", 0, 20).items())
                .singleElement().satisfies(source -> assertThat(source.id()).isEqualTo(firstSource));
        assertThat(service.resolvedSources(second.getSlug(), "github-run-owner-two", 0, 20).items())
                .singleElement().satisfies(source -> assertThat(source.id()).isEqualTo(secondSource));
        assertThatThrownBy(() -> service.resolvedSources(first.getSlug(), "github-run-member-one", 0, 20))
                .isInstanceOf(DomainForbiddenException.class);
        assertThatThrownBy(() -> service.resolvedSources(first.getSlug(), "github-run-owner-two", 0, 20))
                .isInstanceOf(DomainForbiddenException.class);
        assertThat(service.skillPaths(first.getSlug(), "github-run-owner-one", firstSource, 0, 20)
                .items()).isEmpty();
        assertThatThrownBy(() -> service.skillPaths(first.getSlug(), "github-run-owner-one",
                secondSource, 0, 20)).isInstanceOf(DomainNotFoundException.class);
        assertThatThrownBy(() -> service.skillPaths(first.getSlug(), "github-run-member-one",
                firstSource, 0, 20)).isInstanceOf(DomainForbiddenException.class);
        assertThat(service.runs(first.getSlug(), "github-run-owner-one", 0, 20).items())
                .singleElement().satisfies(run -> assertThat(run.id()).isEqualTo(firstRun));
        assertThat(service.runs(second.getSlug(), "github-run-owner-two", 0, 20).items())
                .singleElement().satisfies(run -> {
                    assertThat(run.id()).isEqualTo(secondRun);
                    assertThat(run.lastErrorCode()).isEqualTo("GITHUB_RATE_LIMITED");
                    assertThat(run.nextRetryAt()).isNotNull();
                });
        jdbc.update("""
                UPDATE github_sync_task
                SET status = 'ACQUISITION_COMPLETE', retry_at = NULL
                WHERE id = ?
                """, discoveryTask);
        assertThat(service.runs(second.getSlug(), "github-run-owner-two", 0, 20).items())
                .singleElement().satisfies(run -> assertThat(run.lastErrorCode()).isNull());
        assertThatThrownBy(() -> service.runs(first.getSlug(), "github-run-member-one", 0, 20))
                .isInstanceOf(DomainForbiddenException.class);
        assertThatThrownBy(() -> service.runs(first.getSlug(), "github-run-owner-two", 0, 20))
                .isInstanceOf(DomainForbiddenException.class);
    }

    @Test
    void teamAdminControlsSourceWhilePublishedSkillKeepsItsVisibility() {
        jdbc.update("""
                INSERT INTO user_account (id, display_name)
                VALUES ('github-team-owner', 'Owner'), ('github-team-member', 'Member')
                """);
        Namespace team = namespaces.saveAndFlush(new Namespace(
                "github-managed-team", "GitHub Managed Team", "github-team-owner"));
        members.saveAndFlush(new NamespaceMember(team.getId(), "github-team-owner", NamespaceRole.OWNER));
        members.saveAndFlush(new NamespaceMember(team.getId(), "github-team-member", NamespaceRole.MEMBER));
        var service = new GithubTeamSourceAppService(namespaces, members,
                new JdbcGithubDiscoveryEntryRepository(jdbc, new ObjectMapper()), null,
                mock(AuditLogService.class), mock(RequestIdAccessor.class));
        var context = new AuditRequestContext("127.0.0.1", "test");

        assertThatThrownBy(() -> service.create(team.getSlug(),
                new GithubTeamRepositoryRequest("public-owner", "skills", null),
                "github-team-member", context)).isInstanceOf(DomainForbiddenException.class);
        assertThatThrownBy(() -> service.list(team.getSlug(), "github-team-member"))
                .isInstanceOf(DomainForbiddenException.class);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_entry WHERE namespace_id = ?
                """, Integer.class, team.getId())).isZero();

        var configured = service.create(team.getSlug(),
                new GithubTeamRepositoryRequest("public-owner", "skills", null),
                "github-team-owner", context);
        assertThat(configured.sourceVisibility()).isEqualTo("NAMESPACE_ONLY");
        assertThat(service.list(team.getSlug(), "github-team-owner"))
                .singleElement().satisfies(entry -> assertThat(entry.id()).isEqualTo(configured.id()));
        long repositoryId = 930004L;
        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility)
                VALUES (?, ?, 'public-owner', 'skills', 'NAMESPACE_ONLY') RETURNING id
                """, Long.class, team.getId(), repositoryId);
        jdbc.update("""
                INSERT INTO github_source_origin (source_id, origin_type, origin_key)
                VALUES (?, 'TEAM_CONFIG', ?)
                """, sourceId, configured.id().toString());
        long skillId = jdbc.queryForObject("""
                INSERT INTO skill (namespace_id, slug, owner_id, visibility)
                VALUES (?, 'published-skill', 'github-team-owner', 'NAMESPACE_ONLY') RETURNING id
                """, Long.class, team.getId());
        long versionId = jdbc.queryForObject("""
                INSERT INTO skill_version (skill_id, version, status, created_by)
                VALUES (?, '1.0.0', 'PUBLISHED', 'github-team-owner') RETURNING id
                """, Long.class, skillId);
        jdbc.update("UPDATE skill SET latest_version_id = ? WHERE id = ?", versionId, skillId);

        var updated = service.setVisibility(team.getSlug(), configured.id(), "PUBLIC",
                "github-team-owner", context);
        assertThat(updated.sourceVisibility()).isEqualTo("PUBLIC");
        assertThat(jdbc.queryForObject("""
                SELECT visibility FROM github_source_binding WHERE id = ?
                """, String.class, sourceId)).isEqualTo("PUBLIC");
        assertThat(jdbc.queryForObject("SELECT visibility FROM skill WHERE id = ?",
                String.class, skillId)).isEqualTo("NAMESPACE_ONLY");
        assertThat(jdbc.queryForObject("SELECT latest_version_id FROM skill WHERE id = ?",
                Long.class, skillId)).isEqualTo(versionId);

        UUID entryId = configured.id();
        assertThatThrownBy(() -> service.setEnabled(team.getSlug(), entryId, false,
                "github-team-member", context)).isInstanceOf(DomainForbiddenException.class);
        service.setEnabled(team.getSlug(), entryId, false, "github-team-owner", context);
        assertThat(jdbc.queryForObject("SELECT status FROM github_source_binding WHERE id = ?",
                String.class, sourceId)).isEqualTo("PAUSED");
        service.setEnabled(team.getSlug(), entryId, true, "github-team-owner", context);
        assertThat(jdbc.queryForObject("SELECT status FROM github_source_binding WHERE id = ?",
                String.class, sourceId)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT visibility FROM skill WHERE id = ?",
                String.class, skillId)).isEqualTo("NAMESPACE_ONLY");
    }
}
