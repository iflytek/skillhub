package com.iflytek.skillhub.authoring;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.domain.authoring.SkillDraft;
import com.iflytek.skillhub.domain.authoring.service.DraftSubmitService;
import com.iflytek.skillhub.domain.authoring.service.FindingFixService;
import com.iflytek.skillhub.domain.authoring.service.RuntimeBindingService;
import com.iflytek.skillhub.domain.authoring.service.SkillDraftService;
import com.iflytek.skillhub.domain.authoring.service.ValidationRunService;
import com.iflytek.skillhub.domain.authoring.validation.FindingSeverity;
import com.iflytek.skillhub.domain.authoring.validation.ValidationFinding;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRun;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRunStatus;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * End-to-end authoring flow against the real service stack (Testcontainers
 * PostgreSQL, in-memory object storage, mocked publish pipeline): create draft →
 * edit files → bind runtime → validate (structure + config + behavior) → fix
 * findings → re-validate → submit. Runs on real PostgreSQL because the JSONB
 * columns must round-trip exactly as they do in production.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestRedisConfig.class, AuthoringFlowIntegrationTest.InMemoryStorageConfig.class})
@Testcontainers
class AuthoringFlowIntegrationTest {

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
    @Autowired private RuntimeBindingService bindingService;
    @Autowired private ValidationRunService runService;
    @Autowired private DraftSubmitService submitService;
    @Autowired private FindingFixService fixService;
    @Autowired private ValidationRunOrchestrator orchestrator;
    @Autowired private NamespaceRepository namespaceRepository;
    @Autowired private NamespaceMemberRepository namespaceMemberRepository;
    @Autowired private UserAccountRepository userAccountRepository;

    @MockBean private SkillPublishService publishService;

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
    void validatedDraftRunsAllLayersAndSubmits() {
        // ---- create with scaffold
        SkillDraft draft = draftService.createDraft(NS_SLUG, USER, "demo-flow", "summarizes documents", null);
        assertThat(draft.getRevision()).isEqualTo(1);
        assertThat(draftService.listFiles(draft.getId()))
                .extracting(file -> file.getFilePath())
                .containsExactly("SKILL.md");
        // the scaffold bytes must be readable straight away, before any manual
        // save — creation persists content, not just the metadata row
        assertThat(draftService.readFile(draft.getId(), USER, "SKILL.md", null).asText())
                .contains("name: demo-flow")
                .contains("description: summarizes documents");

        // ---- edit files: overwrite SKILL.md, add script + validation.yaml
        draftService.saveFile(draft.getId(), USER, "SKILL.md", validSkillMd(), "text/markdown", null, null);
        draftService.saveFile(draft.getId(), USER, "scripts/check.sh",
                "echo 'smoke OK'\n".getBytes(StandardCharsets.UTF_8), null, null, null);
        draftService.saveFile(draft.getId(), USER, "validation.yaml",
                validationYaml().getBytes(StandardCharsets.UTF_8), null, null, null);
        assertThat(draftService.getDraft(draft.getId()).getRevision()).isEqualTo(4);

        // ---- bind runtime: local script execution
        bindingService.saveBinding(draft.getId(), USER, "local-script",
                Map.of("interpreter", "sh"), null, null, null);

        // ---- validate: all three layers
        ValidationRun run = runService.startRun(draft.getId(), USER, null);
        orchestrator.submit(run.getId());
        ValidationRun finished = awaitTerminal(run.getId());

        assertThat(finished.getStatus()).isEqualTo(ValidationRunStatus.SUCCEEDED);
        assertThat(finished.getErrorCount()).isZero();
        assertThat(runService.listFindings(run.getId())).isEmpty();
        assertThat(runService.listEvents(run.getId(), null))
                .extracting(event -> event.getEventType().name())
                .contains("RUN_STARTED", "PHASE_STARTED", "TOOL_CALL", "LOG", "RUN_FINISHED");

        // ---- draft is validated for this revision
        SkillDraft validated = draftService.getDraft(draft.getId());
        assertThat(validated.isCurrentRevisionValidated()).isTrue();
        assertThat(validated.getValidatedRevision()).isEqualTo(validated.getRevision());

        // ---- submit into the (mocked) publish pipeline
        SkillVersion publishedVersion = new SkillVersion(99L, "1.0.0", USER);
        when(publishService.publishFromEntries(eq(NS_SLUG), any(), eq(USER),
                eq(SkillVisibility.PRIVATE), any(), anyBoolean()))
                .thenReturn(new SkillPublishService.PublishResult(99L, "demo-flow", publishedVersion));

        DraftSubmitService.SubmitOutcome outcome = submitService.submit(
                draft.getId(), USER, SkillVisibility.PRIVATE, null);

        assertThat(outcome.skillId()).isEqualTo(99L);
        assertThat(outcome.version()).isEqualTo("1.0.0");
        assertThat(draftService.getDraft(draft.getId()).getSubmittedSkillId()).isEqualTo(99L);
    }

