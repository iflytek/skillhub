package com.iflytek.skillhub.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.SkillSuiteBundleProperties;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersion;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleCoordinate;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberSourceType;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMode;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleOperationStatus;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSession;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSessionRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewStatus;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePublishAction;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleRelationshipChange;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundleConfirmationAppService;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundlePreviewPlanner;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundlePreviewRevalidationService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class SkillSuiteBundlePersistenceTest {

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() { };

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
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired private jakarta.persistence.EntityManager entityManager;
    @Autowired private SkillSuiteBundlePreviewSessionRepository previewRepository;
    @Autowired private SkillSuiteBundleExecutionOperationRepository operationRepository;
    @Autowired private SkillSuiteBundleMemberResultRepository memberRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void previewsForTheSameCreateTargetCanCoexistWithoutReservingIt() {
        Namespace namespace = persistNamespace("bundle-preview");
        previewRepository.save(preview("preview-a", "actor-a", namespace.getId(), "target", null, null));
        previewRepository.save(preview("preview-b", "actor-b", namespace.getId(), "target", null, null));
        entityManager.flush();
        entityManager.clear();

        assertThat(previewRepository.findById("preview-a")).isPresent();
        assertThat(previewRepository.findById("preview-b")).isPresent();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void activeCreateReservationIsUniqueAndAReleasedReservationCanBeReacquired() {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        Long namespaceId = transactions.execute(status -> persistNamespace("bundle-create-lock").getId());
        transactions.executeWithoutResult(status -> {
            previewRepository.save(preview("create-preview-a", "create-actor-a", namespaceId, "target", null, null));
            operationRepository.save(operation(
                    "create-op-a", "create-preview-a", "create-request-a", "create-actor-a",
                    SkillSuiteBundleMode.CREATE, namespaceId, "target", null, null));
        });

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            previewRepository.save(preview("create-preview-b", "create-actor-b", namespaceId, "target", null, null));
            operationRepository.save(operation(
                    "create-op-b", "create-preview-b", "create-request-b", "create-actor-b",
                    SkillSuiteBundleMode.CREATE, namespaceId, "target", null, null));
        })).isInstanceOf(DataIntegrityViolationException.class);

        transactions.executeWithoutResult(status -> {
            SkillSuiteBundleExecutionOperation first = operationRepository.findById("create-op-a").orElseThrow();
            first.transition(SkillSuiteBundleOperationStatus.CANCELLED, now().plusSeconds(5));
            operationRepository.save(first);
        });
        transactions.executeWithoutResult(status -> {
            previewRepository.save(preview("create-preview-c", "create-actor-c", namespaceId, "target", null, null));
            operationRepository.save(operation(
                    "create-op-c", "create-preview-c", "create-request-c", "create-actor-c",
                    SkillSuiteBundleMode.CREATE, namespaceId, "target", null, null));
        });

        assertThat(operationRepository.findById("create-op-c")).isPresent();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCreateConfirmationsAcquireExactlyOneReservation() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        Long namespaceId = transactions.execute(status -> persistNamespace("bundle-concurrent-lock").getId());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attemptReservation(
                    transactions, ready, start, namespaceId, null, null, "concurrent-a"));
            var second = executor.submit(() -> attemptReservation(
                    transactions, ready, start, namespaceId, null, null, "concurrent-b"));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentUpdateConfirmationsAcquireExactlyOneReservation() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        SuiteFixture fixture = transactions.execute(status -> persistSuite("bundle-concurrent-update-lock"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attemptReservation(
                    transactions, ready, start, fixture.namespaceId(), fixture.suiteId(),
                    fixture.baseVersionId(), "concurrent-update-a"));
            var second = executor.submit(() -> attemptReservation(
                    transactions, ready, start, fixture.namespaceId(), fixture.suiteId(),
                    fixture.baseVersionId(), "concurrent-update-b"));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void confirmationServiceAtomicallyReservesTargetAndReplaysAfterResponseLoss() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        Long namespaceId = transactions.execute(status -> persistNamespace("bundle-confirm-service").getId());
        ObjectMapper objectMapper = new ObjectMapper();
        SkillSuiteBundlePreviewPlanner.PreviewPlan plan = confirmationPlan(namespaceId);
        var manifest = new com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleManifestParser().parse("""
                apiVersion: skillhub.iflytek.com/v1alpha1
                kind: SkillSuiteBundle
                metadata:
                  namespace: bundle-confirm-service
                  slug: target
                spec:
                  mode: CREATE
                  version: 1.1.0
                  displayName: Target
                  summary: Summary
                  overview: Overview
                  visibility: PUBLIC
                  entry: "@bundle-confirm-service/member"
                  members:
                    - skill: "@bundle-confirm-service/member"
                      package:
                        path: skills/member
                        visibility: PUBLIC
                """);
        transactions.executeWithoutResult(status -> {
            previewRepository.save(confirmablePreview(
                    "confirm-service-a", "actor-a", namespaceId, manifest, plan, objectMapper));
            previewRepository.save(confirmablePreview(
                    "confirm-service-b", "actor-b", namespaceId, manifest, plan, objectMapper));
        });

        SkillSuiteBundlePreviewPlanner planner = mock(SkillSuiteBundlePreviewPlanner.class);
        when(planner.plan(any(), any(), any(), any())).thenReturn(plan);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        when(storage.exists(any())).thenReturn(true);
        SkillSuiteBundleProperties properties = new SkillSuiteBundleProperties();
        properties.setConfirmationEnabled(true);
        SkillSuiteBundlePreviewRevalidationService revalidation =
                new SkillSuiteBundlePreviewRevalidationService(planner, storage, objectMapper);
        SkillSuiteBundleConfirmationAppService confirmation = new SkillSuiteBundleConfirmationAppService(
                previewRepository, operationRepository, memberRepository, revalidation, properties,
                mock(org.springframework.context.ApplicationEventPublisher.class),
                java.time.Clock.fixed(now(), java.time.ZoneOffset.UTC));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attemptConfirmation(
                    transactions, confirmation, ready, start,
                    "confirm-service-a", "request-a", "actor-a"));
            var second = executor.submit(() -> attemptConfirmation(
                    transactions, confirmation, ready, start,
                    "confirm-service-b", "request-b", "actor-b"));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
        }

        SkillSuiteBundleExecutionOperation winner = transactions.execute(status -> operationRepository
                .findByPreviewToken("confirm-service-a")
                .or(() -> operationRepository.findByPreviewToken("confirm-service-b"))
                .orElseThrow());
        SkillSuiteBundleConfirmationAppService.ConfirmationOutcome replay = transactions.execute(status ->
                confirmation.confirm(
                        winner.getPreviewToken(), winner.getClientRequestId(), winner.getWarningDigest(),
                        winner.getActorId(), Map.of(), java.util.Set.of()));
        assertThat(replay.operationId()).isEqualTo(winner.getOperationId());
        assertThat(replay.replayed()).isTrue();
    }

    @Test
    void updateReservationRejectsMissingSuiteIdentity() {
        assertThatThrownBy(() -> SkillSuiteBundleExecutionOperation.reservationKey(
                SkillSuiteBundleMode.UPDATE, 1L, "target", null, "1.1.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UPDATE reservation requires suite and target version");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void updateReservationIsScopedToSuiteAndTargetVersion() {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        SuiteFixture fixture = transactions.execute(status -> persistSuite("bundle-update-lock"));
        transactions.executeWithoutResult(status -> {
            previewRepository.save(preview(
                    "update-preview-a", "update-actor-a", fixture.namespaceId(), "target",
                    fixture.suiteId(), fixture.baseVersionId()));
            operationRepository.save(operation(
                    "update-op-a", "update-preview-a", "update-request-a", "update-actor-a",
                    SkillSuiteBundleMode.UPDATE, fixture.namespaceId(), "target",
                    fixture.suiteId(), fixture.baseVersionId()));
        });

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            previewRepository.save(preview(
                    "update-preview-b", "update-actor-b", fixture.namespaceId(), "target",
                    fixture.suiteId(), fixture.baseVersionId()));
            operationRepository.save(operation(
                    "update-op-b", "update-preview-b", "update-request-b", "update-actor-b",
                    SkillSuiteBundleMode.UPDATE, fixture.namespaceId(), "target",
                    fixture.suiteId(), fixture.baseVersionId()));
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void operationAndMemberPlanRemainReadableAfterPersistenceContextIsCleared() {
        Namespace namespace = persistNamespace("bundle-recovery");
        previewRepository.save(preview("recovery-preview", "actor", namespace.getId(), "target", null, null));
        operationRepository.save(operation(
                "recovery-op", "recovery-preview", "request", "actor",
                SkillSuiteBundleMode.CREATE, namespace.getId(), "target", null, null));
        memberRepository.saveAll(List.of(new SkillSuiteBundleMemberResult(
                "recovery-op", 0, new SkillSuiteBundleCoordinate("global", "member"),
                SkillSuiteBundleMemberSourceType.PACKAGE, "members/member", SkillVisibility.PUBLIC,
                "1.0.0", SkillSuiteBundleRelationshipChange.ADDED,
                SkillSuiteBundlePublishAction.CREATE_SKILL, "sha256:member", null, null,
                List.of(), List.of("review warning"), now())));
        entityManager.flush();
        entityManager.clear();

        SkillSuiteBundleExecutionOperation operation = operationRepository.findById("recovery-op").orElseThrow();
        assertThat(operation.getPlan()).containsEntry("memberCount", 1);
        assertThat(operation.isReservationActive()).isTrue();
        assertThat(memberRepository.findByOperationIdOrderByPosition("recovery-op"))
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.getSkillSlug()).isEqualTo("member");
                    assertThat(member.getWarnings()).containsExactly("review warning");
                });
    }

    @Test
    void cleanupQueriesReturnOnlyExpiredPreviewsAndUncleanedTerminalOperations() {
        Namespace namespace = persistNamespace("bundle-cleanup-query");
        SkillSuiteBundlePreviewSession expired = preview(
                "cleanup-expired", "actor", namespace.getId(), "expired", null, null);
        expired.markExpired();
        previewRepository.save(expired);
        SkillSuiteBundlePreviewSession activePreview = preview(
                "cleanup-active-preview", "actor", namespace.getId(), "active", null, null);
        activePreview.markConfirmed(now());
        previewRepository.save(activePreview);
        operationRepository.save(operation(
                "cleanup-active", "cleanup-active-preview", "cleanup-active-request", "actor",
                SkillSuiteBundleMode.CREATE, namespace.getId(), "active", null, null));
        SkillSuiteBundlePreviewSession terminalPreview = preview(
                "cleanup-terminal-preview", "actor", namespace.getId(), "terminal", null, null);
        terminalPreview.markConfirmed(now());
        previewRepository.save(terminalPreview);
        SkillSuiteBundleExecutionOperation terminal = operation(
                "cleanup-terminal", "cleanup-terminal-preview", "cleanup-request", "actor",
                SkillSuiteBundleMode.CREATE, namespace.getId(), "terminal", null, null);
        terminal.cancel(now().plusSeconds(1));
        operationRepository.save(terminal);
        entityManager.flush();
        entityManager.clear();

        assertThat(previewRepository
                .findTop100ByStatusAndStagedObjectsCleanedAtIsNullOrderByExpiresAtAsc(
                        SkillSuiteBundlePreviewStatus.EXPIRED))
                .extracting(SkillSuiteBundlePreviewSession::getToken)
                .containsExactly("cleanup-expired");
        assertThat(operationRepository
                .findTop100ByStatusInAndStagedObjectsCleanedAtIsNullOrderByCompletedAtAsc(Set.of(
                        SkillSuiteBundleOperationStatus.REPREVIEW_REQUIRED,
                        SkillSuiteBundleOperationStatus.SUITE_DRAFT_CREATED,
                        SkillSuiteBundleOperationStatus.CANCELLED)))
                .extracting(SkillSuiteBundleExecutionOperation::getOperationId)
                .contains("cleanup-terminal")
                .doesNotContain("cleanup-active");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollingBackConfirmationLeavesNoOperationOrSuiteLifecycleRows() {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        Long namespaceId = transactions.execute(status -> persistNamespace("bundle-rollback").getId());

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            previewRepository.save(preview(
                    "rollback-preview", "rollback-actor", namespaceId, "target", null, null));
            operationRepository.save(operation(
                    "rollback-op", "rollback-preview", "rollback-request", "rollback-actor",
                    SkillSuiteBundleMode.CREATE, namespaceId, "target", null, null));
            entityManager.flush();
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(operationRepository.findById("rollback-op")).isEmpty();
        assertThat(previewRepository.findById("rollback-preview")).isEmpty();
        Long suiteCount = transactions.execute(status -> entityManager.createQuery(
                        "SELECT COUNT(suite) FROM SkillSuite suite WHERE suite.namespaceId = :namespaceId", Long.class)
                .setParameter("namespaceId", namespaceId)
                .getSingleResult());
        assertThat(suiteCount).isZero();
    }

    private boolean attemptReservation(
            TransactionTemplate transactions, CountDownLatch ready, CountDownLatch start,
            Long namespaceId, Long suiteId, Long baseVersionId, String suffix
    ) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to start concurrent confirmation");
            }
            transactions.executeWithoutResult(status -> {
                String previewToken = "preview-" + suffix;
                SkillSuiteBundleMode mode = suiteId == null
                        ? SkillSuiteBundleMode.CREATE
                        : SkillSuiteBundleMode.UPDATE;
                previewRepository.save(preview(
                        previewToken, "actor-" + suffix, namespaceId, "target", suiteId, baseVersionId));
                operationRepository.save(operation(
                        "operation-" + suffix, previewToken, "request-" + suffix, "actor-" + suffix,
                        mode, namespaceId, "target", suiteId, baseVersionId));
            });
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting to confirm", exception);
        }
    }

    private boolean attemptConfirmation(
            TransactionTemplate transactions,
            SkillSuiteBundleConfirmationAppService confirmation,
            CountDownLatch ready,
            CountDownLatch start,
            String previewToken,
            String requestId,
            String actorId
    ) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to start concurrent confirmation");
            }
            transactions.execute(status -> confirmation.confirm(
                    previewToken, requestId, "warning-digest", actorId, Map.of(), java.util.Set.of()));
            return true;
        } catch (DomainConflictException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting to confirm", exception);
        }
    }

    private SkillSuiteBundlePreviewSession confirmablePreview(
            String token,
            String actor,
            Long namespaceId,
            com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleManifest manifest,
            SkillSuiteBundlePreviewPlanner.PreviewPlan plan,
            ObjectMapper objectMapper
    ) {
        return new SkillSuiteBundlePreviewSession(
                token, actor, SkillSuiteBundleMode.CREATE, namespaceId, "target", null, null, "1.1.0",
                "temporary/" + token + ".zip", "a".repeat(64),
                objectMapper.convertValue(manifest, JSON_OBJECT), objectMapper.convertValue(plan, JSON_OBJECT),
                "warning-digest", now().plus(30, ChronoUnit.MINUTES), now());
    }

    private SkillSuiteBundlePreviewPlanner.PreviewPlan confirmationPlan(Long namespaceId) {
        SkillSuiteBundlePreviewPlanner.MemberPlan member = new SkillSuiteBundlePreviewPlanner.MemberPlan(
                new SkillSuiteBundleCoordinate("bundle-confirm-service", "member"),
                SkillSuiteBundleMemberSourceType.PACKAGE,
                SkillSuiteBundleRelationshipChange.ADDED, SkillSuiteBundlePublishAction.CREATE_SKILL,
                null, null, SkillVisibility.PUBLIC, "1.0.0", "sha256:member",
                List.of(), List.of(), List.of());
        return new SkillSuiteBundlePreviewPlanner.PreviewPlan(
                SkillSuiteBundleMode.CREATE,
                new SkillSuiteBundleCoordinate("bundle-confirm-service", "target"), namespaceId,
                null, null, "1.1.0", "Target", "Summary", "Overview", SkillVisibility.PUBLIC,
                List.of(member), List.of(), List.of(), List.of(), "warning-digest");
    }

    private Namespace persistNamespace(String slug) {
        String ownerId = "owner-" + slug;
        entityManager.persist(new UserAccount(ownerId, ownerId, null, null));
        Namespace namespace = new Namespace(slug, slug, ownerId);
        entityManager.persist(namespace);
        entityManager.flush();
        return namespace;
    }

    private SuiteFixture persistSuite(String namespaceSlug) {
        Namespace namespace = persistNamespace(namespaceSlug);
        String ownerId = "owner-" + namespaceSlug;
        SkillSuite suite = new SkillSuite(namespace.getId(), "target", "Target", ownerId);
        entityManager.persist(suite);
        SkillSuiteVersion version = new SkillSuiteVersion(
                suite.getId(), "1.0.0", "Target", "Summary", SkillVisibility.PUBLIC, ownerId);
        entityManager.persist(version);
        entityManager.flush();
        return new SuiteFixture(namespace.getId(), suite.getId(), version.getId());
    }

    private SkillSuiteBundlePreviewSession preview(
            String token, String actor, Long namespaceId, String slug, Long suiteId, Long baseVersionId
    ) {
        SkillSuiteBundleMode mode = suiteId == null ? SkillSuiteBundleMode.CREATE : SkillSuiteBundleMode.UPDATE;
        return new SkillSuiteBundlePreviewSession(
                token, actor, mode, namespaceId, slug, suiteId, baseVersionId, "1.1.0",
                "temporary/" + token + ".zip", "a".repeat(64), Map.of("kind", "SkillSuiteBundle"),
                Map.of("memberCount", 1), "b".repeat(64), now().plus(30, ChronoUnit.MINUTES), now());
    }

    private SkillSuiteBundleExecutionOperation operation(
            String operationId, String previewToken, String requestId, String actor,
            SkillSuiteBundleMode mode, Long namespaceId, String slug, Long suiteId, Long baseVersionId
    ) {
        return new SkillSuiteBundleExecutionOperation(
                operationId, previewToken, requestId, actor, mode, namespaceId, slug, suiteId,
                baseVersionId, "1.1.0", "temporary/" + previewToken + ".zip", "a".repeat(64),
                Map.of("memberCount", 1), "b".repeat(64), now());
    }

    private Instant now() {
        return Instant.parse("2026-09-11T04:00:00Z");
    }

    private record SuiteFixture(Long namespaceId, Long suiteId, Long baseVersionId) {
    }
}
