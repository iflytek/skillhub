package com.iflytek.skillhub.authoring;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.domain.authoring.SkillDraft;
import com.iflytek.skillhub.domain.authoring.service.RuntimeBindingService;
import com.iflytek.skillhub.domain.authoring.service.SkillDraftService;
import com.iflytek.skillhub.domain.authoring.service.ValidationRunService;
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
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runtime-binding layer of the authoring platform: three-layer validation with
 * a local-script binding (structure + config + behavior, script subprocess
 * executed inline under the test profile) and the save-time security policy
 * for bindings (cloud-metadata endpoints, envRefs allowlist, LLM surface).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestRedisConfig.class, AuthoringRuntimeFlowIntegrationTest.InMemoryStorageConfig.class})
@Testcontainers
class AuthoringRuntimeFlowIntegrationTest {

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

    private static final String USER = "author-user-2";
    private static final String NS_SLUG = "authoring-test-ns";

    @Autowired private SkillDraftService draftService;
    @Autowired private RuntimeBindingService bindingService;
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
            return new AuthoringDraftFlowIntegrationTest.InMemoryObjectStorage();
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
    void threeLayerValidationExecutesScriptTasks() {
        SkillDraft draft = draftService.createDraft(NS_SLUG, USER, "runtime-flow",
                "summarizes documents", null);
        draftService.saveFile(draft.getId(), USER, "SKILL.md", validSkillMd().getBytes(StandardCharsets.UTF_8),
                "text/markdown", null, null);
        draftService.saveFile(draft.getId(), USER, "scripts/check.sh",
                "echo 'smoke OK'\n".getBytes(StandardCharsets.UTF_8), null, null, null);
        draftService.saveFile(draft.getId(), USER, "validation.yaml",
                validationYaml().getBytes(StandardCharsets.UTF_8), null, null, null);

        // ---- bind the local-script runtime
        bindingService.saveBinding(draft.getId(), USER, "local-script",
                Map.of("interpreter", "sh"), null, null, null);

        // ---- validate: all three layers run, the script task executes inline
        ValidationRun run = runService.startRun(draft.getId(), USER, null);
        orchestrator.submit(run.getId());
        ValidationRun finished = awaitTerminal(run.getId());

        assertThat(finished.getStatus()).isEqualTo(ValidationRunStatus.SUCCEEDED);
        assertThat(finished.getErrorCount()).isZero();
        assertThat(runService.listFindings(run.getId())).isEmpty();
        assertThat(runService.listEvents(run.getId(), null))
                .extracting(event -> event.getEventType().name())
                .contains("RUN_STARTED", "TOOL_CALL", "LOG", "RUN_FINISHED");
        assertThat(runService.listEvents(run.getId(), null).stream()
                .map(event -> event.getPayload() == null ? "" : event.getPayload().toString())
                .filter(payload -> payload.contains("smoke OK"))
                .count()).isPositive();

        SkillDraft validated = draftService.getDraft(draft.getId());
        assertThat(validated.isCurrentRevisionValidated()).isTrue();
    }

    @Test
    void securityPolicyRejectsUnsafeBindingsAtSaveTime() {
        SkillDraft draft = draftService.createDraft(NS_SLUG, USER, "security-flow",
                "exercises the binding security policy", null);
        draftService.saveFile(draft.getId(), USER, "SKILL.md", validSkillMd().getBytes(StandardCharsets.UTF_8),
                "text/markdown", null, null);

        // cloud metadata endpoints are always blocked — even under the test
        // profile where loopback/RFC1918 endpoints are deliberately allowed
        assertBindingRejected(() -> bindingService.saveBinding(draft.getId(), USER, "local-script",
                Map.of("interpreter", "sh"), null,
                List.of(Map.of("name", "metadata", "transport", "http",
                        "endpoint", "http://169.254.169.254/latest/meta-data/")), null),
                "link-local");

        // envRefs outside the configured allowlist are rejected: bindings must
        // not be able to read arbitrary server environment variables
        assertBindingRejected(() -> bindingService.saveBinding(draft.getId(), USER, "local-script",
                Map.of("interpreter", "sh"), null,
                List.of(Map.of("name", "env-hog", "transport", "http",
                        "endpoint", "http://127.0.0.1:9/mcp",
                        "envRefs", List.of("HOME"))), null),
                "env-allowlist");

        // the LLM surface is guarded too — those requests carry the server API key
        assertBindingRejected(() -> bindingService.saveBinding(draft.getId(), USER, "openai-compatible",
                Map.of("endpoint", "http://169.254.169.254/v1", "model", "test-model"), null, null, null),
                "link-local");

        // the allowlisted env ref saves fine (loopback is allowed under the test profile)
        bindingService.saveBinding(draft.getId(), USER, "local-script",
                Map.of("interpreter", "sh"), null,
                List.of(Map.of("name", "env-ok", "transport", "http",
                        "endpoint", "http://127.0.0.1:9/mcp",
                        "envRefs", List.of("TEST_ALLOWED_MCP_VAR"))), null);
    }

    private String validSkillMd() {
        return "---\nname: runtime-flow\ndescription: summarizes documents\n---\n\n# runtime-flow\n";
    }

    private String validationYaml() {
        return """
                version: 1
                tasks:
                  - name: smoke
                    description: script prints the greeting
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

    private static void assertBindingRejected(Runnable save, String reasonFragment) {
        try {
            save.run();
            fail("binding save should have been rejected: " + reasonFragment);
        } catch (com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException exception) {
            assertThat(String.valueOf(exception.messageArgs()[0])).contains(reasonFragment);
        }
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
