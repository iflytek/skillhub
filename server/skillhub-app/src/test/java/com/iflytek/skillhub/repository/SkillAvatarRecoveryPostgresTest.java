package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillAvatarCandidate;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.dto.AvatarReviewSettingsResponse;
import com.iflytek.skillhub.infra.jpa.NamespaceJpaRepository;
import com.iflytek.skillhub.infra.jpa.SkillAvatarCandidateJpaRepository;
import com.iflytek.skillhub.infra.jpa.SkillJpaRepository;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.AvatarModerationGateway;
import com.iflytek.skillhub.service.AvatarModerationProvider;
import com.iflytek.skillhub.service.AvatarReviewSettingsService;
import com.iflytek.skillhub.service.SkillAvatarAppService;
import com.iflytek.skillhub.service.SkillAvatarImageProcessor;
import com.iflytek.skillhub.service.SkillAvatarUrls;
import com.iflytek.skillhub.listener.SkillAvatarScopeReviewListener;
import com.iflytek.skillhub.task.SkillAvatarMachineRecoveryTask;
import com.iflytek.skillhub.task.SkillAvatarScopeReviewRecoveryTask;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Real PostgreSQL recovery transactions with controlled policy, storage and external provider. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Import({com.iflytek.skillhub.auth.rbac.RbacService.class, com.iflytek.skillhub.domain.skill.SkillOwnerAccessPolicy.class, SkillAvatarRecoveryPostgresTest.ReviewConfiguration.class, com.iflytek.skillhub.domain.review.ReviewService.class, com.iflytek.skillhub.domain.review.ReviewPermissionChecker.class, SkillAvatarAppService.class, SkillAvatarMachineRecoveryTask.class,
        SkillAvatarScopeReviewListener.class, SkillAvatarScopeReviewRecoveryTask.class,
        com.iflytek.skillhub.task.SkillAvatarCleanupTask.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SkillAvatarRecoveryPostgresTest {
    @Container private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

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

    @Autowired private SkillJpaRepository skills;
    @Autowired private NamespaceJpaRepository namespaces;
    @Autowired private com.iflytek.skillhub.infra.jpa.NamespaceMemberJpaRepository members;
    @Autowired private com.iflytek.skillhub.infra.jpa.SkillVersionJpaRepository versions;
    @Autowired private SkillAvatarCandidateJpaRepository candidates;
    @Autowired private SkillAvatarAppService avatars;
    @Autowired private SkillAvatarMachineRecoveryTask machineRecovery;
    @Autowired private SkillAvatarScopeReviewRecoveryTask scopeRecovery;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.iflytek.skillhub.task.SkillAvatarCleanupTask cleanup;
    @Autowired private com.iflytek.skillhub.infra.jpa.UserAccountJpaRepository users;
    @MockBean private com.iflytek.skillhub.domain.skill.service.SkillVisibilityService visibilityChanges;
    @MockBean private ObjectStorageService storage;
    @MockBean private SkillAvatarImageProcessor imageProcessor;
    @MockBean private AvatarReviewSettingsService settings;
    @MockBean private AvatarModerationGateway moderation;
    @MockBean private VisibilityChecker visibility;
    @MockBean private SkillQueryService skillQueries;
    @MockBean private AuditLogService audit;
    @MockBean private RequestIdAccessor requestIds;
    @MockBean private com.iflytek.skillhub.domain.skill.service.SkillGovernanceService governance;
    @MockBean private com.iflytek.skillhub.domain.governance.GovernanceNotificationService notifications;
    @Autowired private com.iflytek.skillhub.domain.review.ReviewService reviews;
    @Autowired private com.iflytek.skillhub.infra.jpa.ReviewTaskJpaRepository reviewRows;

    @org.springframework.boot.test.context.TestConfiguration
    static class ReviewConfiguration {
        @org.springframework.context.annotation.Bean java.time.Clock clock() { return java.time.Clock.systemUTC(); }
        @org.springframework.context.annotation.Bean com.fasterxml.jackson.databind.ObjectMapper mapper() { return new com.fasterxml.jackson.databind.ObjectMapper(); }
    }

    private com.iflytek.skillhub.dto.SkillAvatarStatusResponse unifiedDecision(String ns, String slug, long candidateId,
            boolean approved, String reason, String actor, java.util.Map<Long, com.iflytek.skillhub.domain.namespace.NamespaceRole> roles,
            java.util.Set<String> platformRoles, AuditRequestContext context) {
        var tasks = reviewRows.findByAvatarCandidateIdAndStatus(candidateId, com.iflytek.skillhub.domain.review.ReviewTaskStatus.PENDING);
        if (tasks.isEmpty()) { return avatars.decide(ns, slug, candidateId, approved, reason, actor, roles, platformRoles, context); }
        if (approved) { reviews.approveReview(tasks.getFirst().getId(), actor, reason, roles, platformRoles); }
        else { reviews.rejectReview(tasks.getFirst().getId(), actor, reason, roles, platformRoles); }
        return avatars.submittedStatus(ns, slug, "owner", candidateId);
    }


    @BeforeEach
    void providerAndStorage() {
        if (((com.iflytek.skillhub.domain.user.UserAccountRepository) users).findById("owner").isEmpty()) {
            users.saveAndFlush(new com.iflytek.skillhub.domain.user.UserAccount("owner", "owner", null, null));
        }
        byte[] image = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(storage.getObject(anyString())).thenAnswer(ignored -> new ByteArrayInputStream(image));
        when(moderation.requireConfiguredProvider()).thenReturn("xfyun");
        org.mockito.Mockito.lenient().when(moderation.context(anyString())).thenReturn(
                new AvatarModerationProvider.Context("fixture-image", "test-v1", "fixture-policy-v1", null));
        when(moderation.moderate(eq("xfyun"), any(byte[].class))).thenReturn(AvatarModerationProvider.Verdict.PASS);
        when(settings.current()).thenReturn(new AvatarReviewSettingsResponse(true, false, 1, null));
    }

    @Test
    void ordinaryCommittedPendingUploadIsRecoveredByScheduler() {
        Long skillId = skill(false);
        byte[] image = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(imageProcessor.normalize(any())).thenAnswer(call -> call.getArgument(0));
        Long candidateId = avatars.submit(namespaceSlug(skillId), "skill", "owner", image,
                new AuditRequestContext(null, null)).candidateId();
        machineRecovery.recover();
        assertThat(candidates.findById(candidateId).orElseThrow().getMachineStatus())
                .isEqualTo(SkillAvatarCandidate.MachineStatus.PENDING);
        assertThat(readSkill(skillId).getActiveAvatarId()).isNull();
        setTime(candidateId, "created_at", Instant.now().minusSeconds(660));
        Instant createdAt = candidates.findById(candidateId).orElseThrow().getCreatedAt();
        assertThat(createdAt).isBefore(Instant.now().minusSeconds(600));
        assertThat(abandoned(createdAt.minusSeconds(1), Instant.now())).extracting(SkillAvatarCandidate::getId).doesNotContain(candidateId);
        assertThat(abandoned(createdAt, Instant.now())).extracting(SkillAvatarCandidate::getId).contains(candidateId);
        machineRecovery.recover();
        assertThat(candidates.findById(candidateId).orElseThrow().getMachineStatus())
                .isEqualTo(SkillAvatarCandidate.MachineStatus.PASSED);
        assertThat(readSkill(skillId).getActiveAvatarId()).isEqualTo(candidateId);
        assertThat(readSkill(skillId).getPendingAvatarId()).isNull();
    }

    @Test
    void expiredLeaseIsReclaimedAndLateFirstAttemptCannotOverwriteSecond() throws Exception {
        Long skillId = skill(false);
        Long candidateId = pending(skillId);
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(moderation.moderate(eq("xfyun"), any(byte[].class))).thenAnswer(ignored -> {
            if (calls.incrementAndGet() == 1) {
                providerEntered.countDown();
                assertThat(releaseFirst.await(20, TimeUnit.SECONDS)).isTrue();
                return AvatarModerationProvider.Verdict.BLOCK;
            }
            return AvatarModerationProvider.Verdict.PASS;
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> avatars.processMachine(candidateId, new AuditRequestContext(null, null)));
            try {
                assertThat(providerEntered.await(10, TimeUnit.SECONDS)).isTrue();
                var otherSession = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                    jdbc.execute("SET LOCAL lock_timeout = '2s'");
                    return skills.findLockedById(skillId).orElseThrow().getId();
                }));
                assertThat(otherSession.get(5, TimeUnit.SECONDS)).isEqualTo(skillId);
                setTime(candidateId, "machine_lease_until", Instant.now().minusSeconds(1));
                Instant lease = candidates.findById(candidateId).orElseThrow().getMachineLeaseUntil();
                assertThat(lease).isBefore(Instant.now());
                assertThat(abandoned(Instant.now().minusSeconds(600), lease.minusSeconds(1)))
                        .extracting(SkillAvatarCandidate::getId).doesNotContain(candidateId);
                assertThat(abandoned(Instant.now().minusSeconds(600), lease))
                        .extracting(SkillAvatarCandidate::getId).contains(candidateId);
                machineRecovery.recover();
                assertThat(candidates.findById(candidateId).orElseThrow().getMachineAttemptCount()).isEqualTo(2);
            } finally {
                releaseFirst.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS).machineStatus()).isEqualTo("PASSED");
            assertThat(calls.get()).isEqualTo(2);
        }
        assertThat(candidates.findById(candidateId).orElseThrow().getMachineStatus())
                .isEqualTo(SkillAvatarCandidate.MachineStatus.PASSED);
        assertThat(readSkill(skillId).getActiveAvatarId()).isEqualTo(candidateId);
    }

    @Test
    void missingScopeListenerIsCompensatedWithoutPublishingPrivateImage() {
        Long skillId = skill(true);
        Long oldAvatarId = readSkill(skillId).getActiveAvatarId();
        when(storage.getObject("old/" + skillId)).thenThrow(new IllegalStateException("temporary storage outage"));
        scopeRecovery.recover();
        assertThat(readSkill(skillId).getPendingAvatarId()).isNull();
        assertThat(SkillAvatarUrls.current(readSkill(skillId), "test")).isNull();
        org.mockito.Mockito.doAnswer(ignored -> new ByteArrayInputStream(new byte[] {1, 2, 3}))
                .when(storage).getObject("old/" + skillId);
        scopeRecovery.recover();
        Skill recovered = readSkill(skillId);
        assertThat(recovered.getActiveAvatarId()).isNotEqualTo(oldAvatarId);
        assertThat(recovered.getActiveAvatarAudience()).isEqualTo(SkillVisibility.PUBLIC);
        assertThat(candidates.findBySkillIdOrderByCreatedAtDesc(skillId)).hasSize(2);
        assertThat(candidates.findById(recovered.getActiveAvatarId()).orElseThrow().isScopeReview()).isTrue();
    }

    @Test
    void committedScopeCandidateRecoversWithCooldownAndOwnerRetryAfterThreeAutomaticAttempts() {
        // Independent candidates: a manually repeated ERROR cannot be applied to an already active PASS.
        for (AvatarModerationProvider.Verdict manualVerdict : new AvatarModerationProvider.Verdict[] {
                AvatarModerationProvider.Verdict.ERROR, AvatarModerationProvider.Verdict.PASS}) {
            Long skillId = skill(true);
            Long candidateId = avatars.prepareScopeReview(skillId, null).candidateId();
            assertThat(candidates.findById(candidateId).orElseThrow().isScopeReview()).isTrue();
            assertThat(candidates.findById(candidateId).orElseThrow().getScopeReviewVersionId()).isNull();
            assertThat(abandoned(Instant.now().plusSeconds(660), Instant.now().plusSeconds(660)))
                    .extracting(SkillAvatarCandidate::getId).doesNotContain(candidateId);
            setTime(candidateId, "created_at", Instant.now().minusSeconds(660));
            assertThat(candidates.findById(candidateId).orElseThrow().getCreatedAt()).isBefore(Instant.now().minusSeconds(600));
            org.mockito.Mockito.clearInvocations(moderation);
            when(moderation.moderate(eq("xfyun"), any(byte[].class))).thenReturn(AvatarModerationProvider.Verdict.ERROR);
            scopeRecovery.recover();
            SkillAvatarCandidate first = candidates.findById(candidateId).orElseThrow();
            assertThat(first.getMachineAttemptCount()).isEqualTo(1);
            assertThat(first.getMachineNextRetryAt()).isBetween(Instant.now().plusSeconds(3500), Instant.now().plusSeconds(3601));
            assertThatThrownBy(() -> avatars.processMachine(candidateId, new AuditRequestContext(null, null)))
                    .isInstanceOf(DomainConflictException.class);
            scopeRecovery.recover();
            assertThat(candidates.findById(candidateId).orElseThrow().getMachineAttemptCount()).isEqualTo(1);
            for (int attempt = 2; attempt <= 3; attempt++) {
                setTime(candidateId, "machine_next_retry_at", Instant.now().minusSeconds(1));
                scopeRecovery.recover();
                assertThat(candidates.findById(candidateId).orElseThrow().getMachineAttemptCount()).isEqualTo(attempt);
            }
            avatars.requireCurrentOwnerCandidate(namespaceSlug(skillId), "skill", candidateId, "owner");
            assertThatThrownBy(() -> avatars.processMachine(candidateId, new AuditRequestContext(null, null)))
                    .isInstanceOf(DomainConflictException.class);
            assertThatThrownBy(() -> avatars.requireCurrentOwnerCandidate(namespaceSlug(skillId), "skill", candidateId, "stranger"))
                    .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException.class);
            org.mockito.Mockito.verify(moderation, org.mockito.Mockito.times(3)).moderate(eq("xfyun"), any(byte[].class));
            setTime(candidateId, "machine_next_retry_at", Instant.now().minusSeconds(1));
            scopeRecovery.recover();
            scopeRecovery.recover();
            assertThat(candidates.findById(candidateId).orElseThrow().getMachineAttemptCount()).isEqualTo(3);
            org.mockito.Mockito.verify(moderation, org.mockito.Mockito.times(3)).moderate(eq("xfyun"), any(byte[].class));
            assertThat(readSkill(skillId).getPendingAvatarId()).isEqualTo(candidateId);
            assertThat(SkillAvatarUrls.current(readSkill(skillId), "test")).isNull();
            when(moderation.moderate(eq("xfyun"), any(byte[].class))).thenReturn(manualVerdict);
            avatars.requireCurrentOwnerCandidate(namespaceSlug(skillId), "skill", candidateId, "owner");
            var retried = avatars.processMachine(candidateId, new AuditRequestContext(null, null));
            assertThat(candidates.findById(candidateId).orElseThrow().getMachineAttemptCount()).isEqualTo(4);
            org.mockito.Mockito.verify(moderation, org.mockito.Mockito.times(4)).moderate(eq("xfyun"), any(byte[].class));
            if (manualVerdict == AvatarModerationProvider.Verdict.PASS) {
                assertThat(retried.avatarUrl()).isNotNull();
                assertThat(readSkill(skillId).getActiveAvatarId()).isEqualTo(candidateId);
                assertThat(readSkill(skillId).getPendingAvatarId()).isNull();
                assertThatThrownBy(() -> avatars.requireCurrentOwnerCandidate(namespaceSlug(skillId), "skill", candidateId, "owner"))
                        .isInstanceOf(DomainConflictException.class);
            } else {
                assertThat(retried.avatarUrl()).isNull();
                assertThat(readSkill(skillId).getPendingAvatarId()).isEqualTo(candidateId);
                assertThat(candidates.findById(candidateId).orElseThrow().getMachineNextRetryAt())
                        .isAfter(Instant.now().plusSeconds(3500));
            }
            assertThat(candidates.findBySkillIdOrderByCreatedAtDesc(skillId)).hasSize(2);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = AvatarModerationProvider.Verdict.class, names = {"PASS", "BLOCK"})
    void changedPolicyDoesNotReuseEitherPassOrBlockFromIdenticalImage(AvatarModerationProvider.Verdict previousVerdict) throws Exception {
        byte[] image = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(imageProcessor.normalize(any())).thenAnswer(call -> call.getArgument(0));
        when(settings.current()).thenReturn(new AvatarReviewSettingsResponse(true, true, 1, null));
        var policyA = new AvatarModerationProvider.Context("image", "v2", "test-policy-A", null);
        var policyB = new AvatarModerationProvider.Context("image", "v2", "test-policy-B", null);
        when(moderation.context("xfyun")).thenReturn(policyA);
        when(moderation.moderate(eq("xfyun"), any(byte[].class))).thenReturn(previousVerdict);
        Long sourceId = skill(false);
        Long sourceCandidate = avatars.submit(namespaceSlug(sourceId), "skill", "owner", image,
                new AuditRequestContext(null, null)).candidateId();
        avatars.processMachine(sourceCandidate, new AuditRequestContext(null, null));
        assertThat(candidates.findById(sourceCandidate).orElseThrow().getMachinePolicyRevision()).isEqualTo("test-policy-A");

        when(moderation.context("xfyun")).thenReturn(policyB);
        Long nextId = skill(false);
        var next = avatars.submit(namespaceSlug(nextId), "skill", "owner", image, new AuditRequestContext(null, null));
        assertThat(next.machineStatus()).isEqualTo("PENDING");
        assertThat(next.avatarUrl()).isNull();
        var nextVerdict = previousVerdict == AvatarModerationProvider.Verdict.PASS
                ? AvatarModerationProvider.Verdict.BLOCK : AvatarModerationProvider.Verdict.PASS;
        when(moderation.moderate(eq("xfyun"), any(byte[].class))).thenReturn(nextVerdict);
        var result = avatars.processMachine(next.candidateId(), new AuditRequestContext(null, null));
        assertThat(result.machineStatus()).isEqualTo(nextVerdict == AvatarModerationProvider.Verdict.PASS ? "PASSED" : "BLOCKED");
        var persisted = candidates.findById(next.candidateId()).orElseThrow();
        assertThat(persisted.getMachineServiceId()).isEqualTo("image");
        assertThat(persisted.getMachineApiVersion()).isEqualTo("v2");
        assertThat(persisted.getMachinePolicyRevision()).isEqualTo("test-policy-B");
        assertThat(persisted.getMachineModelVersion()).isNull();
        assertThat(candidates.findById(sourceCandidate).orElseThrow().getMachinePolicyRevision()).isEqualTo("test-policy-A");
        org.mockito.Mockito.verify(moderation, org.mockito.Mockito.times(2)).moderate(eq("xfyun"), any(byte[].class));
        var details = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(audit, org.mockito.Mockito.times(2)).record(eq("owner"), eq("SKILL_AVATAR_MACHINE_RESULT"),
                eq("SKILL_AVATAR"), any(), any(), any(), any(), details.capture());
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertThat(details.getAllValues().stream().map(detail -> {
            try { return mapper.readTree(detail).path("policyRevision").asText(); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        }).toList()).containsExactly("test-policy-A", "test-policy-B");
        assertThat(mapper.readTree(details.getValue()).path("attemptId").asText()).isNotBlank();
        assertThat(mapper.readTree(details.getValue()).path("modelVersion").asText()).isEqualTo("UNKNOWN");
    }

    @Test
    void changedContextBeforeCallFailsClosedAndRetryRecordsItsActualPolicy() throws Exception {
        when(imageProcessor.normalize(any())).thenAnswer(call -> call.getArgument(0));
        when(settings.current()).thenReturn(new AvatarReviewSettingsResponse(true, true, 1, null));
        Long skillId = skill(false);
        Long candidateId = avatars.submit(namespaceSlug(skillId), "skill", "owner", new byte[]{1},
                new AuditRequestContext(null, null)).candidateId();
        var policyA = new AvatarModerationProvider.Context("image", "v2", "test-policy-A", null);
        var policyB = new AvatarModerationProvider.Context("image", "v2", "test-policy-B", null);
        when(moderation.context("xfyun")).thenReturn(policyA, policyB);
        var failed = avatars.processMachine(candidateId, new AuditRequestContext(null, null));
        assertThat(failed.machineStatus()).isEqualTo("ERROR");
        org.mockito.Mockito.verify(moderation, org.mockito.Mockito.never()).moderate(anyString(), any(byte[].class));
        assertThat(candidates.findById(candidateId).orElseThrow().getMachinePolicyRevision()).isEqualTo("test-policy-A");
        jdbc.update("UPDATE skill_avatar_candidate SET machine_next_retry_at = TIMESTAMP '2000-01-01 00:00:00' WHERE id = ?", candidateId);
        when(moderation.context("xfyun")).thenReturn(policyB);
        var retried = avatars.processMachine(candidateId, new AuditRequestContext(null, null));
        assertThat(retried.machineStatus()).isEqualTo("PASSED");
        assertThat(candidates.findById(candidateId).orElseThrow().getMachinePolicyRevision()).isEqualTo("test-policy-B");
        var details = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(audit, org.mockito.Mockito.times(2)).record(eq("owner"), eq("SKILL_AVATAR_MACHINE_RESULT"),
                eq("SKILL_AVATAR"), eq(candidateId), any(), any(), any(), details.capture());
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var first = mapper.readTree(details.getAllValues().getFirst());
        var second = mapper.readTree(details.getAllValues().getLast());
        assertThat(first.path("policyRevision").asText()).isEqualTo("test-policy-A");
        assertThat(first.path("contextMatched").asBoolean()).isFalse();
        assertThat(second.path("policyRevision").asText()).isEqualTo("test-policy-B");
        assertThat(second.path("contextMatched").asBoolean()).isTrue();
        assertThat(first.path("attemptId").asText()).isNotEqualTo(second.path("attemptId").asText());
    }

    @Test
    void blockedAndProviderFailureNeverExposeAnActiveAvatar() {
        for (AvatarModerationProvider.Verdict verdict : new AvatarModerationProvider.Verdict[] {
                AvatarModerationProvider.Verdict.BLOCK, AvatarModerationProvider.Verdict.ERROR}) {
            Long skillId = skill(false);
            Long candidateId = pending(skillId);
            when(moderation.moderate(eq("xfyun"), any(byte[].class))).thenReturn(verdict);
            var result = avatars.processMachine(candidateId, new AuditRequestContext(null, null));
            assertThat(result.machineStatus()).isEqualTo(verdict == AvatarModerationProvider.Verdict.BLOCK ? "BLOCKED" : "ERROR");
            assertThat(result.avatarUrl()).isNull();
            assertThat(readSkill(skillId).getActiveAvatarId()).isNull();
            assertThat(readSkill(skillId).getPendingAvatarId()).isEqualTo(candidateId);
        }
        Long skillId = skill(false);
        Long candidateId = pending(skillId);
        when(moderation.moderate(eq("xfyun"), any(byte[].class))).thenThrow(new IllegalStateException("provider unavailable"));
        assertThat(avatars.processMachine(candidateId, new AuditRequestContext(null, null)).machineStatus()).isEqualTo("ERROR");
        assertThat(readSkill(skillId).getActiveAvatarId()).isNull();
    }

    @Test
    void globalReviewDoesNotAcceptTeamAdministratorRole() {
        Long skillId = skill(false);
        Skill skill = readSkill(skillId);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Namespace ns = ((com.iflytek.skillhub.domain.namespace.NamespaceRepository) namespaces).findById(skill.getNamespaceId()).orElseThrow();
            ns.setType(com.iflytek.skillhub.domain.namespace.NamespaceType.GLOBAL);
        });
        when(settings.current()).thenReturn(new AvatarReviewSettingsResponse(false, true, 1, null));
        when(imageProcessor.normalize(any())).thenReturn(new byte[] {5, 6});
        Long candidateId = avatars.submit(namespaceSlug(skillId), "skill", "owner", new byte[] {5, 6}, new AuditRequestContext(null, null)).candidateId();
        assertThatThrownBy(() -> unifiedDecision(namespaceSlug(skillId), "skill", candidateId, true, "reviewed", "reviewer",
                java.util.Map.of(skill.getNamespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.ADMIN),
                java.util.Set.of(), new AuditRequestContext(null, null)))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException.class);
        assertThat(readSkill(skillId).getActiveAvatarId()).isNull();
        var approved = unifiedDecision(namespaceSlug(skillId), "skill", candidateId, true, "reviewed", "owner",
                java.util.Map.of(), java.util.Set.of("SUPER_ADMIN"), new AuditRequestContext(null, null));
        assertThat(approved.avatarUrl()).isNotNull();
    }


    @Test
    void expiredImageCleanupRetriesFailureAndPreservesHistoryAndActiveObject() {
        Long skillId = skill(true);
        SkillAvatarCandidate active = candidates.findById(readSkill(skillId).getActiveAvatarId()).orElseThrow();
        Long rejectedId = new TransactionTemplate(transactions).execute(status -> {
            SkillAvatarCandidate rejected = new SkillAvatarCandidate(skillId, "owner", "rejected/" + skillId,
                    "rejected-sha", SkillVisibility.PUBLIC, false, true, null);
            rejected.reject("owner", "keep this textual review history");
            return candidates.saveAndFlush(rejected).getId();
        });
        SkillAvatarCandidate rejected = candidates.findById(rejectedId).orElseThrow();
        assertThat(candidates.findTop100ByDiscardAtBeforeAndObjectKeyIsNotNull(rejected.getDiscardAt().minusSeconds(1)))
                .extracting(SkillAvatarCandidate::getId).doesNotContain(rejectedId);
        assertThat(candidates.findTop100ByDiscardAtBeforeAndObjectKeyIsNotNull(rejected.getDiscardAt()))
                .extracting(SkillAvatarCandidate::getId).doesNotContain(rejectedId);
        assertThat(candidates.findTop100ByDiscardAtBeforeAndObjectKeyIsNotNull(rejected.getDiscardAt().plusSeconds(1)))
                .extracting(SkillAvatarCandidate::getId).contains(rejectedId);
        assertThat(rejected.getDiscardAt()).isBetween(Instant.now().plusSeconds(30L * 86400 - 60),
                Instant.now().plusSeconds(30L * 86400 + 1));
        cleanup.removeExpiredImages();
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).deleteObject("rejected/" + skillId);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            SkillAvatarCandidate expired = candidates.findById(rejectedId).orElseThrow();
            org.springframework.test.util.ReflectionTestUtils.setField(expired, "discardAt", Instant.now().minusSeconds(1));
        });
        org.mockito.Mockito.doThrow(new IllegalStateException("temporary object store outage"))
                .doNothing().when(storage).deleteObject("rejected/" + skillId);
        assertThatThrownBy(cleanup::removeExpiredImages).isInstanceOf(IllegalStateException.class);
        assertThat(candidates.findById(rejectedId).orElseThrow().getObjectKey()).isEqualTo("rejected/" + skillId);
        cleanup.removeExpiredImages();
        SkillAvatarCandidate retained = candidates.findById(rejectedId).orElseThrow();
        assertThat(retained.getObjectKey()).isNull();
        assertThat(retained.getReviewReason()).isEqualTo("keep this textual review history");
        assertThat(retained.getReviewedBy()).isEqualTo("owner");
        assertThat(retained.getStatus()).isEqualTo(SkillAvatarCandidate.Status.REJECTED);
        assertThat(candidates.findById(active.getId()).orElseThrow().getObjectKey()).isEqualTo(active.getObjectKey());
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).deleteObject(active.getObjectKey());
    }




    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void reviewPolicyMatrixAppliesToPublicUploadsButPrivateUploadsBypassReview(boolean machineEnabled, boolean manualEnabled) {
        when(settings.current()).thenReturn(new AvatarReviewSettingsResponse(machineEnabled, manualEnabled, 1, null));
        when(imageProcessor.normalize(any())).thenAnswer(call -> call.getArgument(0));
        Long publicId = skill(false);
        Skill publicSkill = readSkill(publicId);
        var ownerRole = java.util.Map.of(publicSkill.getNamespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.OWNER);
        byte[] publicImage = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var submitted = avatars.submit(namespaceSlug(publicId), "skill", "owner", publicImage, new AuditRequestContext(null, null));
        assertThat(submitted.machineStatus()).isEqualTo(machineEnabled ? "PENDING" : "SKIPPED");
        assertThat(submitted.reviewStatus()).isEqualTo(manualEnabled ? "PENDING" : "NOT_REQUIRED");
        if (machineEnabled || manualEnabled) {
            assertThat(submitted.avatarUrl()).isNull();
            assertThat(readSkill(publicId).getPendingAvatarId()).isEqualTo(submitted.candidateId());
        } else {
            assertThat(submitted.avatarUrl()).isNotNull();
        }
        if (machineEnabled) {
            var machineResult = avatars.processMachine(submitted.candidateId(), new AuditRequestContext(null, null));
            assertThat(machineResult.machineStatus()).isEqualTo("PASSED");
            assertThat(machineResult.avatarUrl() == null).isEqualTo(manualEnabled);
        }
        if (manualEnabled) {
            assertThat(avatars.reviewQueue("owner", ownerRole, java.util.Set.of()))
                    .extracting(com.iflytek.skillhub.dto.SkillAvatarStatusResponse::candidateId).contains(submitted.candidateId());
            var reviewed = unifiedDecision(namespaceSlug(publicId), "skill", submitted.candidateId(), true, "reviewed", "owner",
                    ownerRole, java.util.Set.of(), new AuditRequestContext(null, null));
            assertThat(reviewed.avatarUrl()).isNotNull();
        }
        assertThat(readSkill(publicId).getActiveAvatarId()).isEqualTo(submitted.candidateId());
        assertThat(readSkill(publicId).getPendingAvatarId()).isNull();
        org.mockito.Mockito.verify(moderation, org.mockito.Mockito.times(machineEnabled ? 1 : 0))
                .moderate(anyString(), any(byte[].class));

        Long privateId = skill(false);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Skill privateSkill = skills.findLockedById(privateId).orElseThrow();
            privateSkill.setVisibility(SkillVisibility.PRIVATE);
        });
        var privateUploaded = avatars.submit(namespaceSlug(privateId), "skill", "owner",
                UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), new AuditRequestContext(null, null));
        assertThat(privateUploaded.machineStatus()).isEqualTo("SKIPPED");
        assertThat(privateUploaded.reviewStatus()).isEqualTo("NOT_REQUIRED");
        assertThat(privateUploaded.avatarUrl()).isNotNull();
        assertThat(readSkill(privateId).getActiveAvatarId()).isEqualTo(privateUploaded.candidateId());
        assertThat(readSkill(privateId).getPendingAvatarId()).isNull();
        assertThat(avatars.reviewQueue("owner", java.util.Map.of(readSkill(privateId).getNamespaceId(),
                com.iflytek.skillhub.domain.namespace.NamespaceRole.OWNER), java.util.Set.of()))
                .extracting(com.iflytek.skillhub.dto.SkillAvatarStatusResponse::candidateId).doesNotContain(privateUploaded.candidateId());
        org.mockito.Mockito.verify(moderation, org.mockito.Mockito.times(machineEnabled ? 1 : 0))
                .moderate(anyString(), any(byte[].class));
    }

    @Test
    void teamAdministratorCannotActAsOwnerOrReadPrivateAvatar() {
        Long skillId = skill(false);
        Skill skill = readSkill(skillId);
        var adminRole = java.util.Map.of(skill.getNamespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.ADMIN);
        when(imageProcessor.normalize(any())).thenAnswer(call -> call.getArgument(0));
        assertThatThrownBy(() -> avatars.submit(namespaceSlug(skillId), "skill", "admin", new byte[] {7},
                new AuditRequestContext(null, null)))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException.class);
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never())
                .putObject(anyString(), any(), org.mockito.ArgumentMatchers.anyLong(), anyString());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Skill privateSkill = skills.findLockedById(skillId).orElseThrow();
            privateSkill.setVisibility(SkillVisibility.PRIVATE);
        });
        var uploaded = avatars.submit(namespaceSlug(skillId), "skill", "owner", new byte[] {8}, new AuditRequestContext(null, null));
        SkillQueryService.SkillDetailDTO detail = new SkillQueryService.SkillDetailDTO(
                skillId, null, null, null, null, null, null, null, null, null, null, null, null,
                false, null, null, null, false, false, false, false, null, null, null, null, null, null, null);
        when(skillQueries.getSkillDetail(anyString(), eq("skill"), eq("admin"), any())).thenReturn(detail);
        when(visibility.canAccess(any(), eq("admin"), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> avatars.readActive(namespaceSlug(skillId), "skill", "admin", adminRole, java.util.Set.of()))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException.class);
        assertThatThrownBy(() -> avatars.readCandidate(namespaceSlug(skillId), "skill", uploaded.candidateId(), "admin", adminRole, java.util.Set.of()))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException.class);
        assertThatThrownBy(() -> avatars.remove(namespaceSlug(skillId), "skill", "admin", false, null, adminRole,
                java.util.Set.of(), new AuditRequestContext(null, null)))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException.class);
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).getObject(anyString());
        assertThat(readSkill(skillId).getActiveAvatarId()).isEqualTo(uploaded.candidateId());
    }


    @Test
    void scopeRejectionSuppressesCurrentPublicationAndNewPublicationCreatesOneFreshReview() {
        Long skillId = skill(true);
        publishVersion(skillId, "1.0.0");
        when(settings.current()).thenReturn(new AvatarReviewSettingsResponse(false, true, 1, null));
        Long rejectedId = avatars.prepareScopeReview(skillId, null).candidateId();
        var roles = java.util.Map.of(readSkill(skillId).getNamespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.OWNER);
        unifiedDecision(namespaceSlug(skillId), "skill", rejectedId, false, "rejected for this publication", "owner",
                roles, java.util.Set.of(), new AuditRequestContext(null, null));
        scopeRecovery.recover();
        scopeRecovery.recover();
        assertThat(candidates.findBySkillIdOrderByCreatedAtDesc(skillId)).hasSize(2);
        assertThat(readSkill(skillId).getPendingAvatarId()).isNull();
        assertThat(SkillAvatarUrls.current(readSkill(skillId), "test")).isNull();
        publishVersion(skillId, "1.1.0");
        scopeRecovery.recover();
        scopeRecovery.recover();
        assertThat(candidates.findBySkillIdOrderByCreatedAtDesc(skillId)).hasSize(3);
        Long newCandidateId = readSkill(skillId).getPendingAvatarId();
        assertThat(newCandidateId).isNotNull().isNotEqualTo(rejectedId);
        SkillAvatarCandidate newCandidate = candidates.findById(newCandidateId).orElseThrow();
        assertThat(newCandidate.getScopeReviewVersionId()).isEqualTo(readSkill(skillId).getLatestVersionId());
        assertThat(newCandidate.getReviewStatus()).isEqualTo(SkillAvatarCandidate.ReviewStatus.PENDING);
        assertThat(newCandidate.getStatus()).isEqualTo(SkillAvatarCandidate.Status.PENDING);
        assertThat(SkillAvatarUrls.current(readSkill(skillId), "test")).isNull();
    }

    @Test
    void machineOnlyBlockCanBeAppealedAndExplicitlyApproved() {
        Long skillId = skill(false);
        Long candidateId = pending(skillId);
        when(moderation.moderate(eq("xfyun"), any(byte[].class))).thenReturn(AvatarModerationProvider.Verdict.BLOCK);
        assertThat(avatars.processMachine(candidateId, new AuditRequestContext(null, null)).avatarUrl()).isNull();
        var appealed = avatars.appeal(namespaceSlug(skillId), "skill", candidateId, "owner", new AuditRequestContext(null, null));
        assertThat(appealed.reviewStatus()).isEqualTo("APPEAL_PENDING");
        assertThat(appealed.avatarUrl()).isNull();
        var approved = unifiedDecision(namespaceSlug(skillId), "skill", candidateId, true, "machine false positive", "owner",
                java.util.Map.of(readSkill(skillId).getNamespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.OWNER),
                java.util.Set.of(), new AuditRequestContext(null, null));
        assertThat(approved.avatarUrl()).isNotNull();
        SkillAvatarCandidate candidate = candidates.findById(candidateId).orElseThrow();
        assertThat(candidate.getReviewReason()).isEqualTo("machine false positive");
        assertThat(candidate.getReviewedBy()).isEqualTo("owner");
        assertThat(readSkill(skillId).getPendingAvatarId()).isNull();
        assertThat(readSkill(skillId).getActiveAvatarId()).isEqualTo(candidateId);
    }

    private void publishVersion(Long skillId, String version) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var published = new com.iflytek.skillhub.domain.skill.SkillVersion(skillId, version, "owner");
            published.setStatus(com.iflytek.skillhub.domain.skill.SkillVersionStatus.PUBLISHED);
            versions.saveAndFlush(published);
            skills.findLockedById(skillId).orElseThrow().setLatestVersionId(published.getId());
        });
    }

    private java.util.List<SkillAvatarCandidate> abandoned(Instant staleBefore, Instant now) {
        return candidates.findAbandonedMachineCandidates(0L, SkillAvatarCandidate.Status.PENDING,
                SkillAvatarCandidate.MachineStatus.PENDING, SkillAvatarCandidate.MachineStatus.PROCESSING,
                staleBefore, now, org.springframework.data.domain.PageRequest.of(0, 100));
    }

    // Hibernate binds these timestamp-without-zone columns in UTC; use the same calendar in JDBC fixtures.
    private void setTime(Long candidateId, String column, Instant value) {
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("UPDATE skill_avatar_candidate SET " + column + " = ? WHERE id = ?");
            statement.setTimestamp(1, java.sql.Timestamp.from(value),
                    java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
            statement.setLong(2, candidateId);
            return statement;
        });
    }

    private Skill readSkill(Long id) {
        return ((com.iflytek.skillhub.domain.skill.SkillRepository) skills).findById(id).orElseThrow();
    }

    private String namespaceSlug(Long skillId) {
        return ((com.iflytek.skillhub.domain.namespace.NamespaceRepository) namespaces).findById(readSkill(skillId).getNamespaceId()).orElseThrow().getSlug();
    }

    private Long skill(boolean expanded) {
        return new TransactionTemplate(transactions).execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Namespace ns = namespaces.saveAndFlush(new Namespace("avatar-" + suffix, "Avatar recovery", "owner"));
            members.saveAndFlush(new com.iflytek.skillhub.domain.namespace.NamespaceMember(
                    ns.getId(), "owner", com.iflytek.skillhub.domain.namespace.NamespaceRole.OWNER));
            Skill skill = skills.saveAndFlush(new Skill(ns.getId(), "skill", "owner", expanded ? SkillVisibility.PRIVATE : SkillVisibility.PUBLIC));
            if (expanded) {
                SkillAvatarCandidate old = new SkillAvatarCandidate(skill.getId(), "owner", "old/" + skill.getId(), "old-sha-" + suffix,
                        SkillVisibility.PRIVATE, false, false, null);
                old.activate();
                candidates.saveAndFlush(old);
                skill.activateAvatar(old.getId(), SkillVisibility.PRIVATE);
                skill.setVisibility(SkillVisibility.PUBLIC);
                skills.saveAndFlush(skill);
            }
            return skill.getId();
        });
    }

    private Long pending(Long skillId) {
        return new TransactionTemplate(transactions).execute(status -> {
            SkillAvatarCandidate candidate = new SkillAvatarCandidate(skillId, "owner", "pending/" + skillId,
                    "sha-" + skillId, SkillVisibility.PUBLIC, true, false, "xfyun");
            candidates.saveAndFlush(candidate);
            Skill skill = skills.findLockedById(skillId).orElseThrow();
            skill.setPendingAvatarId(candidate.getId());
            return candidate.getId();
        });
    }
}
