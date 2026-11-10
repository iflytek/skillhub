package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.GithubSyncRepositoryResponse.*;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.repository.GithubSyncRepositoryQueryRepository;
import com.iflytek.skillhub.repository.GithubSyncManagementQueryRepository;
import com.iflytek.skillhub.repository.GithubDiscoveryEntryStatusQueryRepository;
import com.iflytek.skillhub.githubsync.GithubDiscoveryEntryRepository;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "skillhub.builtin-skills.enabled=false")
class GithubSyncUnifiedRepositoryIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired GithubSyncRepositoryQueryRepository queries;
    @Autowired GithubSyncManagementQueryRepository management;
    @Autowired GithubDiscoveryEntryRepository entries;
    @Autowired GithubDiscoveryEntryStatusQueryRepository entryStatus;
    @Autowired MockMvc mvc;
    long namespace;
    static final Instant FIRST = Instant.parse("2026-01-01T00:00:00Z");
    @BeforeEach void setup() { namespace = management.platformNamespaceId(); }

    @Test void sourceCandidateOverlapAndOriginsNeverDuplicateIdentityOrInflateTotals() {
        long a = source(namespace, 101, "ACTIVE");
        long b = candidate(namespace, 101, "BOUND", null);
        sourceOrigin(a, "CURATED_REPOSITORY", UUID.randomUUID().toString());
        sourceOrigin(a, "SEARCH_RULE", "deleted-rule");
        candidateOrigin(b, "SEARCH_RULE", "deleted-rule");
        candidateOrigin(b, "OFFICIAL_DISCOVERY", "deleted-query");
        long onlyCandidate = candidate(namespace, 102, "PENDING", null);
        candidateOrigin(onlyCandidate, "CURATED_OWNER", "owner");
        source(namespace, 103, "PAUSED");
        long other = otherNamespace(); source(other, 101, "ACTIVE"); candidate(other, 999, "PENDING", null);
        jdbc.update("UPDATE github_source_binding SET owner_login='renamed' WHERE id=?", a);
        var page = queries.repositories(0, 20, null, null, null, null, null);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).extracting(Summary::repositoryId).containsExactly(103L, 102L, 101L);
        assertThat(page.items().getLast().owner()).isEqualTo("renamed");
        assertThat(page.items().getLast().state()).isEqualTo(State.REGISTERED);
        assertThat(page.items().getLast().originTypes()).containsExactly(OriginType.CURATED_REPOSITORY, OriginType.OFFICIAL_DISCOVERY, OriginType.SEARCH_RULE);
        var origins = queries.origins(101, 0, 20, null);
        assertThat(origins.total()).isEqualTo(3);
        assertThat(origins.items()).filteredOn(o -> o.originType() == OriginType.SEARCH_RULE).singleElement()
                .satisfies(o -> { assertThat(o.originKey()).isEqualTo("deleted-rule"); assertThat(o.entryId()).isNull(); });
        assertThat(origins.items()).filteredOn(o -> o.originType() == OriginType.CURATED_REPOSITORY).singleElement()
                .satisfies(o -> assertThat(o.lastSeenAt()).isNull());
        assertThat(queries.detail(103).repository().sourceStatus()).isEqualTo(SourceStatus.PAUSED);
        assertThatThrownBy(() -> queries.detail(999)).isInstanceOf(DomainNotFoundException.class);
        assertThatThrownBy(() -> queries.paths(999, 0, 20, null)).isInstanceOf(DomainNotFoundException.class);
    }

    @Test void allFiltersApplyBeforeStablePagingAndCountWithLiteralSearchAndAsOf() {
        for (int i = 1; i <= 45; i++) {
            long id = candidate(namespace, i, i % 2 == 0 ? "REJECTED" : "PENDING", i % 2 == 0 ? "NO_VALID_SKILL" : null);
            candidateOrigin(id, "SEARCH_RULE", "rule");
            if (i % 3 == 0) source(namespace, i, i % 2 == 0 ? "PAUSED" : "ACTIVE");
        }
        var p0 = queries.repositories(0, 20, null, null, OriginType.SEARCH_RULE, null, null);
        var p1 = queries.repositories(1, 20, p0.asOf(), null, OriginType.SEARCH_RULE, null, null);
        assertThat(p0.total()).isEqualTo(45);
        assertThat(p0.items()).extracting(Summary::repositoryId).containsExactlyElementsOf(java.util.stream.LongStream.iterate(45, i -> i - 1).limit(20).boxed().toList());
        assertThat(p1.items()).extracting(Summary::repositoryId).containsExactlyElementsOf(java.util.stream.LongStream.iterate(25, i -> i - 1).limit(20).boxed().toList());
        assertThat(queries.repositories(3, 20, p0.asOf(), null, null, null, null).items()).isEmpty();
        for (State state : State.values()) for (SourceStatus status : SourceStatus.values()) {
            var expected = queries.repositories(0, 100, p0.asOf(), null, null, null, null).items().stream()
                    .filter(item -> item.state() == state && item.sourceStatus() == status).toList();
            var actual = queries.repositories(0, 100, p0.asOf(), null, OriginType.SEARCH_RULE, state, status);
            assertThat(actual.items()).isEqualTo(expected); assertThat(actual.total()).isEqualTo(expected.size());
        }
        jdbc.update("UPDATE github_discovery_candidate SET repository_name='literal_%' WHERE repository_id=1 AND namespace_id=?", namespace);
        assertThat(queries.repositories(0, 20, null, "_%", null, null, null).items()).extracting(Summary::repositoryId).containsExactly(1L);
        candidate(namespace, 46, "PENDING", null);
        jdbc.update("UPDATE github_discovery_candidate SET first_seen_at=? WHERE repository_id=46 AND namespace_id=?", Timestamp.from(p0.asOf().plusSeconds(1)), namespace);
        assertThat(queries.repositories(0, 100, p0.asOf(), null, null, null, null).total()).isEqualTo(45);
    }

    @Test void candidateStagesDoNotClaimRegistrationOrQuality() {
        candidate(namespace, 1, "BOUND", null);
        candidate(namespace, 2, "REJECTED", "NO_VALID_SKILL");
        candidate(namespace, 3, "REJECTED", "INVALID_MANIFEST");
        candidate(namespace, 4, "UNREACHABLE", "GITHUB_NOT_FOUND");
        long validating = candidate(namespace, 5, "PENDING", null);
        UUID validation = task(run("REPOSITORY_SYNC", "QUEUED"), null, "REPOSITORY_SYNC", "FULL", "{\"repositoryId\":5}");
        jdbc.update("UPDATE github_discovery_candidate SET status='VALIDATING',validation_task_id=? WHERE id=?", validation, validating);
        assertThat(queries.repositories(0, 20, null, null, null, null, null).items()).extracting(Summary::state)
                .containsExactly(State.VALIDATING, State.UNREACHABLE, State.VALIDATION_FAILED, State.NO_VALID_SKILL, State.BOUND_WITHOUT_SOURCE);
    }

    @Test void pureSourcePathsReuseSafeProjectionAndHidePurgedRunLinks() {
        long source = source(namespace, 11, "ACTIVE");
        UUID run = run("REPOSITORY_SYNC", "ACQUISITION_COMPLETE");
        UUID task = task(run, source, "REPOSITORY_SYNC", "FULL", "{\"repositoryId\":11}");
        jdbc.update("UPDATE github_sync_task SET status='ACQUISITION_COMPLETE',completed_at=CURRENT_TIMESTAMP WHERE id=?", task);
        jdbc.update("INSERT INTO github_sync_path_result(task_id,commit_sha,skill_path,outcome,reason) VALUES (?,?,'skills/a','IGNORED','IGNORE_PATTERN')", task, "a".repeat(40));
        var result = queries.paths(11, 0, 20, null);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items().getFirst().runId()).isEqualTo(run);
        assertThat(result.items().getFirst().reviewId()).isNull();
        assertThat(queries.detail(11).latestRunId()).isEqualTo(run);
        assertThat(queries.detail(11).lastCheckedAt()).isNotNull();
        jdbc.update("UPDATE github_sync_run SET details_purged_at=CURRENT_TIMESTAMP WHERE id=?", run);
        assertThat(queries.paths(11, 0, 20, null).items().getFirst().runId()).isNull();
        assertThat(queries.detail(11).latestRunId()).isNull();
    }

    @Test void repeatedDesignatedRepositoryPreservesDisabledEntryAndExecutionIsNotGuessed() {
        var first = entries.createCuratedRepository("Owner", "Repo");
        entries.setEnabled(first.id(), false);
        var disabled = entries.findPlatformEntry(first.id()).orElseThrow();
        var repeated = entries.createCuratedRepository("owner", "repo");
        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(repeated.enabled()).isFalse();
        assertThat(repeated.updatedAt()).isEqualTo(disabled.updatedAt());
        assertThat(repeated.nextDueAt()).isEqualTo(disabled.nextDueAt());
        assertThat(queries.repositories(0, 20, null, null, null, null, null).total()).isZero();
        assertThat(entryStatus.latest(namespace, first.id())).isNull();
        UUID run = run("DISCOVERY", "RETRY_WAIT");
        UUID task = task(run, null, "DISCOVERY", null, "{\"entryType\":\"CURATED_REPOSITORY\",\"ruleId\":\"" + first.id() + "\",\"owner\":\"owner\",\"repositoryName\":\"repo\"}");
        jdbc.update("UPDATE github_sync_task SET status='RETRY_WAIT',last_error_code='GITHUB_RATE_LIMITED',retry_at=CURRENT_TIMESTAMP+INTERVAL '1 hour' WHERE id=?", task);
        var execution = entryStatus.latest(namespace, first.id());
        assertThat(execution.taskId()).isEqualTo(task);
        assertThat(execution.errorCode()).isEqualTo("GITHUB_RATE_LIMITED");
        assertThat(entryStatus.latestForEntries(namespace, List.of(first.id())).get(first.id())).isEqualTo(execution);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_sync_task WHERE namespace_id=?", Integer.class, namespace)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentRepeatedRegistrationKeepsOneDisabledIdentity() throws Exception {
        var original = entries.createCuratedRepository("concurrent-owner", "same-repo");
        entries.setEnabled(original.id(), false);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<com.iflytek.skillhub.githubsync.GithubDiscoveryEntry> register = () -> {
                ready.countDown();
                assertThat(start.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                return entries.createCuratedRepository("CONCURRENT-owner", "SAME-repo");
            };
            var first = executor.submit(register);
            var second = executor.submit(register);
            try { assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
            finally { start.countDown(); }
            for (var result : List.of(first, second)) {
                var entry = result.get(15, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(entry.id()).isEqualTo(original.id());
                assertThat(entry.enabled()).isFalse();
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM github_discovery_entry WHERE namespace_id=? AND lower(owner_login)='concurrent-owner' AND lower(repository_name)='same-repo'", Integer.class, namespace)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM github_discovery_entry WHERE id=?", original.id());
        }
    }

    @Test void runFiltersAndTargetsUseRealRunsWithoutPreviewOrPurgedRows() {
        UUID single = run("DISCOVERY", "QUEUED");
        String owner = "{\"entryType\":\"CURATED_OWNER\",\"owner\":\"sample\"}";
        task(single, null, "DISCOVERY", null, owner); task(single, null, "DISCOVERY", null, owner);
        UUID multi = run("REPOSITORY_SYNC", "PARTIAL");
        task(multi, source(namespace, 1, "ACTIVE"), "REPOSITORY_SYNC", "HEAD_ONLY", "{}");
        task(multi, source(namespace, 2, "ACTIVE"), "REPOSITORY_SYNC", "FULL", "{}");
        UUID empty = run("DISCOVERY", "FAILED");
        UUID preview = run("DISCOVERY", "QUEUED"); task(preview, null, "DISCOVERY", null, "{\"entryType\":\"SEARCH_PREVIEW\"}");
        UUID purged = run("DISCOVERY", "QUEUED"); jdbc.update("UPDATE github_sync_run SET details_purged_at=CURRENT_TIMESTAMP WHERE id=?", purged);
        var rows = management.runs(namespace, 0, 20);
        assertThat(rows.total()).isEqualTo(3);
        assertThat(rows.items()).filteredOn(r -> r.id().equals(single)).singleElement().satisfies(r -> {
            assertThat(r.targetCount()).isEqualTo(1); assertThat(r.target()).isEqualTo(new Target("OWNER", "sample")); });
        assertThat(rows.items()).filteredOn(r -> r.id().equals(multi)).singleElement().satisfies(r -> {
            assertThat(r.targetCount()).isEqualTo(2); assertThat(r.target()).isNull(); });
        assertThat(rows.items()).filteredOn(r -> r.id().equals(empty)).singleElement().satisfies(r -> {
            assertThat(r.targetCount()).isZero(); assertThat(r.target()).isNull(); });
        for (RunKind kind : RunKind.values()) for (RunStatus status : RunStatus.values()) {
            var expected = rows.items().stream().filter(r -> r.kind().equals(kind.name()) && r.status().equals(status.name())).toList();
            var actual = management.runs(namespace, 0, 1, kind, status);
            assertThat(actual.total()).isEqualTo(expected.size()); assertThat(actual.items()).isEqualTo(expected.stream().limit(1).toList());
            assertThat(management.runs(namespace, 1, 1, kind, status).items()).isEqualTo(expected.stream().skip(1).limit(1).toList());
        }
    }

    @Test void unboundCandidateRunUsesItsActualSnapshotRepositoryTarget() {
        UUID run = run("REPOSITORY_SYNC", "QUEUED");
        UUID older = task(run, null, "REPOSITORY_SYNC", "FULL", "{\"repositoryId\":77,\"owner\":\"old-name\",\"name\":\"skills\"}");
        jdbc.update("UPDATE github_sync_task SET created_at=CURRENT_TIMESTAMP-INTERVAL '1 hour' WHERE id=?", older);
        task(run, null, "REPOSITORY_SYNC", "FULL", "{\"repositoryId\":77,\"owner\":\"upstream\",\"name\":\"skills\"}");
        var item = management.runs(namespace, 0, 20).items().getFirst();
        assertThat(item.targetCount()).isEqualTo(1);
        assertThat(item.target()).isEqualTo(new Target("REPOSITORY", "upstream/skills"));
    }

    @Test void routesEnforceAdminScopeAndValidateFilters() throws Exception {
        source(namespace, 9, "ACTIVE");
        for (String suffix : List.of("", "/9", "/9/origins", "/9/paths")) {
            String path = "/api/v1/admin/github-sync/repositories" + suffix;
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(authentication(auth("USER")))).andExpect(status().isForbidden());
            mvc.perform(get(path).with(authentication(auth("SUPER_ADMIN")))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        }
        for (String query : List.of("state=BOGUS", "originType=BOGUS", "sourceStatus=BOGUS", "size=101", "page=-1", "q=" + "a".repeat(201))) {
            mvc.perform(get("/api/v1/admin/github-sync/repositories?" + query).with(authentication(auth("SUPER_ADMIN")))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/admin/github-sync/runs?kind=BOGUS").with(authentication(auth("SUPER_ADMIN")))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/github-sync/runs?status=BOGUS").with(authentication(auth("SUPER_ADMIN")))).andExpect(status().isBadRequest());
    }

    private long source(long ns, long repo, String status) {
        return jdbc.queryForObject("INSERT INTO github_source_binding(namespace_id,repository_id,owner_login,repository_name,visibility,status,created_at) VALUES (?,?,'owner',?,'PUBLIC',?,?) RETURNING id", Long.class, ns, repo, "repo" + repo, status, Timestamp.from(FIRST));
    }
    private long candidate(long ns, long repo, String status, String error) {
        return jdbc.queryForObject("INSERT INTO github_discovery_candidate(namespace_id,repository_id,owner_login,repository_name,status,last_error_code,first_seen_at) VALUES (?,?,'owner',?,?,?,?) RETURNING id", Long.class, ns, repo, "repo" + repo, status, error, Timestamp.from(FIRST));
    }
    private void sourceOrigin(long source, String type, String key) { jdbc.update("INSERT INTO github_source_origin(source_id,origin_type,origin_key) VALUES (?,?,?)", source, type, key); }
    private void candidateOrigin(long candidate, String type, String key) { jdbc.update("INSERT INTO github_discovery_candidate_origin(candidate_id,origin_type,origin_key) VALUES (?,?,?)", candidate, type, key); }
    private long otherNamespace() { return jdbc.queryForObject("INSERT INTO namespace(slug,display_name,type) VALUES ('unified-other','Other','TEAM') RETURNING id", Long.class); }
    private UUID run(String kind, String status) { UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO github_sync_run(id,namespace_id,kind,status) VALUES (?,?,?,?)", id, namespace, kind, status); return id; }
    private UUID task(UUID run, Long source, String kind, String mode, String snapshot) {
        UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO github_sync_task(id,run_id,namespace_id,source_id,kind,mode,dedupe_key,snapshot_json) VALUES (?,?,?,?,?,?,?,?::jsonb)", id, run, namespace, source, kind, mode, id.toString(), snapshot); return id;
    }
    private UsernamePasswordAuthenticationToken auth(String role) {
        var principal = new PlatformPrincipal("unified-test", "Test", null, null, null, Set.of(role));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
