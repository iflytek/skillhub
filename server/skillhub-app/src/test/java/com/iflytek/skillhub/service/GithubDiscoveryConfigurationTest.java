package com.iflytek.skillhub.service;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.dto.GithubSearchSelectorRequest;
import com.iflytek.skillhub.infra.jpa.JdbcGithubDiscoveryEntryRepository;
import com.iflytek.skillhub.infra.jpa.JdbcGithubSearchPreviewRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class GithubDiscoveryConfigurationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PostgresTestImage.name());

    @Test
    void disabledInitialRuleAndImmutableQueuedSnapshotWithSideEffectFreePreview() throws Exception {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var mapper = new ObjectMapper();
        var entries = new JdbcGithubDiscoveryEntryRepository(jdbc, mapper);
        var previews = new JdbcGithubSearchPreviewRepository(jdbc, mapper);
        var admin = new GithubDiscoveryAdminAppService(null, null, mapper, null, null, null);
        String criteria = admin.serializeSelectors(List.of(
                new GithubSearchSelectorRequest("KEYWORD", "agent skills", "CORE")));
        String nextCriteria = admin.serializeSelectors(List.of(
                new GithubSearchSelectorRequest("TOPIC", "agent-skills", "EXTENSION")));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_entry
                WHERE entry_type = 'SEARCH_RULE' AND enabled = FALSE
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_sync_task", Integer.class)).isZero();
        assertThatThrownBy(() -> admin.serializeSelectors(List.of(
                new GithubSearchSelectorRequest("KEYWORD", "stars:>100", "CORE"))))
                .isInstanceOf(DomainBadRequestException.class);

        var created = entries.createSearchRule("Independent rule", criteria, 86400);
        assertThat(created.enabled()).isFalse();
        assertThat(created.ruleVersion()).isEqualTo(1);
        var enabled = entries.setEnabled(created.id(), true);
        assertThat(enabled.ruleVersion()).isEqualTo(2);
        assertThat(entries.enqueueDue(Instant.now().plusSeconds(1), 10)).isEqualTo(1);
        String snapshot = jdbc.queryForObject("""
                SELECT snapshot_json::text FROM github_sync_task
                WHERE dedupe_key = ?
                """, String.class, "discovery:" + created.id());
        var queued = mapper.readTree(snapshot);
        assertThat(queued.path("ruleVersion").asLong()).isEqualTo(2);
        assertThat(queued.path("criteria").path("selectors").get(0).path("value").asText())
                .isEqualTo("agent skills");
        assertThat(queued.path("searchWindow").path("until").asText()).isNotBlank();

        var edited = entries.updateSearchRule(created.id(), "Independent rule",
                nextCriteria, 86400, true);
        assertThat(edited.ruleVersion()).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT snapshot_json::text FROM github_sync_task
                WHERE dedupe_key = ?
                """, String.class, "discovery:" + created.id())).isEqualTo(snapshot);

        UUID previewId = previews.queue(nextCriteria, "github-sync-publisher", Instant.now());
        assertThat(previews.find(previewId)).isPresent();
        assertThat(jdbc.queryForObject("""
                SELECT snapshot_json ->> 'entryType' FROM github_sync_task WHERE id = ?
                """, String.class, previewId)).isEqualTo("SEARCH_PREVIEW");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_source_binding",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_discovery_candidate",
                Integer.class)).isZero();

        var budgetRule = entries.createSearchRule("Budget rule", nextCriteria, 86400);
        entries.setEnabled(budgetRule.id(), true);
        long namespaceId = jdbc.queryForObject("""
                SELECT id FROM namespace WHERE slug = 'github'
                """, Long.class);
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET candidate_pending_limit = 1, candidate_per_lease_limit = 1
                WHERE id = TRUE
                """);
        long candidateId = jdbc.queryForObject("""
                INSERT INTO github_discovery_candidate
                    (namespace_id, repository_id, owner_login, repository_name)
                VALUES (?, 930005, 'public-owner', 'skills') RETURNING id
                """, Long.class, namespaceId);
        assertThat(entries.enqueueDue(Instant.now().plusSeconds(1), 10)).isZero();
        jdbc.update("UPDATE github_discovery_candidate SET status = 'BOUND' WHERE id = ?", candidateId);
        assertThat(entries.enqueueDue(Instant.now().plusSeconds(1), 10)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT (snapshot_json ->> 'candidateBudget')::integer
                FROM github_sync_task WHERE dedupe_key = ?
                """, Integer.class, "discovery:" + budgetRule.id())).isEqualTo(1);

        jdbc.update("""
                UPDATE github_sync_task SET status = 'ACQUISITION_COMPLETE'
                WHERE dedupe_key = ? AND status = 'QUEUED'
                """, "discovery:" + budgetRule.id());
        Instant incrementalCheck = Instant.now().plusSeconds(86400 + 5);
        assertThat(entries.enqueueDue(incrementalCheck, 10)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT snapshot_json #>> '{searchWindow,mode}'
                FROM github_sync_task WHERE dedupe_key = ?
                ORDER BY created_at DESC, id DESC LIMIT 1
                """, String.class, "discovery:" + budgetRule.id())).isEqualTo("INCREMENTAL");

        jdbc.update("""
                UPDATE github_sync_task SET status = 'ACQUISITION_COMPLETE'
                WHERE dedupe_key = ? AND status = 'QUEUED'
                """, "discovery:" + budgetRule.id());
        Instant fullRecheck = Instant.now().plusSeconds(8 * 86400L);
        assertThat(entries.enqueueDue(fullRecheck, 10)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT snapshot_json #>> '{searchWindow,mode}'
                FROM github_sync_task WHERE dedupe_key = ?
                ORDER BY created_at DESC, id DESC LIMIT 1
                """, String.class, "discovery:" + budgetRule.id())).isEqualTo("FULL_RECHECK");
    }
}
