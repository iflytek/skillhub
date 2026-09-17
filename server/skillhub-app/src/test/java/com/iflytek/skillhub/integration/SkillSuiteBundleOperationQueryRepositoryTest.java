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
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleOperationStatus;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSession;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePublishAction;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleRelationshipChange;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.repository.SkillSuiteBundleOperationQueryRepository;
import com.iflytek.skillhub.repository.MySkillSuiteQueryRepository;
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
@Import({SkillSuiteBundleOperationQueryRepository.class, MySkillSuiteQueryRepository.class})
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

    @Autowired
    private MySkillSuiteQueryRepository workspaceRepository;

    @Test
    void workspacePagesTemporaryCreationsAndCountsAttentionAcrossAllPagesWithoutMemberQueries() {
        entityManager.persist(new UserAccount("actor", "Actor", null, null));
        Namespace namespace = entityManager.persistFlushFind(new Namespace("team-ai", "AI Team", "actor"));
        for (int index = 0; index < 15; index++) {
            var operation = createOperation("operation-" + Integer.toHexString(index), "actor", namespace, "temporary-" + index);
            if (index < 3) operation.markBlockedRetryable("MEMBER_EXECUTION_FAILED", "retry", NOW.plusSeconds(index));
            else operation.cancel(NOW.plusSeconds(index));
            entityManager.persist(operation);
        }
        entityManager.flush();
        var statistics = entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        var first = workspaceRepository.findWorkspace("actor", java.util.Set.of(namespace.getId()), java.util.Set.of(), "", "", 0, 12);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(first.items()).hasSize(12);
        assertThat(first.total()).isEqualTo(15);
        assertThat(first.attentionCount()).isEqualTo(3);
        assertThat(first.hasChangingOperations()).isFalse();
        assertThat(first.items().getFirst().state()).isEqualTo("ATTENTION");
        var second = workspaceRepository.findWorkspace("actor", java.util.Set.of(namespace.getId()), java.util.Set.of(), "", "", 1, 12);
        assertThat(second.items()).hasSize(3);
        assertThat(second.attentionCount()).isEqualTo(3);
        assertThat(workspaceRepository.findWorkspace("actor", java.util.Set.of(namespace.getId()), java.util.Set.of(), "", "ATTENTION", 0, 12).items()).hasSize(3);
        assertThat(workspaceRepository.findWorkspace("other", java.util.Set.of(namespace.getId()), java.util.Set.of(), "", "", 0, 12).total()).isZero();
        assertThat(workspaceRepository.findWorkspace("actor", java.util.Set.of(), java.util.Set.of(), "", "", 0, 12).total()).isZero();
        assertThat(workspaceRepository.findWorkspace("actor", java.util.Set.of(namespace.getId()), java.util.Set.of(), "%", "", 0, 12).total()).isZero();
        assertThat(workspaceRepository.findWorkspace("actor", java.util.Set.of(namespace.getId()), java.util.Set.of(), "team-ai/temporary-1", "", 0, 12).total()).isEqualTo(6);
    }

    @Test
    void workspaceMergesCreatedSuiteAndKeepsSuiteReviewWithoutABundleOperation() {
        entityManager.persist(new UserAccount("actor", "Actor", null, null));
        Namespace namespace = entityManager.persistFlushFind(new Namespace("team-ai", "AI Team", "actor"));
        var operation = createOperation("operation-1", "actor", namespace, "care-suite");
        operation.cancel(NOW.plusSeconds(1));
        entityManager.persist(operation);
        SkillSuite suite = entityManager.persistFlushFind(new SkillSuite(namespace.getId(), "care-suite", "Care", "actor"));
        SkillSuiteVersion version = new SkillSuiteVersion(suite.getId(), "1.0.0", SkillVisibility.PUBLIC, "actor");
        version.setDisplayName("Care");
        version.setStatus(com.iflytek.skillhub.domain.suite.SkillSuiteVersionStatus.PENDING_REVIEW);
        entityManager.persistAndFlush(version);
        var result = workspaceRepository.findWorkspace("actor", java.util.Set.of(namespace.getId()), java.util.Set.of(), "", "", 0, 12);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.suiteId()).isEqualTo(suite.getId());
            assertThat(item.state()).isEqualTo("PENDING_REVIEW");
            assertThat(item.operationId()).isNull();
        });
        assertThat(workspaceRepository.findWorkspace("admin", java.util.Set.of(namespace.getId()), java.util.Set.of(namespace.getId()), "", "PENDING_REVIEW", 0, 12).total()).isEqualTo(1);
        assertThat(workspaceRepository.findWorkspace("other", java.util.Set.of(namespace.getId()), java.util.Set.of(), "", "", 0, 12).total()).isZero();
    }

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
        SkillSuiteBundleExecutionOperation newest = createOperation(
                "operation-2", "actor", namespace, "newer-suite");
        newest.markWaitingForMembers(NOW.plusSeconds(2));
        entityManager.persist(newest);
        SkillSuiteBundleExecutionOperation terminal = createOperation(
                "operation-3", "actor", namespace, "finished-suite");
        terminal.cancel(NOW.plusSeconds(3));
        entityManager.persist(terminal);
        SkillSuiteBundleExecutionOperation anotherActor = createOperation(
                "operation-4", "another-actor", namespace, "other-suite");
        anotherActor.markWaitingForMembers(NOW.plusSeconds(4));
        entityManager.persist(anotherActor);
        entityManager.flush();

        var firstPage = repository.findActive("actor", 0, 1);
        var secondPage = repository.findActive("actor", 1, 1);

        assertThat(firstPage.total()).isEqualTo(2);
        assertThat(firstPage.items()).singleElement()
                .extracting(summary -> summary.operationId())
                .isEqualTo("operation-2");
        assertThat(secondPage.items()).singleElement().satisfies(summary -> {
            assertThat(summary.operationId()).isEqualTo("operation-1");
            assertThat(summary.targetCoordinate()).isEqualTo("@team-ai/care-suite");
            assertThat(summary.baseVersion()).isEqualTo("1.0.0");
            assertThat(summary.totalMembers()).isEqualTo(2);
            assertThat(summary.completedMembers()).isZero();
            assertThat(summary.waitingMembers()).isEqualTo(1);
        });
        assertThat(repository.findActive("another-actor", 0, 1).items()).singleElement()
                .extracting(summary -> summary.operationId())
                .isEqualTo("operation-4");
        var history = repository.findMine("actor", 0, 10);
        assertThat(history.total()).isEqualTo(3);
        assertThat(history.hasChangingOperations()).isTrue();
        assertThat(history.items()).extracting(summary -> summary.operationId())
                .containsExactly("operation-2", "operation-1", "operation-3");
        assertThat(repository.findMine("another-actor", 0, 10).items()).singleElement()
                .extracting(summary -> summary.operationId())
                .isEqualTo("operation-4");
        assertThat(entityManager.getEntityManager().createNativeQuery("""
                SELECT indexdef FROM pg_indexes
                WHERE indexname = 'idx_suite_bundle_operation_actor_status_updated'
                """).getSingleResult().toString())
                .contains("(actor_id, status, updated_at DESC, operation_id DESC)");
        assertThat(entityManager.getEntityManager().createNativeQuery("""
                SELECT indexdef FROM pg_indexes
                WHERE indexname = 'idx_suite_bundle_operation_actor_priority_updated'
                """).getSingleResult().toString())
                .contains("actor_id", "CASE", "updated_at DESC", "operation_id DESC");
    }

    @Test
    void prioritizesActionableAndChangingTasksAheadOfNewerTerminalHistory() {
        entityManager.persist(new UserAccount("actor", "Actor", null, null));
        Namespace namespace = entityManager.persistFlushFind(
                new Namespace("team-ai", "AI Team", "actor"));
        List<String> terminalTokens = List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b");
        for (int index = 0; index < terminalTokens.size(); index++) {
            String token = terminalTokens.get(index);
            SkillSuiteBundleExecutionOperation terminal = createOperation(
                    "operation-" + token, "actor", namespace, "terminal-" + token);
            terminal.cancel(NOW.plusSeconds(100L + index));
            entityManager.persist(terminal);
        }
        SkillSuiteBundleExecutionOperation running = createOperation(
                "operation-c", "actor", namespace, "running-suite");
        entityManager.persist(running);
        SkillSuiteBundleExecutionOperation blocked = createOperation(
                "operation-d", "actor", namespace, "blocked-suite");
        blocked.markBlockedRetryable("MEMBER_FAILED", "retry", NOW.plusSeconds(1));
        entityManager.persist(blocked);
        entityManager.flush();

        var page = repository.findMine("actor", 0, 12);

        assertThat(page.total()).isEqualTo(14);
        assertThat(page.hasChangingOperations()).isTrue();
        assertThat(page.items()).hasSize(12);
        assertThat(page.items()).extracting(summary -> summary.operationId())
                .startsWith("operation-d", "operation-c");
        assertThat(page.items()).extracting(summary -> summary.status())
                .startsWith(SkillSuiteBundleOperationStatus.BLOCKED_RETRYABLE,
                        SkillSuiteBundleOperationStatus.RUNNING);
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

    private SkillSuiteBundleExecutionOperation createOperation(
            String operationId,
            String actorId,
            Namespace namespace,
            String suiteSlug
    ) {
        String suffix = operationId.substring(operationId.lastIndexOf('-') + 1);
        SkillSuiteBundlePreviewSession preview = entityManager.persistFlushFind(
                new SkillSuiteBundlePreviewSession(
                        "preview-" + suffix, actorId, SkillSuiteBundleMode.CREATE, namespace.getId(),
                        suiteSlug, null, null, "1.0.0", "staging/" + suffix + ".zip",
                        suffix.repeat(64), Map.of(), Map.of(), "warning-" + suffix,
                        NOW.plusSeconds(600), NOW));
        return new SkillSuiteBundleExecutionOperation(
                operationId, preview.getToken(), "request-" + suffix, actorId,
                SkillSuiteBundleMode.CREATE, namespace.getId(), suiteSlug, null, null, "1.0.0",
                "staging/" + suffix + ".zip", suffix.repeat(64), Map.of(),
                "warning-" + suffix, NOW);
    }
}
