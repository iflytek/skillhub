package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.support.PostgresTestImage;
import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.user.UserAccount;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaReviewQueueQueryRepository.class, GithubReviewGroupQueryRepository.class})
@Testcontainers
class JpaReviewQueueQueryRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());

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

    @Autowired TestEntityManager entities;
    @Autowired JpaReviewQueueQueryRepository repository;
    @Autowired GithubReviewGroupQueryRepository groups;

    @BeforeEach
    void users() {
        entities.persist(new UserAccount("queue-owner", "Owner", null, null));
        entities.persist(new UserAccount("queue-author", "Author Name", null, null));
        entities.flush();
    }

    @Test
    void authorizationAndTotalAreAppliedBeforePaginationAcrossManagedTeams() {
        Namespace first = namespace("queue-first", NamespaceType.TEAM);
        Namespace second = namespace("queue-second", NamespaceType.TEAM);
        Namespace foreign = namespace("queue-foreign", NamespaceType.TEAM);
        Namespace catalog = namespace("queue-catalog", NamespaceType.CATALOG);
        ReviewTask older = task(first, "older", "1.0.0", "2026-01-01T00:00:00Z");
        ReviewTask newer = task(second, "newer", "1.0.0", "2026-01-02T00:00:00Z");
        task(foreign, "not-visible", "1.0.0", "2026-01-04T00:00:00Z");
        task(catalog, "not-a-team", "1.0.0", "2026-01-03T00:00:00Z");
        var filter = filter("PENDING", null, "", null, null);
        Set<Long> managed = Set.of(first.getId(), second.getId(), catalog.getId());
        var page = repository.list(filter, false, managed, page(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(entry -> entry.task().getId()).containsExactly(newer.getId());
        assertThat(repository.list(filter, false, managed, page(1, 1)).getContent())
                .extracting(entry -> entry.task().getId()).containsExactly(older.getId());
        assertThat(repository.list(filter, false, managed, page(4, 1)).getTotalElements()).isEqualTo(2);
        assertThat(repository.list(filter, false, Set.of(), page(0, 20))).isEmpty();
        assertThat(repository.list(filter, true, Set.of(), page(0, 20)).getTotalElements()).isEqualTo(4);
        assertThat(repository.list(filter("PENDING", foreign.getId(), "", null, null), false, managed, page(0, 20)))
                .isEmpty();
    }

    @Test
    void keywordUsesVersionPresentationCoordinatesAndAuthorAndEscapesWildcards() {
        Namespace team = namespace("queue-search", NamespaceType.TEAM);
        ReviewTask match = task(team, "percent-skill", "1.0.0", "2026-01-01T00:00:00Z");
        entities.getEntityManager().createNativeQuery("""
                UPDATE skill_version SET parsed_metadata_json = CAST(:metadata AS jsonb) WHERE id = :id
                """).setParameter("metadata", """
                {"name":"package-name","description":"package summary","displayNameOverride":"Review 100%_ready",
                 "shortDescriptionOverride":"effective summary"}
                """).setParameter("id", match.getSkillVersionId()).executeUpdate();
        task(team, "other-skill", "1.0.0", "2026-01-02T00:00:00Z");
        var literal = repository.list(filter(null, null, "%_", null, "USER"), true, Set.of(), page(0, 20));
        assertThat(literal.getContent()).singleElement().satisfies(entry -> {
            assertThat(entry.task().getId()).isEqualTo(match.getId());
            assertThat(entry.displayName()).isEqualTo("Review 100%_ready");
            assertThat(entry.summary()).isEqualTo("effective summary");
        });
        assertThat(repository.list(filter(null, null, "@queue-search/percent-skill", null, null), true, Set.of(), page(0, 20)))
                .hasSize(1);
        assertThat(repository.list(filter(null, null, "author name", null, null), true, Set.of(), page(0, 20)))
                .hasSize(2);
        assertThat(repository.list(filter(null, null, "queue-author", null, null), true, Set.of(), page(0, 20)))
                .hasSize(2);
        assertThat(repository.list(filter(null, null, "effective summary", null, null), true, Set.of(), page(0, 20)))
                .isEmpty();
    }

    @Test
    void classifiesVersionMetadataAndSuiteRequestsAndFiltersStatusWithStableSort() {
        Namespace team = namespace("queue-kinds", NamespaceType.TEAM);
        ReviewTask first = task(team, "first", "1.0.0", "2026-01-02T00:00:00Z");
        Skill skill = entities.find(Skill.class, first.getSubjectId());
        SkillVersion published = new SkillVersion(skill.getId(), "0.9.0", "queue-owner");
        published.setStatus(SkillVersionStatus.PUBLISHED);
        published.setPublishedAt(Instant.parse("2026-01-01T00:00:00Z"));
        entities.persistFlushFind(published);
        SkillSuite suite = entities.persistFlushFind(new SkillSuite(team.getId(), "suite", "Suite", "queue-owner"));
        entities.persistFlushFind(ReviewTask.forSuiteVersion(null, suite.getId(), team.getId(), "1.0.0", "queue-author"));
        entities.persistFlushFind(ReviewTask.forSkillMetadata(skill.getId(), team.getId(), "queue-author", null));
        entities.persistFlushFind(ReviewTask.forVisibility(skill, SkillVisibility.PUBLIC, "queue-author"));
        ReviewTask rejected = task(team, "rejected", "1.0.0", "2026-01-03T00:00:00Z");
        rejected.setStatus(ReviewTaskStatus.REJECTED); entities.flush();
        assertThat(repository.list(filter(null, null, "", "VERSION_UPDATE", null), true, Set.of(), page(0, 20)))
                .singleElement().satisfies(entry -> assertThat(entry.task().getId()).isEqualTo(first.getId()));
        for (String kind : new String[]{"SUITE_PUBLISH", "AVATAR_CHANGE", "VISIBILITY_CHANGE"}) {
            assertThat(repository.list(filter(null, null, "", kind, null), true, Set.of(), page(0, 20)))
                    .singleElement().satisfies(entry -> assertThat(entry.reviewKind()).isEqualTo(kind));
        }
        assertThat(repository.list(filter("REJECTED", null, "", null, null), true, Set.of(), page(0, 20)))
                .singleElement().satisfies(entry -> assertThat(entry.task().getId()).isEqualTo(rejected.getId()));
        assertThat(repository.list(filter(null, null, "", null, null), true, Set.of(), page(0, 20)).getTotalElements())
                .isEqualTo(5);
    }

    @Test
    void githubAndUserOriginsShareQueueButRemainSeparatelyFilterable() {
        Namespace team = namespace("queue-origins", NamespaceType.TEAM);
        ReviewTask user = task(team, "user", "1.0.0", "2026-01-01T00:00:00Z");
        ReviewTask sync = task(team, "sync", "1.0.0", "2026-01-02T00:00:00Z");
        java.util.UUID run = java.util.UUID.randomUUID();
        java.util.UUID task = java.util.UUID.randomUUID();
        java.util.UUID admission = java.util.UUID.randomUUID();
        java.util.UUID upload = java.util.UUID.randomUUID();
        java.util.UUID ingestion = java.util.UUID.randomUUID();
        execute("INSERT INTO github_sync_run (id, namespace_id, kind) VALUES (?1, ?2, 'REPOSITORY_SYNC')", run, team.getId());
        execute("""
                INSERT INTO github_sync_task (id, run_id, namespace_id, kind, mode, dedupe_key, snapshot_json)
                VALUES (?1, ?2, ?3, 'REPOSITORY_SYNC', 'FULL', 'queue-test', CAST('{}' AS jsonb))
                """, task, run, team.getId());
        execute("""
                INSERT INTO github_sync_package_admission
                  (id, task_id, namespace_id, repository_id, commit_sha, skill_path, lease_generation)
                VALUES (?1, ?2, ?3, 100, ?4, 'skill', 1)
                """, admission, task, team.getId(), "a".repeat(40));
        execute("""
                INSERT INTO github_sync_staged_upload
                  (id, admission_id, object_key, mode, ticket_hash, size_bytes, sha256, status, expires_at)
                VALUES (?1, ?2, 'queue-test', 'LOCAL_STREAM', ?3, 1, ?3, 'ACCEPTED', CURRENT_TIMESTAMP + INTERVAL '1 day')
                """, upload, admission, "b".repeat(64));
        execute("""
                INSERT INTO github_sync_package_ingestion
                  (id, task_id, admission_id, upload_id, namespace_id, repository_id, commit_sha,
                   skill_path, object_key, size_bytes, sha256, status)
                VALUES (?1, ?2, ?3, ?4, ?5, 100, ?6, 'skill', 'queue-test', 1, ?7, 'VALIDATED')
                """, ingestion, task, admission, upload, team.getId(), "a".repeat(40), "b".repeat(64));
        execute("UPDATE review_task SET origin_type = 'GITHUB_SYNC', github_sync_ingestion_id = ?1 WHERE id = ?2",
                ingestion, sync.getId());
        entities.clear();
        assertThat(repository.list(filter("PENDING", null, "", null, null), false, Set.of(team.getId()), page(0, 1)))
                .singleElement().satisfies(entry -> assertThat(entry.task().getId()).isEqualTo(sync.getId()));
        assertThat(repository.list(filter("PENDING", null, "", null, null), false, Set.of(team.getId()), page(0, 1))
                .getTotalElements()).isEqualTo(2);
        assertThat(repository.list(filter(null, null, "", null, "USER"), true, Set.of(), page(0, 20)))
                .singleElement().satisfies(entry -> assertThat(entry.task().getId()).isEqualTo(user.getId()));
        assertThat(repository.list(filter(null, null, "", null, "GITHUB_SYNC"), true, Set.of(), page(0, 20)))
                .singleElement().satisfies(entry -> assertThat(entry.task().getId()).isEqualTo(sync.getId()));
    }

    private void execute(String sql, Object... parameters) {
        var query = entities.getEntityManager().createNativeQuery(sql);
        for (int index = 0; index < parameters.length; index++) query.setParameter(index + 1, parameters[index]);
        query.executeUpdate();
    }

    @Test
    void suiteSearchAndPresentationUseTheTaskBoundVersionInsteadOfPublishedContainer() {
        Namespace team = namespace("queue-suite-snapshot", NamespaceType.TEAM);
        SkillSuite suite = entities.persistFlushFind(new SkillSuite(team.getId(), "suite", "Published A", "queue-owner"));
        var pending = entities.persistFlushFind(new com.iflytek.skillhub.domain.suite.SkillSuiteVersion(
                suite.getId(), "2.0.0", "Pending B", "Pending summary", SkillVisibility.PUBLIC, "queue-author"));
        ReviewTask task = entities.persistFlushFind(ReviewTask.forSuiteVersion(
                pending.getId(), suite.getId(), team.getId(), "2.0.0", "queue-author"));
        assertThat(repository.list(filter(null, null, "Pending B", null, null), true, Set.of(), page(0, 20)))
                .singleElement().satisfies(entry -> {
                    assertThat(entry.task().getId()).isEqualTo(task.getId());
                    assertThat(entry.displayName()).isEqualTo("Pending B");
                    assertThat(entry.summary()).isEqualTo("Pending summary");
                });
        assertThat(repository.list(filter(null, null, "Published A", null, null), true, Set.of(), page(0, 20))).isEmpty();
    }

    @Test
    void packageTextProjectionMatchesSkillMetadataForTypesWhitespaceAndUtf16Limits() throws Exception {
        Namespace team = namespace("queue-metadata-parity", NamespaceType.TEAM);
        Object[][] cases = {
            {123, 456}, {"   ", " \t "}, {"\u3000 Trimmed \u2000", "\u2000 Summary \u3000"},
            {"x".repeat(200), "summary"}, {"x".repeat(201), "summary"},
            {"😀".repeat(100), "summary"}, {"😀".repeat(101), "summary"}
        };
        java.util.Map<Long, com.iflytek.skillhub.domain.skill.metadata.SkillMetadata> expected = new java.util.HashMap<>();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (int index = 0; index < cases.length; index++) {
            ReviewTask task = task(team, "parity-" + index, "1.0.0", "2026-01-01T00:00:00Z");
            var metadata = new com.iflytek.skillhub.domain.skill.metadata.SkillMetadata(
                    "package-" + index, "description-" + index, "1.0.0", "body", java.util.Map.of(
                    "metadata", java.util.Map.of("display-name", cases[index][0], "short-description", cases[index][1])));
            execute("UPDATE skill_version SET parsed_metadata_json = CAST(?1 AS jsonb) WHERE id = ?2",
                    mapper.writeValueAsString(metadata), task.getSkillVersionId());
            expected.put(task.getId(), metadata);
        }
        var entries = repository.list(filter(null, team.getId(), "", null, null), true, Set.of(), page(0, 20));
        assertThat(entries).hasSize(cases.length);
        for (var entry : entries) {
            assertThat(entry.displayName()).isEqualTo(expected.get(entry.task().getId()).displayName());
            assertThat(entry.summary()).isEqualTo(expected.get(entry.task().getId()).shortDescription());
        }
        assertThat(repository.list(filter(null, null, "Trimmed", null, null), true, Set.of(), page(0, 20))).hasSize(1);
    }

    @Test
    void fixedSnapshotGroupsAreAuthorizedAndCountedBeforeMemberPagination() {
        Namespace team = namespace("group-team", NamespaceType.TEAM);
        Namespace other = namespace("group-other", NamespaceType.TEAM);
        long snapshot = snapshot(team, "skills", 3);
        snapshot(other, "private-repository", 2);
        entities.clear();
        var filter = filter("PENDING", null, "", null, "GITHUB_SYNC");
        var result = groups.list(filter, Set.of(team.getId()), 0, 1, false);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).singleElement().satisfies(group -> {
            assertThat(group.snapshotId()).isEqualTo(snapshot);
            assertThat(group.total()).isEqualTo(3);
            assertThat(group.scanPassed()).isEqualTo(1);
            assertThat(group.scanWaiting()).isEqualTo(1);
            assertThat(group.scanBlocked()).isEqualTo(1);
        });
        assertThat(repository.list(filter("PENDING", null, "upstream/skills", null, "GITHUB_SYNC"), false,
                Set.of(team.getId()), page(0, 1)).getTotalElements()).isEqualTo(3);
        var first = groups.members(filter, Set.of(team.getId()), snapshot, 0, 2, false);
        var second = groups.members(filter, Set.of(team.getId()), snapshot, 1, 2, false);
        assertThat(first.total()).isEqualTo(3);
        assertThat(first.items()).hasSize(2);
        assertThat(second.items()).hasSize(1).doesNotContainAnyElementsOf(first.items());
        assertThat(groups.members(filter, Set.of(other.getId()), snapshot, 0, 20, false).items()).isEmpty();
        assertThat(groups.list(filter("PENDING", null, "skills", null, "GITHUB_SYNC"), Set.of(team.getId()), 0, 20, false).total()).isEqualTo(1);
        assertThat(groups.list(filter("PENDING", null, "%", null, "GITHUB_SYNC"), Set.of(team.getId()), 0, 20, false).items()).isEmpty();
        assertThat(groups.list(filter, Set.of(), 0, 20, false).items()).isEmpty();
    }

    @Test
    void verdictMatchesOnlyTheCurrentSuccessfulRoundWithinTheAuthorizedPage() {
        Namespace team = namespace("verdict-team", NamespaceType.TEAM);
        Namespace foreign = namespace("verdict-foreign", NamespaceType.TEAM);
        ReviewTask first = task(team, "versioned", "1.0.0", "2026-01-01T00:00:00Z");
        SkillVersion second = entities.persistFlushFind(new SkillVersion(first.getSubjectId(), "2.0.0", "queue-owner"));
        ReviewTask update = entities.persistFlushFind(new ReviewTask(second.getId(), first.getSubjectId(),
                team.getId(), "2.0.0", "queue-author"));
        ReviewTask other = task(team, "other", "1.0.0", "2026-01-02T00:00:00Z");
        ReviewTask hidden = task(foreign, "hidden", "1.0.0", "2026-01-03T00:00:00Z");
        audit(first, "SAFE", true, null);
        audit(update, "DANGEROUS", true, null);
        audit(other, "DANGEROUS", true, null);
        audit(hidden, "DANGEROUS", true, null);
        var risk = new ReviewQueueQueryRepository.Filter("PENDING", null, "", null, null, "DANGEROUS");
        var managed = Set.of(team.getId());
        var firstPage = repository.list(risk, false, managed, page(0, 1));
        var secondPage = repository.list(risk, false, managed, page(1, 1));
        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(java.util.stream.Stream.concat(firstPage.stream(), secondPage.stream()))
                .extracting(entry -> entry.task().getId()).containsExactlyInAnyOrder(update.getId(), other.getId());
        audit(update, "SUSPICIOUS", false, null);
        audit(other, "SUSPICIOUS", true, "timeout");
        assertThat(repository.list(risk, false, managed, page(0, 20))).isEmpty();
        assertThat(repository.list(new ReviewQueueQueryRepository.Filter("PENDING", null, "", null, null,
                "SUSPICIOUS"), false, managed, page(0, 20))).isEmpty();
        var all = repository.list(filter("PENDING", null, "", null, null), false, managed, page(0, 20));
        assertThat(all.getContent()).extracting(ReviewQueueQueryRepository.Entry::scanVerdict)
                .containsExactlyInAnyOrder("SAFE", null, null);
    }

    @Test
    void scanRestartBetweenCountAndRowsKeepsOneDatabaseSnapshot() throws Exception {
        Namespace team = namespace("verdict-snapshot", NamespaceType.TEAM);
        ReviewTask review = task(team, "changing", "1.0.0", "2026-01-01T00:00:00Z");
        audit(review, "DANGEROUS", true, null);
        entities.flush();
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();
        org.springframework.test.context.transaction.TestTransaction.end();
        var target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(repository);
        var original = (jakarta.persistence.EntityManager) ReflectionTestUtils.getField(target, "entityManager");
        var restarted = new java.util.concurrent.atomic.AtomicBoolean();
        var intercepted = (jakarta.persistence.EntityManager) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{jakarta.persistence.EntityManager.class},
                (proxy, method, arguments) -> {
                    Object result = method.invoke(original, arguments);
                    if (method.getName().equals("createNativeQuery") && arguments[0] instanceof String sql
                            && sql.startsWith("SELECT COUNT(*)")) {
                        var query = (jakarta.persistence.Query) result;
                        return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                                new Class<?>[]{jakarta.persistence.Query.class}, (queryProxy, operation, values) -> {
                                    Object value = operation.invoke(query, values);
                                    if (operation.getName().equals("getSingleResult") && restarted.compareAndSet(false, true)) {
                                        try (var connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                                                POSTGRES.getUsername(), POSTGRES.getPassword());
                                             var statement = connection.prepareStatement("""
                                                INSERT INTO security_audit
                                                    (skill_version_id, task_id, scanner_type, verdict, is_safe)
                                                VALUES (?, ?, 'SKILL_SCANNER', 'SUSPICIOUS', false)
                                                """)) {
                                            statement.setLong(1, review.getSkillVersionId());
                                            statement.setString(2, java.util.UUID.randomUUID().toString());
                                            statement.executeUpdate();
                                        }
                                    }
                                    return value == query ? queryProxy : value;
                                });
                    }
                    return result;
                });
        ReflectionTestUtils.setField(target, "entityManager", intercepted);
        try {
            var risk = new ReviewQueueQueryRepository.Filter("PENDING", team.getId(), "", null, null, "DANGEROUS");
            var result = repository.list(risk, false, Set.of(team.getId()), page(0, 20));
            assertThat(restarted).isTrue();
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).singleElement().satisfies(entry -> {
                assertThat(entry.task().getId()).isEqualTo(review.getId());
                assertThat(entry.scanVerdict()).isEqualTo("DANGEROUS");
            });
            assertThat(repository.list(risk, false, Set.of(team.getId()), page(0, 20))).isEmpty();
        } finally {
            ReflectionTestUtils.setField(target, "entityManager", original);
            try (var connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                    POSTGRES.getUsername(), POSTGRES.getPassword()); var statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM review_task WHERE id = " + review.getId());
                statement.executeUpdate("DELETE FROM security_audit WHERE skill_version_id = " + review.getSkillVersionId());
                statement.executeUpdate("DELETE FROM skill_version WHERE id = " + review.getSkillVersionId());
                statement.executeUpdate("DELETE FROM skill WHERE id = " + review.getSubjectId());
                statement.executeUpdate("DELETE FROM namespace WHERE id = " + team.getId());
                statement.executeUpdate("DELETE FROM user_account WHERE id IN ('queue-owner', 'queue-author')");
            }
        }
    }

    private void audit(ReviewTask task, String verdict, boolean finished, String failure) {
        var audit = new com.iflytek.skillhub.domain.security.SecurityAudit(task.getSkillVersionId(),
                com.iflytek.skillhub.domain.security.ScannerType.SKILL_SCANNER, java.util.UUID.randomUUID().toString());
        audit.setVerdict(com.iflytek.skillhub.domain.security.SecurityVerdict.valueOf(verdict));
        audit.setIsSafe("SAFE".equals(verdict));
        if (finished) audit.setScannedAt(Instant.now());
        if (failure != null) audit.markFailed(Instant.now(), failure);
        entities.persistAndFlush(audit);
    }

    private long scalar(String sql, Object... parameters) {
        var query = entities.getEntityManager().createNativeQuery(sql);
        for (int index = 0; index < parameters.length; index++) query.setParameter(index + 1, parameters[index]);
        return ((Number) query.getSingleResult()).longValue();
    }

    private long snapshot(Namespace team, String repositoryName, int count) {
        var run = java.util.UUID.randomUUID(); var acquisition = java.util.UUID.randomUUID();
        long source = scalar("INSERT INTO github_source_binding (namespace_id, repository_id, owner_login, repository_name, visibility) VALUES (?1, 100, 'upstream', ?2, 'PUBLIC') RETURNING id", team.getId(), repositoryName);
        execute("INSERT INTO github_sync_run (id, namespace_id, kind) VALUES (?1, ?2, 'REPOSITORY_SYNC')", run, team.getId());
        execute("INSERT INTO github_sync_task (id, run_id, namespace_id, source_id, kind, mode, dedupe_key, snapshot_json) VALUES (?1, ?2, ?3, ?4, 'REPOSITORY_SYNC', 'FULL', ?5, CAST('{}' AS jsonb))", acquisition, run, team.getId(), source, acquisition.toString());
        long snapshot = scalar("INSERT INTO github_repository_snapshot (source_id, acquisition_task_id, commit_sha, ignore_patterns, captured_at) VALUES (?1, ?2, ?3, CAST('[]' AS jsonb), CURRENT_TIMESTAMP) RETURNING id", source, acquisition, "a".repeat(40));
        for (int index = 0; index < count; index++) {
            var admission = java.util.UUID.randomUUID(); var upload = java.util.UUID.randomUUID(); var ingestion = java.util.UUID.randomUUID();
            String path = "skill-" + index;
            String hash = java.util.UUID.randomUUID().toString().replace("-", "").repeat(2);
            var review = task(team, path, "1.0.0", "2026-01-01T00:00:00Z");
            execute("INSERT INTO github_sync_package_admission (id, task_id, namespace_id, repository_id, commit_sha, skill_path, lease_generation) VALUES (?1, ?2, ?3, 100, ?4, ?5, 1)", admission, acquisition, team.getId(), "a".repeat(40), path);
            execute("INSERT INTO github_sync_staged_upload (id, admission_id, object_key, mode, ticket_hash, size_bytes, sha256, status, expires_at) VALUES (?1, ?2, ?3, 'LOCAL_STREAM', ?4, 1, ?4, 'ACCEPTED', CURRENT_TIMESTAMP + INTERVAL '1 day')", upload, admission, source + "/" + path, hash);
            execute("INSERT INTO github_sync_package_ingestion (id, task_id, admission_id, upload_id, namespace_id, repository_id, commit_sha, skill_path, object_key, size_bytes, sha256, status) VALUES (?1, ?2, ?3, ?4, ?5, 100, ?6, ?7, ?7, 1, ?8, 'VALIDATED')", ingestion, acquisition, admission, upload, team.getId(), "a".repeat(40), path, hash);
            if (index < 2) {
                var audit = new com.iflytek.skillhub.domain.security.SecurityAudit(review.getSkillVersionId(),
                        com.iflytek.skillhub.domain.security.ScannerType.SKILL_SCANNER);
                audit.setVerdict(index == 0 ? com.iflytek.skillhub.domain.security.SecurityVerdict.SAFE
                        : com.iflytek.skillhub.domain.security.SecurityVerdict.DANGEROUS);
                audit.setIsSafe(index == 0);
                audit.setScannedAt(Instant.now());
                entities.persistAndFlush(audit);
            }
            execute("INSERT INTO github_repository_snapshot_member (snapshot_id, skill_path, ingestion_id) VALUES (?1, ?2, ?3)", snapshot, path, ingestion);
            execute("UPDATE review_task SET origin_type = 'GITHUB_SYNC', github_sync_ingestion_id = ?1 WHERE id = ?2", ingestion, review.getId());
        }
        return snapshot;
    }

    private Namespace namespace(String slug, NamespaceType type) {
        Namespace namespace = new Namespace(slug, slug, "queue-owner"); namespace.setType(type);
        return entities.persistFlushFind(namespace);
    }

    private ReviewTask task(Namespace namespace, String slug, String number, String submittedAt) {
        Skill skill = entities.persistFlushFind(new Skill(namespace.getId(), slug, "queue-owner", SkillVisibility.PRIVATE));
        SkillVersion version = entities.persistFlushFind(new SkillVersion(skill.getId(), number, "queue-owner"));
        ReviewTask task = new ReviewTask(version.getId(), skill.getId(), namespace.getId(), number, "queue-author");
        ReflectionTestUtils.setField(task, "submittedAt", Instant.parse(submittedAt));
        return entities.persistFlushFind(task);
    }

    private ReviewQueueQueryRepository.Filter filter(String status, Long namespace, String query, String kind, String origin) {
        return new ReviewQueueQueryRepository.Filter(status, namespace, query, kind, origin);
    }

    private PageRequest page(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }
}