    @Test
    void brokenFrontmatterProducesFixableFindingAndRevalidationLoop() {
        SkillDraft draft = draftService.createDraft(NS_SLUG, USER, "broken-flow", null, null);
        // SKILL.md without the required description field
        draftService.saveFile(draft.getId(), USER, "SKILL.md",
                "---\nname: broken-flow\n---\n\n# Broken\n".getBytes(StandardCharsets.UTF_8),
                "text/markdown", null, null);

        ValidationRun run = runService.startRun(draft.getId(), USER, null);
        orchestrator.submit(run.getId());
        awaitTerminal(run.getId());

        List<ValidationFinding> findings = runService.listFindings(run.getId());
        assertThat(findings).isNotEmpty();
        Optional<ValidationFinding> missingField = findings.stream()
                .filter(finding -> "FRONTMATTER_FIELD_MISSING".equals(finding.getRuleCode()))
                .findFirst();
        assertThat(missingField).isPresent();
        assertThat(missingField.get().getSeverity()).isEqualTo(FindingSeverity.ERROR);
        assertThat(missingField.get().hasSuggestion()).isTrue();
        assertThat(runService.getRun(run.getId()).getStatus()).isEqualTo(ValidationRunStatus.FAILED);

        // ---- apply the suggested fix
        int revisionBefore = draftService.getDraft(draft.getId()).getRevision();
        FindingFixService.FixOutcome outcome = fixService.applyFix(
                missingField.get().getId(), USER, null);
        assertThat(outcome.finding().getStatus().name()).isEqualTo("APPLIED");
        assertThat(outcome.draft().getRevision()).isGreaterThan(revisionBefore);

        // ---- validation verdict is invalidated by the new revision
        SkillDraft fixed = draftService.getDraft(draft.getId());
        assertThat(fixed.isCurrentRevisionValidated()).isFalse();

        // ---- re-validate: now the description field exists and the run passes
        ValidationRun second = runService.startRun(draft.getId(), USER, null);
        orchestrator.submit(second.getId());
        ValidationRun finishedSecond = awaitTerminal(second.getId());

        assertThat(finishedSecond.getStatus()).isEqualTo(ValidationRunStatus.SUCCEEDED);
        assertThat(draftService.getDraft(draft.getId()).isCurrentRevisionValidated()).isTrue();
    }

    @Test
    void deadMcpServerFailsTheRunWithoutValidationYaml() {
        // no validation.yaml on purpose: the config layer must still check the
        // runtime binding — a declared MCP server that cannot be reached is a
        // config error even when the behavior layer has no tasks to run
        SkillDraft draft = draftService.createDraft(NS_SLUG, USER, "mcp-probe-flow",
                "declares a dead MCP server", null);
        draftService.saveFile(draft.getId(), USER, "SKILL.md", validSkillMd(), "text/markdown", null, null);

        bindingService.saveBinding(draft.getId(), USER, "local-script",
                Map.of("interpreter", "sh"), null,
                List.of(Map.of("name", "dead-server", "transport", "http",
                        "endpoint", "http://127.0.0.1:9/mcp")), null);

        ValidationRun run = runService.startRun(draft.getId(), USER, null);
        orchestrator.submit(run.getId());
        ValidationRun finished = awaitTerminal(run.getId());

        assertThat(finished.getStatus()).isEqualTo(ValidationRunStatus.FAILED);
        assertThat(runService.listFindings(run.getId()))
                .extracting(ValidationFinding::getRuleCode)
                .contains("SPEC_MISSING", "MCP_CONNECT_FAILED");
    }

    // ---------------------------------------------------------------- helpers

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
        fail("validation run " + runId + " did not reach a terminal state in time");
        return null;
    }

    private static byte[] validSkillMd() {
        return """
                ---
                name: demo-flow
                description: summarizes documents for the demo
                ---

                # Demo flow

                ## Overview

                summarizes documents for the demo
                """.getBytes(StandardCharsets.UTF_8);
    }

    private static String validationYaml() {
        return """
                version: 1
                tasks:
                  - name: smoke
                    description: script runs and prints OK
                    type: script
                    script: scripts/check.sh
                    args: []
                    timeoutMs: 10000
                    assertions:
                      - type: exit_code
                        equals: 0
                      - type: stdout_contains
                        value: smoke OK
                """;
    }
}
