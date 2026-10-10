package com.iflytek.skillhub.authoring;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.domain.authoring.SkillDraft;
import com.iflytek.skillhub.domain.authoring.service.SkillDraftService;
import com.iflytek.skillhub.domain.authoring.service.ValidationRunService;
import com.iflytek.skillhub.domain.authoring.validation.ValidationFinding;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRun;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRunStatus;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
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

    @Autowired private SkillDraftService draftService;
    @Autowired private ValidationRunService runService;
    @Autowired private ValidationRunOrchestrator orchestrator;
    @Autowired private NamespaceRepository namespaceRepository;
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
        assertThat(findings).hasSize(1);
        ValidationFinding finding = findings.get(0);
        assertThat(finding.getRuleCode()).isEqualTo("FRONTMATTER_FIELD_MISSING");
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
        assertThat(runService.listFindings(repaired.getId())).isEmpty();
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

    private String validSkillMd() {
        return "---\nname: draft-flow\ndescription: summarizes documents\n---\n\n# draft-flow\n";
    }

    private byte[] validSkillMdBytes() {
        return validSkillMd().getBytes(StandardCharsets.UTF_8);
    }

    private ValidationRun awaitTerminal(Long runId) {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (runService.getRun(runId).getStatus().isTerminal()) {
                return runService.getRun(runId);
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
