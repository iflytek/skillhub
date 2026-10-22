package com.iflytek.skillhub.authoring;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.domain.authoring.SkillDraft;
import com.iflytek.skillhub.domain.authoring.service.SkillDraftService;
import com.iflytek.skillhub.domain.authoring.service.ValidationRunService;
import com.iflytek.skillhub.domain.authoring.validation.ValidationFinding;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRun;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRunStatus;
import com.iflytek.skillhub.domain.authoring.validation.ValidationEvent;
import com.iflytek.skillhub.domain.authoring.validation.ValidationEventType;
import com.iflytek.skillhub.domain.authoring.validation.FindingDraft;
import com.iflytek.skillhub.domain.authoring.validation.FindingSeverity;
import com.iflytek.skillhub.domain.authoring.validation.ValidationLayer;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.service.authoring.ValidationRunOrchestrator;
import com.iflytek.skillhub.storage.ObjectMetadata;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Draft authoring and structure-layer validation against the real service
 * stack (Testcontainers PostgreSQL, in-memory object storage): create draft →
 * scaffold → break SKILL.md → validation run fails with a fixable finding →
 * repair the file → re-validation succeeds. Runs on real PostgreSQL because
 * the JSONB columns must round-trip exactly as they do in production.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestRedisConfig.class, AuthoringDraftFlowIntegrationTest.InMemoryStorageConfig.class})
@Testcontainers
class AuthoringDraftFlowIntegrationTest {

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

    private static final String USER = "author-user-1";
    private static final String NS_SLUG = "authoring-test-ns";

    @Autowired private ObjectStorageService objectStorage;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private SkillDraftService draftService;
    @Autowired private ValidationRunService runService;
    @Autowired private ValidationRunOrchestrator orchestrator;
    @Autowired private NamespaceRepository namespaceRepository;
    @Autowired private NamespaceService namespaceService;
    @Autowired private NamespaceMemberRepository namespaceMemberRepository;
    @Autowired private UserAccountRepository userAccountRepository;

    @TestConfiguration
    static class InMemoryStorageConfig {

        @Bean
        @Primary
        ObjectStorageService inMemoryObjectStorage() {
            return new InMemoryObjectStorage();
        }
    }

    static final class InMemoryObjectStorage implements ObjectStorageService {
        private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
        private volatile String blockedKey;
        private final CountDownLatch readEntered = new CountDownLatch(1);
        private final CountDownLatch releaseRead = new CountDownLatch(1);

