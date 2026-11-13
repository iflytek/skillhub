package com.iflytek.skillhub.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleCoordinate;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberSourceType;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMode;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSession;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSessionRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePublishAction;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleRelationshipChange;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundleCoordinator;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundleConfirmationAppService;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundleMemberProgressService;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundleOperationCommandService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.support.PostgresTestImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real filters, token hashing, application/domain services and database state; object storage is isolated. */
@SpringBootTest(properties = {
        "skillhub.ratelimit.enabled=false", "skillhub.suite.bundle.confirmation-enabled=true",
        // Built-in skill sync runs asynchronously after startup and writes to storage; keep it out of
        // the no-side-effect assertions here.
        "skillhub.builtin-skills.enabled=false"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@Testcontainers
class TokenCapabilityFlowIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.flyway.enabled", () -> true);
    }

    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired ApiTokenService tokens;
    @Autowired UserAccountRepository users;
    @Autowired NamespaceRepository namespaces;
    @Autowired NamespaceMemberRepository memberships;
    @Autowired SkillRepository skills;
    @Autowired SkillVersionRepository versions;
    @Autowired SkillSuiteBundleExecutionOperationRepository operations;
    @Autowired SkillSuiteBundlePreviewSessionRepository previews;
    @Autowired SkillSuiteBundleMemberResultRepository members;
    @Autowired SkillSuiteBundleMemberProgressService progress;
    @Autowired SkillSuiteBundleOperationCommandService commands;
    @Autowired SkillSuiteBundleCoordinator coordinator;
    @Autowired SkillSuiteBundleConfirmationAppService confirmation;
    @Autowired org.springframework.jdbc.core.JdbcTemplate database;
    @MockBean ObjectStorageService storage;
    @MockBean SecurityScanService scanner;
    @MockBean UserRoleBindingRepository roleBindings;
    @SpyBean com.iflytek.skillhub.domain.skill.service.SkillVisibilityService probeVisibility;
    @Autowired org.springframework.transaction.PlatformTransactionManager probeTransactions;

    private String actor;
    private Namespace namespace;

    @BeforeEach
    void fixture() {
        String suffix = UUID.randomUUID().toString();
        actor = "capability-" + suffix;
        users.save(new UserAccount(actor, "Capability test", actor + "@example.test", ""));
        namespace = namespaces.save(new Namespace("caps-" + suffix, "Capabilities", actor));
        memberships.save(new NamespaceMember(namespace.getId(), actor, NamespaceRole.OWNER));
    }

    @ParameterizedTest
    @CsvSource({"/api/v1,MEMBER", "/api/web,MEMBER", "/api/v1,OWNER", "/api/web,OWNER"})
    void candidateCanUploadSubmitAndReadButCannotConfirmManageOrDelete(String prefix, NamespaceRole role) throws Exception {
        var membership = memberships.findByNamespaceIdAndUserId(namespace.getId(), actor).orElseThrow();
        membership.setRole(role);
        memberships.save(membership);
        String token = token("skill:read", "skill:submit");
        SkillVersion uploaded = upload(prefix, token, "1.0.0");
        assertThat(uploaded.getStatus()).isEqualTo(SkillVersionStatus.UPLOADED);
        clearInvocations(storage);
        http.perform(post(route(prefix, "/confirm-publish")).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"1.0.0\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("error.apiToken.scope.missing"))
                .andExpect(jsonPath("$.errorArgs[0]").value("skill:publish-private"));
        http.perform(post(route(prefix, "/archive")).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        http.perform(delete(route(prefix, "/versions/1.0.0")).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        assertThat(versions.findById(uploaded.getId()).orElseThrow().getStatus()).isEqualTo(SkillVersionStatus.UPLOADED);
        assertThat(skills.findById(uploaded.getSkillId()).orElseThrow().getStatus().name()).isEqualTo("ACTIVE");
        verifyNoInteractions(storage);
        http.perform(post(route(prefix, "/submit-review")).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.0.0\",\"targetVisibility\":\"NAMESPACE_ONLY\"}"))
                .andExpect(status().isOk());
        http.perform(get(route(prefix, "/versions")).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        assertThat(versions.findById(uploaded.getId()).orElseThrow().getStatus())
                .isEqualTo(SkillVersionStatus.PENDING_REVIEW);
    }

    @Test
    void visibilityChangeDenialLeavesVersionsVisibilityAndLatestPointerUnchanged() throws Exception {
        String token = token("skill:read", "skill:submit");
        SkillVersion uploaded = upload("/api/v1", token, "1.0.0");
        Skill skill = skills.findById(uploaded.getSkillId()).orElseThrow();
        skill.setVisibility(SkillVisibility.NAMESPACE_ONLY);
        skills.save(skill);
        int before = versions.findBySkillId(skill.getId()).size();
        Long latest = skill.getLatestVersionId();
        clearInvocations(storage);
        var response = http.perform(multipart("/api/v1/skills/" + namespace.getSlug() + "/publish")
                        .file(archive("2.0.0")).param("visibility", "PRIVATE").param("confirmWarnings", "true")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
        assertThat(response).contains("skill:manage");
        Skill unchanged = skills.findById(skill.getId()).orElseThrow();
        assertThat(unchanged.getVisibility()).isEqualTo(SkillVisibility.NAMESPACE_ONLY);
        assertThat(unchanged.getLatestVersionId()).isEqualTo(latest);
        assertThat(versions.findBySkillId(skill.getId())).hasSize(before);
        verifyNoInteractions(storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"skill:submit", "skill:publish"})
    void superAdminTokenCannotAutoPublishButLegacyCanExplicitlyConfirm(String writeScope) throws Exception {
        superAdmin();
        String token = token("skill:read", writeScope);
        SkillVersion uploaded = upload("/api/v1", token, "1.0.0");
        assertThat(uploaded.getStatus()).isEqualTo(SkillVersionStatus.UPLOADED);
        var request = http.perform(post(route("/api/v1", "/confirm-publish"))
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"1.0.0\"}"));
        request.andExpect(writeScope.equals("skill:publish") ? status().isOk() : status().isForbidden());
        assertThat(versions.findById(uploaded.getId()).orElseThrow().getStatus()).isEqualTo(
                writeScope.equals("skill:publish") ? SkillVersionStatus.PUBLISHED : SkillVersionStatus.UPLOADED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1", "/api/web"})
    void ordinaryLegacyTokenConfirmsAndPrivateRereleaseRemainsUploaded(String prefix) throws Exception {
        String legacy = token("skill:read", "skill:publish");
        SkillVersion uploaded = upload(prefix, legacy, "1.0.0");
        http.perform(post(route(prefix, "/confirm-publish")).header("Authorization", bearer(legacy))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"1.0.0\"}"))
                .andExpect(status().isOk());
        assertThat(versions.findById(uploaded.getId()).orElseThrow().getStatus()).isEqualTo(SkillVersionStatus.PUBLISHED);
        when(storage.getObject(anyString())).thenReturn(new ByteArrayInputStream(markdown("1.0.0")));
        String candidate = token("skill:read", "skill:submit");
        http.perform(post(route(prefix, "/versions/1.0.0/rerelease"))
                        .header("Authorization", bearer(candidate)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetVersion\":\"2.0.0\",\"confirmWarnings\":true}"))
                .andExpect(status().isOk());
        assertThat(versions.findBySkillIdAndVersion(uploaded.getSkillId(), "2.0.0").orElseThrow().getStatus())
                .isEqualTo(SkillVersionStatus.UPLOADED);
    }

    @ParameterizedTest
    @CsvSource({"/api/v1,SUBMIT", "/api/web,SUBMIT", "/api/v1,MANAGE", "/api/web,MANAGE",
            "/api/v1,SESSION", "/api/web,SESSION"})
    void rereleaseMustNotNarrowVisibilityWithoutManageAfterConcurrentExpansion(String prefix, String authority) throws Exception {
        String candidate = authority.equals("MANAGE")
                ? token("skill:read", "skill:submit", "skill:manage") : token("skill:read", "skill:submit");
        SkillVersion source = upload(prefix, candidate, "1.0.0");
        http.perform(post(route(prefix, "/confirm-publish"))
                        .header("Authorization", bearer(token("skill:publish-private")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.0.0\"}"))
                .andExpect(status().isOk());
        when(storage.getObject(anyString())).thenAnswer(invocation ->
                new ByteArrayInputStream(markdown("1.0.0")));
        var fired = new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (fired.compareAndSet(false, true)) {
                var concurrent = new org.springframework.transaction.support.TransactionTemplate(probeTransactions);
                concurrent.setPropagationBehavior(
                        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                concurrent.executeWithoutResult(transaction -> database.update(
                        "UPDATE skill SET visibility = 'NAMESPACE_ONLY' WHERE id = ?", source.getSkillId()));
            }
            return invocation.callRealMethod();
        }).when(probeVisibility).lockedSkill(source.getSkillId());
        when(scanner.isEnabled()).thenReturn(true);
        clearInvocations(storage, scanner);
        Long latest = skills.findById(source.getSkillId()).orElseThrow().getLatestVersionId();
        var request = post(route(prefix, "/versions/1.0.0/rerelease"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetVersion\":\"2.0.0\",\"confirmWarnings\":true}");
        if (authority.equals("SESSION")) {
            var principal = new PlatformPrincipal(actor, "Owner", actor + "@example.test", "", "local", Set.of());
            request.with(authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()))).with(csrf());
        } else {
            request.header("Authorization", bearer(candidate));
        }
        var response = http.perform(request).andReturn().getResponse();
        System.out.println("PROBE rerelease route=" + prefix + "; HTTP=" + response.getStatus()
                + "; concurrent expansion committed=" + fired.get()
                + "; final visibility=" + skills.findById(source.getSkillId()).orElseThrow().getVisibility()
                + "; target version exists="
                + versions.findBySkillIdAndVersion(source.getSkillId(), "2.0.0").isPresent());
        assertThat(fired).isTrue();
        assertThat(skills.findById(source.getSkillId()).orElseThrow().getVisibility())
                .isEqualTo(SkillVisibility.NAMESPACE_ONLY);
        assertThat(skills.findById(source.getSkillId()).orElseThrow().getLatestVersionId()).isEqualTo(latest);
        if (authority.equals("SUBMIT")) {
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(json.readTree(response.getContentAsString()).path("errorCode").asText())
                    .isEqualTo("error.apiToken.scope.missing");
            assertThat(json.readTree(response.getContentAsString()).path("errorArgs").get(0).asText())
                    .isEqualTo("skill:manage");
            assertThat(versions.findBySkillIdAndVersion(source.getSkillId(), "2.0.0")).isEmpty();
            assertThat(org.mockito.Mockito.mockingDetails(storage).getInvocations())
                    .noneMatch(invocation -> invocation.getMethod().getName().startsWith("put")
                            || invocation.getMethod().getName().startsWith("delete"));
            verifyNoInteractions(scanner);
        } else {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(versions.findBySkillIdAndVersion(source.getSkillId(), "2.0.0").orElseThrow())
                    .satisfies(version -> {
                        assertThat(version.getRequestedVisibility()).isEqualTo(SkillVisibility.NAMESPACE_ONLY);
                        assertThat(version.getStatus()).isEqualTo(SkillVersionStatus.PENDING_REVIEW);
                    });
        }
    }

    @Test
    void superAdminSessionKeepsTheExistingPrivateAutoPublishBehavior() throws Exception {
        superAdmin();
        var principal = new PlatformPrincipal(actor, "Admin", actor + "@example.test", "", "local", Set.of("SUPER_ADMIN"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        http.perform(multipart("/api/v1/skills/" + namespace.getSlug() + "/publish").file(archive("1.0.0"))
                        .param("visibility", "PRIVATE").param("confirmWarnings", "true")
                        .with(authentication(auth)).with(csrf()))
                .andExpect(status().isOk());
        Skill skill = skills.findByNamespaceIdAndSlugAndOwnerId(namespace.getId(), "candidate", actor).orElseThrow();
        assertThat(versions.findBySkillIdAndVersion(skill.getId(), "1.0.0").orElseThrow().getStatus())
                .isEqualTo(SkillVersionStatus.PUBLISHED);
    }

    @Test
    void newTokenBundleWithoutSnapshotCannotCreateAnyOperationOrMemberRows() {
        Long operationCount = database.queryForObject("SELECT COUNT(*) FROM skill_suite_bundle_operation", Long.class);
        Long memberCount = database.queryForObject("SELECT COUNT(*) FROM skill_suite_bundle_member_result", Long.class);
        assertThatThrownBy(() -> confirmation.confirm("missing-preview", "new-request", "digest", actor,
                Map.of(), Set.of("SUPER_ADMIN"), true, null))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class)
                .extracting("messageCode").isEqualTo("error.suite.bundle.authorization.snapshotRequired");
        assertThat(database.queryForObject("SELECT COUNT(*) FROM skill_suite_bundle_operation", Long.class))
                .isEqualTo(operationCount);
        assertThat(database.queryForObject("SELECT COUNT(*) FROM skill_suite_bundle_member_result", Long.class))
                .isEqualTo(memberCount);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "CANDIDATE", "LEGACY", "SESSION"})
    void bundleRecoveryUsesOnlyPersistedAuthorityAndPrivilegedRetryCannotRepairIt(String source) throws Exception {
        SkillVersion uploaded = upload("/api/v1", token("skill:read", "skill:submit"), "1.0.0");
        String previewId = UUID.randomUUID().toString();
        String operationId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        previews.save(new SkillSuiteBundlePreviewSession(previewId, actor, SkillSuiteBundleMode.CREATE,
                namespace.getId(), "target", null, null, "1.0.0", "archive", "a".repeat(64),
                Map.of(), Map.of(), "digest", now.plusSeconds(600), now));
        var operation = new SkillSuiteBundleExecutionOperation(operationId, previewId, operationId, actor,
                SkillSuiteBundleMode.CREATE, namespace.getId(), "target", null, null, "1.0.0",
                "archive", "a".repeat(64), Map.of(), "digest", now);
        if (source.equals("SESSION")) {
            operation.captureAuthorization(false, null);
        } else if (!source.equals("UNKNOWN")) {
            operation.captureAuthorization(true, source.equals("LEGACY")
                    ? Set.of("skill:submit", "skill:manage", "skill:publish-private", "suite:legacy-write")
                    : Set.of("skill:read", "skill:submit"));
        }
        operations.save(operation);
        var member = new SkillSuiteBundleMemberResult(operationId, 0,
                new SkillSuiteBundleCoordinate(namespace.getSlug(), "candidate"),
                SkillSuiteBundleMemberSourceType.PACKAGE, "skills/candidate", SkillVisibility.PRIVATE, "1.0.0",
                SkillSuiteBundleRelationshipChange.ADDED, SkillSuiteBundlePublishAction.CREATE_SKILL,
                "fingerprint", null, null, List.of(), List.of(), now);
        member.start(now);
        member.bindVersion(uploaded.getSkillId(), uploaded.getId(), now);
        member.markWaiting(now);
        members.saveAll(List.of(member));
        boolean blocked = source.equals("UNKNOWN") || source.equals("CANDIDATE");
        assertThat(progress.reconcile(operationId)).isEqualTo(blocked
                ? SkillSuiteBundleMemberProgressService.ProgressOutcome.TERMINAL
                : SkillSuiteBundleMemberProgressService.ProgressOutcome.READY_FOR_DRAFT);
        if (blocked) {
            Set<String> snapshot = operations.findById(operationId).orElseThrow().getTokenCapabilities();
            commands.retry(operationId, "more-privileged-caller", Map.of(), Set.of("SUPER_ADMIN"));
            coordinator.advance(operationId);
            var reloaded = operations.findById(operationId).orElseThrow();
            assertThat(reloaded.getStatus().name()).isEqualTo("BLOCKED_RETRYABLE");
            assertThat(reloaded.getTokenCapabilities()).isEqualTo(snapshot);
            assertThat(reloaded.getFailureCode()).isEqualTo(source.equals("UNKNOWN")
                    ? "AUTHORIZATION_PROVENANCE_UNKNOWN" : "TOKEN_CAPABILITY_MISSING");
            assertThat(database.queryForObject("SELECT COUNT(*) FROM skill_suite WHERE namespace_id = ?",
                    Long.class, namespace.getId())).isZero();
        }
        assertThat(versions.findById(uploaded.getId()).orElseThrow().getStatus())
                .isEqualTo(blocked ? SkillVersionStatus.UPLOADED : SkillVersionStatus.PUBLISHED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "CANDIDATE"})
    void unknownBundleAuthorizationMustExplainRecoveryInTheRequestedLocale(String source) throws Exception {
        bundleRecoveryUsesOnlyPersistedAuthorityAndPrivilegedRetryCannotRepairIt(source);
        String operationId = database.queryForObject(
                "SELECT operation_id FROM skill_suite_bundle_operation WHERE actor_id = ?", String.class, actor);
        String expectedKey = source.equals("UNKNOWN") ? "error.suite.bundle.authorization.provenanceUnknown"
                : "error.suite.bundle.authorization.capabilityMissing: skill:publish-private";
        assertThat(members.findByOperationIdOrderByPosition(operationId).getFirst().getErrors()).containsExactly(expectedKey);
        var localized = new org.assertj.core.api.SoftAssertions();
        for (String language : List.of("zh", "ru")) {
            String body = http.perform(get("/api/v1/suite-bundles/operations/" + operationId)
                            .header("Authorization", bearer(token("skill:publish")))
                            .header("Accept-Language", language))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            String error = json.readTree(body).path("data").path("members").get(0).path("errors").get(0).asText();
            System.out.println("PROBE Bundle source=" + source + "; locale=" + language + "; visible member error=" + error);
            localized.assertThat(error).as(language).doesNotContain(
                    "Authorization provenance unknown; create a new explicitly authorized operation",
                    "The original", "error.suite.bundle.authorization.");
            localized.assertThat(error).as(language).contains(language.equals("zh") ? "取消" : "Отмените");
            if (source.equals("CANDIDATE")) {
                localized.assertThat(error).contains("skill:publish-private");
            }
        }
        localized.assertAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRIVATE", "NAMESPACE_ONLY"})
    void submitAndManageAllowVisibilityChangesWithoutBypassingReview(String target) throws Exception {
        SkillVersion uploaded = upload("/api/v1", token("skill:read", "skill:submit"), "1.0.0");
        Skill skill = skills.findById(uploaded.getSkillId()).orElseThrow();
        skill.setVisibility(target.equals("PRIVATE") ? SkillVisibility.NAMESPACE_ONLY : SkillVisibility.PRIVATE);
        skills.save(skill);
        when(scanner.isEnabled()).thenReturn(true);
        http.perform(multipart("/api/v1/skills/" + namespace.getSlug() + "/publish").file(archive("2.0.0"))
                        .param("visibility", target).param("confirmWarnings", "true")
                        .header("Authorization", bearer(token("skill:read", "skill:submit", "skill:manage"))))
                .andExpect(status().isOk());
        SkillVisibility current = skills.findById(skill.getId()).orElseThrow().getVisibility();
        assertThat(current).isEqualTo(SkillVisibility.PRIVATE);
        assertThat(versions.findBySkillIdAndVersion(skill.getId(), "2.0.0").orElseThrow().getStatus())
                .isEqualTo(target.equals("PRIVATE") ? SkillVersionStatus.UPLOADED : SkillVersionStatus.PENDING_REVIEW);
    }

    @Test
    void issuanceDenialsPreserveSameNameTokenAndCannotBypassTheCeilingWithDefaultsOrExpiry() throws Exception {
        var original = tokens.createToken(actor, "rotation-target", "[\"skill:read\"]");
        String expiry = Instant.now().plusSeconds(3600).toString();
        String caller = tokens.createToken(actor, "limited-manager", "[\"token:manage\",\"skill:read\"]", expiry).rawToken();
        for (String body : List.of(
                "{\"name\":\"rotation-target\",\"scopes\":[\"skill:delete\"]}",
                "{\"name\":\"rotation-target\"}",
                "{\"name\":\"rotation-target\",\"scopes\":[\"skill:read\"]}")) {
            http.perform(post("/api/v1/tokens").header("Authorization", bearer(caller))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("error.apiToken.scope.escalation"));
            assertThat(tokens.validateToken(original.rawToken())).isPresent();
        }
        http.perform(post("/api/v1/tokens").header("Authorization", bearer(caller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"rotation-target\",\"scopes\":[\"no-such:scope\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("error.apiToken.scope.unknown"));
        assertThat(tokens.validateToken(original.rawToken())).isPresent();
        http.perform(put("/api/v1/tokens/" + original.entity().getId() + "/expiration")
                        .header("Authorization", bearer(caller)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresAt\":\"" + Instant.now().plusSeconds(7200) + "\"}"))
                .andExpect(status().isForbidden());
        assertThat(tokens.validateToken(original.rawToken()).orElseThrow().getExpiresAt()).isNull();
        http.perform(post("/api/v1/tokens").header("Authorization", bearer(caller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"allowed-child\",\"scopes\":[\"skill:read\"],\"expiresAt\":\"" + expiry + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.capabilities[0]").value("skill:read"))
                .andExpect(jsonPath("$.data.deprecatedDefault").value(false));
    }

    @Test
    void readTokenCanQueryEmptyNamespaceRolesAndExpandedLegacyWhoamiWithoutTokenManagement() throws Exception {
        var membership = memberships.findByNamespaceIdAndUserId(namespace.getId(), actor).orElseThrow();
        membership.setRole(NamespaceRole.ADMIN);
        memberships.save(membership);
        String legacy = token("skill:read", "skill:publish");
        http.perform(get("/api/cli/v1/auth/whoami").header("Authorization", bearer(legacy)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.legacyCompat").value(true))
                .andExpect(jsonPath("$.data.capabilities").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "skill:read", "skill:submit", "skill:manage", "skill:publish-private", "suite:legacy-write")));
        for (String prefix : List.of("/api/v1", "/api/web")) {
            http.perform(get(prefix + "/me/namespaces").header("Authorization", bearer(legacy)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].slug").value(namespace.getSlug()))
                    .andExpect(jsonPath("$.data[0].currentUserRole").value("ADMIN"));
            http.perform(get(prefix + "/me/namespaces/page").header("Authorization", bearer(legacy)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].currentUserRole").value("ADMIN"));
        }
    }

    private SkillVersion upload(String prefix, String token, String version) throws Exception {
        String response = http.perform(multipart(prefix + "/skills/" + namespace.getSlug() + "/publish")
                        .file(archive(version)).param("visibility", "PRIVATE").param("confirmWarnings", "true")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long skillId = json.readTree(response).path("data").path("skillId").asLong();
        return versions.findBySkillIdAndVersion(skillId, version).orElseThrow();
    }

    private String route(String prefix, String suffix) {
        return prefix + "/skills/" + namespace.getSlug() + "/candidate" + suffix;
    }

    private String token(String... scopes) throws Exception {
        return tokens.createToken(actor, UUID.randomUUID().toString(), json.writeValueAsString(scopes)).rawToken();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void superAdmin() {
        Role role = mock(Role.class);
        when(role.getCode()).thenReturn("SUPER_ADMIN");
        UserRoleBinding binding = mock(UserRoleBinding.class);
        when(binding.getRole()).thenReturn(role);
        when(roleBindings.findByUserId(actor)).thenReturn(List.of(binding));
    }

    private byte[] markdown(String version) {
        return ("---\nname: candidate\ndescription: Candidate submission test\nversion: " + version
                + "\n---\n# Candidate\nTest instructions.\n").getBytes(StandardCharsets.UTF_8);
    }

    private MockMultipartFile archive(String version) throws Exception {
        try (var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("SKILL.md"));
            zip.write(markdown(version));
            zip.closeEntry();
            zip.finish();
            return new MockMultipartFile("file", "candidate.zip", "application/zip", output.toByteArray());
        }
    }
}
