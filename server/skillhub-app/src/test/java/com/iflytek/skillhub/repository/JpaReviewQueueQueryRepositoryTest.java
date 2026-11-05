package com.iflytek.skillhub.repository;

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
@Import(JpaReviewQueueQueryRepository.class)
@Testcontainers
class JpaReviewQueueQueryRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

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