        @Override
        public void putObject(String key, InputStream data, long size, String contentType) {
            try {
                objects.put(key, data.readAllBytes());
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public InputStream getObject(String key) {
            if (key.equals(blockedKey)) {
                readEntered.countDown();
                try {
                    if (!releaseRead.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Blocked test storage read was not released");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Test storage read interrupted", exception);
                }
            }
            byte[] content = objects.get(key);
            if (content == null) {
                throw new IllegalArgumentException("missing object: " + key);
            }
            return new java.io.ByteArrayInputStream(content);
        }

        @Override
        public void deleteObject(String key) {
            objects.remove(key);
        }

        @Override
        public void deleteObjects(List<String> keys) {
            keys.forEach(objects::remove);
        }

        @Override
        public boolean exists(String key) {
            return objects.containsKey(key);
        }

        @Override
        public ObjectMetadata getMetadata(String key) {
            byte[] content = objects.get(key);
            if (content == null) {
                throw new IllegalArgumentException("missing object: " + key);
            }
            return new ObjectMetadata(content.length, "application/octet-stream", java.time.Instant.now());
        }

        @Override
        public String generatePresignedUrl(String key, Duration expiry, String downloadFilename) {
            return "http://localhost/" + key;
        }
    }

    @BeforeEach
    void setUpNamespace() {
        if (userAccountRepository.findById(USER).isEmpty()) {
            userAccountRepository.save(new UserAccount(USER, "Author User", "author@example.com", null));
        }
        if (namespaceRepository.findBySlug(NS_SLUG).isEmpty()) {
            Namespace namespace = new Namespace(NS_SLUG, "Authoring Test", USER);
            namespace.setStatus(com.iflytek.skillhub.domain.namespace.NamespaceStatus.ACTIVE);
            Namespace saved = namespaceRepository.save(namespace);
            if (namespaceMemberRepository.findByNamespaceIdAndUserId(saved.getId(), USER).isEmpty()) {
                namespaceMemberRepository.save(new NamespaceMember(saved.getId(), USER, NamespaceRole.MEMBER));
            }
        }
    }

    @Test
    void structureLayerFailsFixablyThenSucceedsAfterRepair() {
        // ---- create with scaffold; the scaffold bytes are readable straight away
        SkillDraft draft = draftService.createDraft(NS_SLUG, USER, "draft-flow", "summarizes documents", null);
        assertThat(draft.getRevision()).isEqualTo(1);
        assertThat(draftService.listFiles(draft.getId()))
                .extracting(file -> file.getFilePath())
                .containsExactly("SKILL.md");
        assertThat(draftService.readFile(draft.getId(), USER, "SKILL.md", null).asText())
                .contains("name: draft-flow")
                .contains("description: summarizes documents");

        // ---- break the frontmatter: drop the description field
        draftService.saveFile(draft.getId(), USER, "SKILL.md",
                "---\nname: draft-flow\n---\n\n# draft-flow\n".getBytes(StandardCharsets.UTF_8),
                "text/markdown", null, null);

        // ---- validate: the structure layer must fail with a fixable finding
        ValidationRun broken = runService.startRun(draft.getId(), USER, null);
        orchestrator.submit(broken.getId());
        ValidationRun failed = awaitTerminal(broken.getId());

        assertThat(failed.getStatus()).isEqualTo(ValidationRunStatus.FAILED);
        List<ValidationFinding> findings = runService.listFindings(broken.getId());
        // the config layer (added with the execution layer) also warns that
        // validation.yaml is absent — the structure ERROR is what fails the run
        ValidationFinding finding = findings.stream()
                .filter(f -> "FRONTMATTER_FIELD_MISSING".equals(f.getRuleCode()))
                .findFirst().orElseThrow();
        assertThat(finding.getSeverity()).isEqualTo(
                com.iflytek.skillhub.domain.authoring.validation.FindingSeverity.ERROR);
        assertThat(finding.getSuggestion()).isNotNull();
        assertThat(finding.getSuggestion().safePatches()).hasSize(1);
        assertThat(runService.listEvents(broken.getId(), null))
                .extracting(event -> event.getEventType().name())
                .contains("RUN_STARTED", "PHASE_STARTED", "FINDING", "RUN_FINISHED");

        // ---- repair the file (the fix-apply endpoint arrives with the fix-loop layer)
        draftService.saveFile(draft.getId(), USER, "SKILL.md", validSkillMdBytes(),
                "text/markdown", null, null);

        // ---- re-validate: the same draft now passes the structure layer
        ValidationRun repaired = runService.startRun(draft.getId(), USER, null);
        orchestrator.submit(repaired.getId());
        ValidationRun succeeded = awaitTerminal(repaired.getId());

        assertThat(succeeded.getStatus()).isEqualTo(ValidationRunStatus.SUCCEEDED);
        assertThat(succeeded.getErrorCount()).isZero();
        // warnings (SPEC_MISSING for the absent validation.yaml) do not block
        assertThat(runService.listFindings(repaired.getId()))
                .extracting(ValidationFinding::getSeverity)
                .doesNotContain(com.iflytek.skillhub.domain.authoring.validation.FindingSeverity.ERROR);
        assertThat(draftService.getDraft(draft.getId()).getValidatedRevision())
                .isEqualTo(succeeded.getDraftRevision());
    }

    @Test
    void concurrentFileSavesConflictOnRevision() {
        SkillDraft draft = draftService.createDraft(NS_SLUG, USER, "optimistic-draft", null, null);

        // a save pinned to the current revision succeeds and bumps it
        draftService.saveFile(draft.getId(), USER, "SKILL.md", validSkillMdBytes(),
                "text/markdown", 1, null);
        assertThat(draftService.getDraft(draft.getId()).getRevision()).isEqualTo(2);

        // a stale writer (still expecting revision 1) must be rejected
        assertThrows(Exception.class, () -> draftService.saveFile(
                draft.getId(), USER, "SKILL.md", validSkillMdBytes(),
                "text/markdown", 1, null));
    }

    @Test
    void rolledBackDeletionRetainsReadableDraftFiles() {
        SkillDraft draft = draftService.createDraft(NS_SLUG, USER, "delete-rollback", null, null);
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            draftService.deleteDraft(draft.getId(), USER, null);
            transaction.setRollbackOnly();
        });
        assertThat(draftService.readFile(draft.getId(), USER, "SKILL.md", null).asText())
                .contains("name: delete-rollback");
        draftService.deleteDraft(draft.getId(), USER, null);
        assertThrows(com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException.class,
                () -> draftService.getDraft(draft.getId()));
    }

