package com.iflytek.skillhub.integration;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersion;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleCoordinate;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberSourceType;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMode;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSession;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePublishAction;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleRelationshipChange;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.repository.SkillSuiteBundleOperationQueryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(SkillSuiteBundleOperationQueryRepository.class)
@Testcontainers
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF"
})
class SkillSuiteBundleOperationQueryRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SkillSuiteBundleOperationQueryRepository repository;

    @Test
    void pagesOnlyTheActorsActiveOperationsAndAggregatesMemberStatusesInPostgres() {
        entityManager.persist(new UserAccount("actor", "Actor", null, null));
        Namespace namespace = entityManager.persistFlushFind(
                new Namespace("team-ai", "AI Team", "actor"));
        SkillSuite suite = entityManager.persistFlushFind(
                new SkillSuite(namespace.getId(), "care-suite", "Care Suite", "actor"));
        SkillSuiteVersion baseVersion = entityManager.persistFlushFind(
                new SkillSuiteVersion(suite.getId(), "1.0.0", SkillVisibility.PUBLIC, "actor"));
        SkillSuiteBundlePreviewSession preview = entityManager.persistFlushFind(
                new SkillSuiteBundlePreviewSession(
                        "preview-1", "actor", SkillSuiteBundleMode.UPDATE, namespace.getId(),
                        suite.getSlug(), suite.getId(), baseVersion.getId(), "1.1.0",
                        "staging/archive.zip", "a".repeat(64), Map.of(), Map.of(),
                        "warning-digest", NOW.plusSeconds(600), NOW));
        SkillSuiteBundleExecutionOperation operation = new SkillSuiteBundleExecutionOperation(
                "operation-1", preview.getToken(), "request-1", "actor", SkillSuiteBundleMode.UPDATE,
                namespace.getId(), suite.getSlug(), suite.getId(), baseVersion.getId(), "1.1.0",
                "staging/archive.zip", "a".repeat(64), Map.of(), "warning-digest", NOW);
        operation.markWaitingForMembers(NOW.plusSeconds(1));
        entityManager.persist(operation);
        entityManager.persist(member(0, "first", false));
        entityManager.persist(member(1, "second", true));
        entityManager.flush();

        var page = repository.findActive("actor", 0, 1);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(summary -> {
            assertThat(summary.operationId()).isEqualTo("operation-1");
            assertThat(summary.targetCoordinate()).isEqualTo("@team-ai/care-suite");
            assertThat(summary.baseVersion()).isEqualTo("1.0.0");
            assertThat(summary.totalMembers()).isEqualTo(2);
            assertThat(summary.completedMembers()).isZero();
            assertThat(summary.waitingMembers()).isEqualTo(1);
        });
        assertThat(repository.findActive("another-actor", 0, 1).items()).isEmpty();
    }

    private SkillSuiteBundleMemberResult member(int position, String slug, boolean waiting) {
        SkillSuiteBundleMemberResult member = new SkillSuiteBundleMemberResult(
                "operation-1", position, new SkillSuiteBundleCoordinate("team-ai", slug),
                SkillSuiteBundleMemberSourceType.PACKAGE, "members/" + slug,
                SkillVisibility.PUBLIC, "1.0.0", SkillSuiteBundleRelationshipChange.ADDED,
                SkillSuiteBundlePublishAction.CREATE_VERSION, "fingerprint-" + slug,
                null, null, List.of(), List.of(), NOW);
        if (waiting) member.markWaiting(NOW.plusSeconds(1));
        return member;
    }
}