    @Test
    void simultaneousStartsCreateExactlyOneActiveRun() throws Exception {
        SkillDraft draft = draftService.createDraft(NS_SLUG, USER, "concurrent-start", null, null);
        List<Boolean> results = concurrently(6, () -> {
            try {
                runService.startRun(draft.getId(), USER, null);
                return true;
            } catch (DomainConflictException expected) {
                return false;
            }
        });
        assertThat(results).filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(runService.listRuns(draft.getId())).hasSize(1);
    }

    @Test
    void simultaneousSavesRejectStaleRevisionRatherThanLosingContent() throws Exception {
        SkillDraft draft = draftService.createDraft(NS_SLUG, USER, "concurrent-save", null, null);
        List<Boolean> results = concurrently(4, () -> {
            try {
                draftService.saveFile(draft.getId(), USER, "SKILL.md", validSkillMdBytes(),
                        "text/markdown", 1, null);
                return true;
            } catch (DomainConflictException expected) {
                return false;
            }
        });
        assertThat(results).filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(draftService.getDraft(draft.getId()).getRevision()).isEqualTo(2);
        assertThat(draftService.readFile(draft.getId(), USER, "SKILL.md", null).content())
                .isEqualTo(validSkillMdBytes());
    }

    @Test
    void snapshotRejectsChangedRevisionAndRetainsCapturedContent() {
        SkillDraft draft = draftService.createDraft(NS_SLUG, USER, "snapshot-draft", null, null);
        var snapshot = draftService.materializeSnapshot(draft.getId(), 1);
        draftService.saveFile(draft.getId(), USER, "SKILL.md", validSkillMdBytes(),
                "text/markdown", 1, null);
        assertThrows(DomainConflictException.class, () -> draftService.materializeSnapshot(draft.getId(), 1));
        draftService.deleteDraft(draft.getId(), USER, null);
        assertThat(new String(snapshot.entries().get(0).content(), StandardCharsets.UTF_8))
                .contains("name: snapshot-draft");
    }

    @Test
    void concurrentEventsRemainOrderedAndTerminalEventIsIdempotent() throws Exception {
        SkillDraft draft = draftService.createDraft(NS_SLUG, USER, "concurrent-events", null, null);
        ValidationRun run = runService.startRun(draft.getId(), USER, null);
        assertThat(runService.appendTerminalEvent(run.getId())).isNull();
        assertThrows(DomainConflictException.class, () -> runService.appendEvent(run.getId(),
                ValidationEventType.RUN_FINISHED, null, Map.of("status", "SUCCEEDED")));
        List<ValidationEvent> appended = concurrently(8, () -> runService.appendEvent(run.getId(),
                ValidationEventType.PHASE_STARTED, "STRUCTURE", Map.of()));
        assertThat(appended).extracting(ValidationEvent::getSeq)
                .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
        runService.requestCancel(run.getId(), USER, null);
        List<ValidationEvent> terminal = concurrently(6, () -> runService.appendTerminalEvent(run.getId()));
        assertThat(terminal).extracting(ValidationEvent::getId).containsOnly(terminal.get(0).getId());
        assertThat(terminal.get(0).getPayload()).containsEntry("status", "CANCELLED");
        assertThat(runService.listEvents(run.getId(), null)).hasSize(9);
        assertThrows(DomainConflictException.class, () -> runService.appendEvent(run.getId(),
                ValidationEventType.PHASE_STARTED, "STRUCTURE", Map.of()));
        assertThrows(DomainConflictException.class, () -> runService.recordFinding(run.getId(),
                new FindingDraft(ValidationLayer.STRUCTURE, "LATE", FindingSeverity.ERROR,
                        "SKILL.md", null, "late finding", null)));
        assertThat(runService.listFindings(run.getId())).isEmpty();
    }

    @Test
    void simultaneousSettlementReturnsOneWinnerAndQueueRejectionCannotFailRunningRun() throws Exception {
        SkillDraft draft = draftService.createDraft(NS_SLUG, USER, "concurrent-settle", null, null);
        ValidationRun run = runService.startRun(draft.getId(), USER, null);
        runService.claimForExecution(run.getId());
        assertThat(runService.settleIfQueued(run.getId(), 1, 0, Map.of())).isEmpty();
        assertThat(runService.getRun(run.getId()).getStatus()).isEqualTo(ValidationRunStatus.RUNNING);
        List<Boolean> results = concurrently(6, () -> runService.settleIfActive(run.getId(),
                ValidationRunStatus.TIMED_OUT, 0, 0, Map.of()).isPresent());
        assertThat(results).filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(runService.getRun(run.getId()).getStatus()).isEqualTo(ValidationRunStatus.TIMED_OUT);
    }

    @Test
    void timeoutSettlesBeforeBlockedSnapshotStorageReadCompletes() throws Exception {
        SkillDraft draft = draftService.createDraft(NS_SLUG, USER, "blocked-snapshot-timeout", null, null);
        ValidationRun run = runService.startRun(draft.getId(), USER, null);
        runService.claimForExecution(run.getId());
        InMemoryObjectStorage storage = (InMemoryObjectStorage) objectStorage;
        storage.blockedKey = draftService.listFiles(draft.getId()).get(0).getStorageKey();
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var snapshot = executor.submit(() ->
                        draftService.materializeSnapshot(draft.getId(), run.getDraftRevision()));
                assertThat(storage.readEntered.await(5, TimeUnit.SECONDS)).isTrue();
                var timeout = executor.submit(() -> runService.settleIfActive(run.getId(),
                        ValidationRunStatus.TIMED_OUT, 0, 0, Map.of("reason", "deadline")));
                assertThat(timeout.get(5, TimeUnit.SECONDS)).isPresent();
                assertThat(runService.getRun(run.getId()).getStatus()).isEqualTo(ValidationRunStatus.TIMED_OUT);
                assertThat(snapshot.isDone()).isFalse();
                assertThrows(DomainConflictException.class, () -> runService.recordFinding(run.getId(),
                        FindingDraft.error(ValidationLayer.STRUCTURE, "LATE", "late finding")));
                assertThat(runService.listFindings(run.getId())).isEmpty();
                storage.releaseRead.countDown();
                assertThat(snapshot.get(5, TimeUnit.SECONDS).entries()).hasSize(1);
                assertThrows(DomainConflictException.class, () -> runService.appendEvent(run.getId(),
                        ValidationEventType.PHASE_STARTED, "STRUCTURE", Map.of()));
            } finally {
                storage.blockedKey = null;
                storage.releaseRead.countDown();
            }
        }
    }

    @Test
    void namespaceWithOnlyDraftRequiresDraftRemovalBeforeDeletion() {
        Namespace namespace = namespaceService.createNamespace("draft-delete-team", "Draft Delete Team", null, USER);
        SkillDraft draft = draftService.createDraft(namespace.getSlug(), USER, "namespace-delete-draft", null, null);

        assertThat(namespaceService.canDelete(namespace, NamespaceRole.OWNER)).isFalse();
        DomainBadRequestException conflict = assertThrows(DomainBadRequestException.class,
                () -> namespaceService.deleteNamespace(namespace.getId(), USER));
        assertThat(conflict.messageCode()).isEqualTo("error.namespace.delete.hasDependencies");
        assertThat(namespaceRepository.findById(namespace.getId())).isPresent();
        assertThat(draftService.readFile(draft.getId(), USER, "SKILL.md", null).asText())
                .contains("name: namespace-delete-draft");

        draftService.deleteDraft(draft.getId(), USER, null);
        assertThat(namespaceService.canDelete(namespace, NamespaceRole.OWNER)).isTrue();
        namespaceService.deleteNamespace(namespace.getId(), USER);
        assertThat(namespaceRepository.findById(namespace.getId())).isEmpty();
        assertThat(namespaceMemberRepository.findByNamespaceIdAndUserId(namespace.getId(), USER)).isEmpty();
    }

    private <T> List<T> concurrently(int workers, Callable<T> action) throws Exception {
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(workers)) {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent test did not start");
                    }
                    return action.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
        }
    }

    private String validSkillMd() {
        return "---\nname: draft-flow\ndescription: summarizes documents\n---\n\n# draft-flow\n";
    }

    private byte[] validSkillMdBytes() {
        return validSkillMd().getBytes(StandardCharsets.UTF_8);
    }

    private ValidationRun awaitTerminal(Long runId) {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            ValidationRun run = runService.getRun(runId);
            if (run.getStatus().isTerminal() && runService.listEvents(runId, null).stream()
                    .anyMatch(event -> event.getEventType() == ValidationEventType.RUN_FINISHED)) {
                return run;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting for the validation run to finish");
            }
        }
        fail("validation run did not reach a terminal state in time");
        return null;
    }
}
