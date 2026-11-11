package com.iflytek.skillhub.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.event.ReviewApprovedEvent;
import com.iflytek.skillhub.domain.event.ReviewSubmittedEvent;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.governance.GovernanceNotificationService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceAccessPolicy;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberService;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.review.ReviewAutoPublicationHandoff;
import com.iflytek.skillhub.domain.review.ReviewAvatarParticipant;
import com.iflytek.skillhub.domain.review.ReviewDecisionMethod;
import com.iflytek.skillhub.domain.review.ReviewPermissionChecker;
import com.iflytek.skillhub.domain.review.ReviewService;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.review.TeamAutoPublicationPolicy;
import com.iflytek.skillhub.domain.review.TeamAutoPublicationService;
import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityAudit;
import com.iflytek.skillhub.domain.security.SecurityScanResponse;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.security.SecurityVerdict;
import com.iflytek.skillhub.domain.skill.SkillOwnerAccessPolicy;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.service.SkillGovernanceService;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.service.SkillStorageDeletionCompensationService;
import com.iflytek.skillhub.domain.skill.service.SkillVisibilityService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.PrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.infra.jpa.AuditLogJpaRepository;
import com.iflytek.skillhub.infra.jpa.JpaSkillRepositoryAdapter;
import com.iflytek.skillhub.infra.jpa.NamespaceJpaRepository;
import com.iflytek.skillhub.infra.jpa.NamespaceMemberJpaRepository;
import com.iflytek.skillhub.infra.jpa.SecurityAuditJpaRepository;
import com.iflytek.skillhub.infra.jpa.SkillVersionJpaRepository;
import com.iflytek.skillhub.infra.jpa.UserAccountJpaRepository;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.support.PostgresTestImage;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Team-only automatic publication on real PostgreSQL: Flyway V123 schema, row locks, committed
 * state and the exact events produced by submission, scan completion, fallback and replay.
 *
 * <p>The scan result is injected through {@link SecurityScanService#processScanResult}, i.e. a
 * controlled scan callback; no scanner process is involved.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Import({SkillOwnerAccessPolicy.class, JpaSkillRepositoryAdapter.class, SkillPublishService.class,
        SkillGovernanceService.class, SkillVisibilityService.class, ReviewService.class,
        ReviewPermissionChecker.class, SecurityScanService.class, TeamAutoPublicationPolicy.class,
        TeamAutoPublicationService.class, ReviewAutoPublicationHandoff.class, NamespaceMemberService.class,
        NamespaceService.class, NamespaceAccessPolicy.class, AuditLogService.class,
        TeamAutoPublicationPostgresTest.Configuration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TeamAutoPublicationPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("skillhub.security.scanner.enabled", () -> true);
    }

    @TestConfiguration
    static class Configuration {
        @Bean Clock clock() {
            return Clock.systemUTC();
        }

        @Bean ObjectMapper objectMapper() {
            // Same unknown-property tolerance as the Spring Boot application mapper.
            return new ObjectMapper()
                    .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }

        @Bean SkillMetadataParser skillMetadataParser() {
            return new SkillMetadataParser();
        }

        @Bean EventRecorder eventRecorder() {
            return new EventRecorder();
        }
    }

    static class EventRecorder {
        final List<Object> events = new CopyOnWriteArrayList<>();

        @EventListener
        public void on(ReviewSubmittedEvent event) {
            events.add(event);
        }

        @EventListener
        public void on(ReviewApprovedEvent event) {
            events.add(event);
        }

        @EventListener
        public void on(SkillPublishedEvent event) {
            events.add(event);
        }

        long count(Class<?> type) {
            return events.stream().filter(type::isInstance).count();
        }
    }

    @MockBean private ScanTaskProducer scanTaskProducer;
    @MockBean private GovernanceNotificationService notifications;
    @MockBean private ReviewAvatarParticipant avatars;
    @MockBean private ObjectStorageService storage;
    @MockBean private SkillStorageDeletionCompensationService compensation;
    @MockBean private SkillPackageValidator validator;
    @MockBean private PrePublishValidator prePublish;
    @MockBean private com.iflytek.skillhub.domain.skill.service.SkillDownloadService downloads;
    @MockBean private com.iflytek.skillhub.domain.authoring.SkillDraftRepository drafts;
    @MockBean private com.iflytek.skillhub.domain.review.PromotionRequestRepository promotions;

    @Autowired private SkillPublishService publishing;
    @Autowired private SecurityScanService scanning;
    @Autowired private ReviewService reviewing;
    @Autowired private NamespaceMemberService memberService;
    @Autowired private UserAccountJpaRepository users;
    @Autowired private NamespaceJpaRepository namespaces;
    @Autowired private NamespaceMemberJpaRepository members;
    @Autowired private SkillRepository skills;
    @Autowired private SkillVersionJpaRepository versionRows;
    @Autowired private SkillVersionRepository versions;
    @Autowired private ReviewTaskRepository reviews;
    @Autowired private SecurityAuditJpaRepository audits;
    @Autowired private AuditLogJpaRepository auditLogs;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EventRecorder recorder;
    @Autowired private com.iflytek.skillhub.infra.jpa.ScanTaskOutboxJpaRepository outboxes;

    private String owner;
    private String publisher;
    private String slug;
    private Long namespaceId;
    private String platformAdmin;

    @BeforeEach
    void setUp() {
        recorder.events.clear();
        when(validator.validate(anyList())).thenReturn(ValidationResult.pass());
        when(prePublish.validate(any())).thenReturn(ValidationResult.pass());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        owner = "tap-owner-" + suffix;
        publisher = "tap-member-" + suffix;
        slug = "tap-" + suffix;
        platformAdmin = "tap-admin-" + suffix;
        namespaceId = new TransactionTemplate(transactions).execute(status -> {
            users.saveAndFlush(new UserAccount(owner, owner, null, null));
            users.saveAndFlush(new UserAccount(publisher, publisher, null, null));
            users.saveAndFlush(new UserAccount(platformAdmin, platformAdmin, null, null));
            Namespace namespace = namespaces.saveAndFlush(new Namespace(slug, "Team", owner));
            members.saveAndFlush(new NamespaceMember(namespace.getId(), owner, NamespaceRole.OWNER));
            members.saveAndFlush(new NamespaceMember(namespace.getId(), publisher, NamespaceRole.MEMBER));
            return namespace.getId();
        });
    }

    @Test
    void migratedSchemaDefaultsExistingRowsToNoAutomaticEligibility() {
        Map<String, Object> member = jdbc.queryForMap(
                "SELECT team_auto_publication_grant_id FROM namespace_member WHERE namespace_id = ? AND user_id = ?",
                namespaceId, publisher);
        assertThat(member.get("team_auto_publication_grant_id")).isNull();
        Map<String, Object> defaults = jdbc.queryForMap("""
                SELECT column_default, is_nullable FROM information_schema.columns
                WHERE table_name = 'review_task' AND column_name = 'auto_publication_cancelled'
                """);
        assertThat(defaults.get("column_default")).isEqualTo("false");
        assertThat(defaults.get("is_nullable")).isEqualTo("NO");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name IN ('namespace', 'namespace_member', 'review_task')
                  AND column_name IN ('trusted_publish_enabled', 'review_exempt', 'auto_approve_on_safe_scan')
                """, Integer.class)).isZero();
    }

    @Test
    void safeCurrentRoundPublishesOnceAsSystemAndReplayChangesNothing() {
        grant();
        Submitted first = submit("1.0.0");
        assertThat(version(first.versionId()).getStatus()).isEqualTo(SkillVersionStatus.SCANNING);
        assertThat(recorder.count(ReviewSubmittedEvent.class)).isZero();

        complete(first, SecurityVerdict.SAFE);

        ReviewTask approved = reviews.findById(first.reviewId()).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(ReviewTaskStatus.APPROVED);
        assertThat(approved.getDecisionMethod()).isEqualTo(ReviewDecisionMethod.SYSTEM_AUTOMATIC);
        assertThat(approved.getDecisionScanTaskId()).isEqualTo(first.scanTaskId());
        assertThat(approved.getReviewedBy()).isNull();
        assertThat(approved.getSubmittedBy()).isEqualTo(publisher);
        assertThat(version(first.versionId()).getStatus()).isEqualTo(SkillVersionStatus.PUBLISHED);
        assertThat(skills.findById(first.skillId()).orElseThrow().getLatestVersionId()).isEqualTo(first.versionId());
        assertThat(recorder.count(SkillPublishedEvent.class)).isEqualTo(1);
        assertThat(recorder.count(ReviewApprovedEvent.class)).isEqualTo(1);
        assertThat(auditLogs.findAll().stream()
                .filter(log -> "TEAM_AUTO_PUBLICATION_APPROVE".equals(log.getAction()))
                .filter(log -> first.reviewId().equals(log.getTargetId())))
                .singleElement()
                .satisfies(log -> assertThat(log.getDetailJson()).contains(first.scanTaskId(), publisher));

        complete(first, SecurityVerdict.SAFE);
        scanning.processScanFailure(first.scanTaskId(), first.versionId(), ScannerType.SKILL_SCANNER, "late");

        assertThat(recorder.count(SkillPublishedEvent.class)).isEqualTo(1);
        assertThat(recorder.count(ReviewApprovedEvent.class)).isEqualTo(1);
        assertThat(version(first.versionId()).getStatus()).isEqualTo(SkillVersionStatus.PUBLISHED);

        Submitted update = submit("1.1.0");
        complete(update, SecurityVerdict.SAFE);
        assertThat(version(update.versionId()).getStatus()).isEqualTo(SkillVersionStatus.PUBLISHED);
        assertThat(skills.findById(first.skillId()).orElseThrow().getLatestVersionId()).isEqualTo(update.versionId());
    }

    @Test
    void unsafeOrFailedScansNeverPublishAndHandOffToReviewersOnce() {
        grant();
        Submitted unsafe = submit("1.0.0");
        complete(unsafe, SecurityVerdict.DANGEROUS);

        assertThat(version(unsafe.versionId()).getStatus()).isEqualTo(SkillVersionStatus.PENDING_REVIEW);
        ReviewTask manual = reviews.findById(unsafe.reviewId()).orElseThrow();
        assertThat(manual.getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
        assertThat(manual.isAutoPublicationCancelled()).isTrue();
        assertThat(manual.isManualReviewNotified()).isTrue();
        assertThat(recorder.count(ReviewSubmittedEvent.class)).isEqualTo(1);
        assertThat(recorder.count(SkillPublishedEvent.class)).isZero();
        assertThat(reviews.findByNamespaceIdAndStatus(namespaceId, ReviewTaskStatus.PENDING,
                org.springframework.data.domain.Pageable.unpaged()).getContent())
                .extracting(ReviewTask::getId).contains(unsafe.reviewId());

        recorder.events.clear();
        Submitted failed = submit("1.0.1");
        scanning.processScanFailure(failed.scanTaskId(), failed.versionId(), ScannerType.SKILL_SCANNER, "timeout");
        scanning.processScanFailure(failed.scanTaskId(), failed.versionId(), ScannerType.SKILL_SCANNER, "timeout");

        assertThat(version(failed.versionId()).getStatus()).isEqualTo(SkillVersionStatus.SCAN_FAILED);
        assertThat(reviews.findById(failed.reviewId()).orElseThrow().isAutoPublicationCancelled()).isTrue();
        assertThat(recorder.count(ReviewSubmittedEvent.class)).isEqualTo(1);
        assertThat(recorder.count(SkillPublishedEvent.class)).isZero();
    }

    @Test
    void revokeDuringScanFallsBackAndRegrantDoesNotReviveOldTask() {
        grant();
        Submitted submitted = submit("1.0.0");
        UUID original = reviews.findById(submitted.reviewId()).orElseThrow().getAutoPublicationGrantId();

        memberService.setTeamAutoPublication(namespaceId, publisher, false, owner);
        assertThat(version(submitted.versionId()).getStatus()).isEqualTo(SkillVersionStatus.SCANNING);
        assertThat(recorder.count(ReviewSubmittedEvent.class)).isEqualTo(1);
        memberService.setTeamAutoPublication(namespaceId, publisher, true, owner);
        UUID regranted = members.findByNamespaceIdAndUserId(namespaceId, publisher).orElseThrow()
                .getTeamAutoPublicationGrantId();
        assertThat(regranted).isNotNull().isNotEqualTo(original);

        complete(submitted, SecurityVerdict.SAFE);

        ReviewTask task = reviews.findById(submitted.reviewId()).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
        assertThat(task.getAutoPublicationGrantId()).isEqualTo(original);
        assertThat(version(submitted.versionId()).getStatus()).isEqualTo(SkillVersionStatus.PENDING_REVIEW);
        assertThat(recorder.count(ReviewSubmittedEvent.class)).isEqualTo(1);
        assertThat(recorder.count(SkillPublishedEvent.class)).isZero();
        assertThat(auditLogs.findAll().stream()
                .filter(log -> "TEAM_AUTO_PUBLICATION_CHANGE".equals(log.getAction()))
                .filter(log -> owner.equals(log.getActorUserId()))
                .filter(log -> log.getDetailJson().contains(publisher))).hasSize(3);
    }

    @Test
    void grantAfterSubmissionDoesNotAffectEarlierTask() {
        Submitted submitted = submit("1.0.0");
        assertThat(reviews.findById(submitted.reviewId()).orElseThrow().getAutoPublicationGrantId()).isNull();
        assertThat(recorder.count(ReviewSubmittedEvent.class)).isEqualTo(1);
        grant();

        complete(submitted, SecurityVerdict.SAFE);

        assertThat(version(submitted.versionId()).getStatus()).isEqualTo(SkillVersionStatus.PENDING_REVIEW);
        assertThat(recorder.count(SkillPublishedEvent.class)).isZero();
    }

    @Test
    void removalDuringScanBlocksApprovalAndRevocationDoesNotWithdrawPublishedVersion() {
        grant();
        Submitted published = submit("1.0.0");
        complete(published, SecurityVerdict.SAFE);
        Submitted pending = submit("1.1.0");

        memberService.removeMember(namespaceId, publisher, owner);
        complete(pending, SecurityVerdict.SAFE);

        assertThat(version(published.versionId()).getStatus()).isEqualTo(SkillVersionStatus.PUBLISHED);
        assertThat(version(pending.versionId()).getStatus()).isEqualTo(SkillVersionStatus.PENDING_REVIEW);
        assertThat(skills.findById(published.skillId()).orElseThrow().getLatestVersionId())
                .isEqualTo(published.versionId());
    }

    @Test
    void privateSkillUploadedAsTeamOnlyNeverAutoApproves() {
        grant();
        String content = skillMd("1.0.0");
        new TransactionTemplate(transactions).executeWithoutResult(status -> publishing.publishFromEntries(
                slug, entries(content), publisher, SkillVisibility.PRIVATE, Set.of()));
        Submitted expansion = submit("1.1.0");
        assertThat(reviews.findById(expansion.reviewId()).orElseThrow().getAutoPublicationGrantId()).isNull();

        complete(expansion, SecurityVerdict.SAFE);

        assertThat(version(expansion.versionId()).getStatus()).isEqualTo(SkillVersionStatus.PENDING_REVIEW);
        assertThat(skills.findById(expansion.skillId()).orElseThrow().getVisibility()).isEqualTo(SkillVisibility.PRIVATE);
    }

    @Test
    void humanApprovalRacingScanCompletionPublishesExactlyOnce() throws Exception {
        grant();
        Submitted submitted = submit("1.0.0");
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var human = pool.submit(() -> {
                start.await();
                try {
                    reviewing.approveReview(submitted.reviewId(), owner, "ok",
                            Map.of(namespaceId, NamespaceRole.OWNER), Set.of());
                    return true;
                } catch (RuntimeException rejected) {
                    // Either still scanning or already settled by the system decision.
                    return false;
                }
            });
            var automatic = pool.submit(() -> {
                start.await();
                complete(submitted, SecurityVerdict.SAFE);
                return true;
            });
            start.countDown();
            boolean humanWon = human.get(30, TimeUnit.SECONDS);
            automatic.get(30, TimeUnit.SECONDS);
            ReviewTask task = reviews.findById(submitted.reviewId()).orElseThrow();
            assertThat(task.getDecisionMethod())
                    .isEqualTo(humanWon ? ReviewDecisionMethod.HUMAN : ReviewDecisionMethod.SYSTEM_AUTOMATIC);
        }

        assertThat(version(submitted.versionId()).getStatus()).isEqualTo(SkillVersionStatus.PUBLISHED);
        assertThat(reviews.findById(submitted.reviewId()).orElseThrow().getStatus()).isEqualTo(ReviewTaskStatus.APPROVED);
        assertThat(recorder.count(SkillPublishedEvent.class)).isEqualTo(1);
        assertThat(recorder.count(ReviewApprovedEvent.class)).isEqualTo(1);
    }

    @Test
    void revocationRacingSafeCompletionEitherPublishesFirstOrFallsBack() throws Exception {
        grant();
        Submitted submitted = submit("1.0.0");
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var revoke = pool.submit(() -> {
                start.await();
                memberService.setTeamAutoPublication(namespaceId, publisher, false, owner);
                return true;
            });
            var complete = pool.submit(() -> {
                start.await();
                complete(submitted, SecurityVerdict.SAFE);
                return true;
            });
            start.countDown();
            revoke.get(30, TimeUnit.SECONDS);
            complete.get(30, TimeUnit.SECONDS);
        }

        ReviewTask task = reviews.findById(submitted.reviewId()).orElseThrow();
        SkillVersionStatus status = version(submitted.versionId()).getStatus();
        if (task.getStatus() == ReviewTaskStatus.APPROVED) {
            assertThat(status).isEqualTo(SkillVersionStatus.PUBLISHED);
            assertThat(task.getDecisionMethod()).isEqualTo(ReviewDecisionMethod.SYSTEM_AUTOMATIC);
            assertThat(recorder.count(ReviewSubmittedEvent.class)).isZero();
        } else {
            assertThat(status).isEqualTo(SkillVersionStatus.PENDING_REVIEW);
            assertThat(task.isAutoPublicationCancelled()).isTrue();
            assertThat(recorder.count(ReviewSubmittedEvent.class)).isEqualTo(1);
        }
        assertThat(recorder.count(SkillPublishedEvent.class)).isLessThanOrEqualTo(1);
    }

    @Test
    void withdrawalRacingSafeCompletionPublishesOnceAndNeverDeletesTheDecidedTask() throws Exception {
        grant();
        Submitted submitted = submit("1.0.0");
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var withdraw = pool.submit(() -> {
                start.await();
                try {
                    new TransactionTemplate(transactions).executeWithoutResult(status ->
                            reviewing.withdrawReview(submitted.versionId(), publisher));
                    return true;
                } catch (RuntimeException rejected) {
                    // Still scanning, or already settled by the system decision.
                    return false;
                }
            });
            var automatic = pool.submit(() -> {
                start.await();
                complete(submitted, SecurityVerdict.SAFE);
                return true;
            });
            start.countDown();
            assertThat(withdraw.get(30, TimeUnit.SECONDS)).isFalse();
            automatic.get(30, TimeUnit.SECONDS);
        }

        ReviewTask task = reviews.findById(submitted.reviewId()).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.APPROVED);
        assertThat(task.getDecisionMethod()).isEqualTo(ReviewDecisionMethod.SYSTEM_AUTOMATIC);
        assertThat(version(submitted.versionId()).getStatus()).isEqualTo(SkillVersionStatus.PUBLISHED);
        assertThat(skills.findById(submitted.skillId()).orElseThrow().getLatestVersionId())
                .isEqualTo(submitted.versionId());
        assertThat(recorder.count(SkillPublishedEvent.class)).isEqualTo(1);
    }

    @Test
    void supersedingUploadPreventsOlderSafeRoundFromMovingLatest() {
        grant();
        Submitted older = submit("1.0.0");
        Submitted newer = submit("1.1.0");

        complete(older, SecurityVerdict.SAFE);

        assertThat(version(older.versionId()).getStatus()).isNotEqualTo(SkillVersionStatus.PUBLISHED);
        assertThat(skills.findById(older.skillId()).orElseThrow().getLatestVersionId()).isNull();
        complete(newer, SecurityVerdict.SAFE);
        assertThat(skills.findById(older.skillId()).orElseThrow().getLatestVersionId()).isEqualTo(newer.versionId());
    }

    @Test
    void platformAdminRoleRoundTripRevokesAndNeverRevivesInFlightTask() {
        grant();
        Submitted submitted = submit("1.0.0");
        var admin = adminConsole();

        new TransactionTemplate(transactions).executeWithoutResult(status -> admin.updateMemberRole(slug, publisher,
                new com.iflytek.skillhub.dto.UpdateMemberRoleRequest(NamespaceRole.ADMIN), platformAdmin));
        assertThat(members.findByNamespaceIdAndUserId(namespaceId, publisher).orElseThrow()
                .getTeamAutoPublicationGrantId()).isNull();
        new TransactionTemplate(transactions).executeWithoutResult(status -> admin.updateMemberRole(slug, publisher,
                new com.iflytek.skillhub.dto.UpdateMemberRoleRequest(NamespaceRole.MEMBER), platformAdmin));
        complete(submitted, SecurityVerdict.SAFE);

        assertHandedOffWithoutPublication(submitted);
        assertThat(grantChangeAudits()).hasSize(2)
                .last()
                .satisfies(log -> {
                    assertThat(log.getActorUserId()).isEqualTo(platformAdmin);
                    assertThat(log.getDetailJson()).contains("ROLE_CHANGED", "\"handedOffTasks\": 1");
                });
    }

    @Test
    void platformAdminRemovalRevokesAndHandsOffInFlightTask() {
        grant();
        Submitted submitted = submit("1.0.0");

        new TransactionTemplate(transactions).executeWithoutResult(status ->
                adminConsole().removeMember(slug, publisher, platformAdmin));
        complete(submitted, SecurityVerdict.SAFE);

        assertHandedOffWithoutPublication(submitted);
        assertThat(grantChangeAudits()).hasSize(2).last()
                .satisfies(log -> assertThat(log.getDetailJson()).contains("MEMBER_REMOVED"));
    }

    @Test
    void platformAdminOwnershipTransferRevokesNewOwnersGrant() {
        grant();
        Submitted submitted = submit("1.0.0");

        new TransactionTemplate(transactions).executeWithoutResult(status ->
                adminConsole().transferOwnership(slug, publisher, platformAdmin));
        complete(submitted, SecurityVerdict.SAFE);

        assertThat(members.findByNamespaceIdAndUserId(namespaceId, publisher).orElseThrow().getRole())
                .isEqualTo(NamespaceRole.OWNER);
        assertHandedOffWithoutPublication(submitted);
        assertThat(grantChangeAudits()).hasSize(2).last()
                .satisfies(log -> assertThat(log.getActorUserId()).isEqualTo(platformAdmin));
    }

    @Test
    void permanentScanDispatchFailureHandsOffAndRetryNeverAutoApproves() {
        grant();
        Submitted submitted = submit("1.0.0");
        org.mockito.Mockito.doThrow(new RuntimeException("broker down")).when(scanTaskProducer).publishScanTask(any());
        var dispatcher = new com.iflytek.skillhub.task.ScanTaskOutboxDispatcher(
                outboxes, scanTaskProducer, versions, Clock.systemUTC(), 50, 1,
                java.time.Duration.ofMinutes(2), java.time.Duration.ofMinutes(5));
        dispatcher.setScanFailures(scanning);

        new TransactionTemplate(transactions).executeWithoutResult(status -> dispatcher.dispatch());

        ReviewTask failed = reviews.findById(submitted.reviewId()).orElseThrow();
        assertThat(version(submitted.versionId()).getStatus()).isEqualTo(SkillVersionStatus.SCAN_FAILED);
        assertThat(failed.getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
        assertThat(failed.isAutoPublicationCancelled()).isTrue();
        assertThat(failed.isManualReviewNotified()).isTrue();
        assertThat(recorder.count(ReviewSubmittedEvent.class)).isEqualTo(1);

        org.mockito.Mockito.reset(scanTaskProducer);
        String retryTask = new TransactionTemplate(transactions).execute(status -> scanning.retryStoredBundleScan(
                version(submitted.versionId()), "packages/x/bundle.zip", publisher).taskId());
        complete(new Submitted(submitted.skillId(), submitted.versionId(), submitted.reviewId(), retryTask),
                SecurityVerdict.SAFE);

        ReviewTask manual = reviews.findById(submitted.reviewId()).orElseThrow();
        assertThat(manual.getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
        assertThat(manual.getDecisionMethod()).isNull();
        assertThat(version(submitted.versionId()).getStatus()).isEqualTo(SkillVersionStatus.PENDING_REVIEW);
        assertThat(recorder.count(SkillPublishedEvent.class)).isZero();
        assertThat(recorder.count(ReviewSubmittedEvent.class)).isEqualTo(1);
    }

    private com.iflytek.skillhub.service.AdminNamespaceAppService adminConsole() {
        return new com.iflytek.skillhub.service.AdminNamespaceAppService(
                null, null, null, memberService, members, namespaces, null, new NamespaceAccessPolicy(), users);
    }

    private void assertHandedOffWithoutPublication(Submitted submitted) {
        ReviewTask task = reviews.findById(submitted.reviewId()).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
        assertThat(task.getDecisionMethod()).isNull();
        assertThat(task.isAutoPublicationCancelled()).isTrue();
        assertThat(task.isManualReviewNotified()).isTrue();
        assertThat(version(submitted.versionId()).getStatus()).isEqualTo(SkillVersionStatus.PENDING_REVIEW);
        assertThat(recorder.count(SkillPublishedEvent.class)).isZero();
        assertThat(recorder.count(ReviewSubmittedEvent.class)).isEqualTo(1);
    }

    private List<com.iflytek.skillhub.domain.audit.AuditLog> grantChangeAudits() {
        return auditLogs.findAll().stream()
                .filter(log -> "TEAM_AUTO_PUBLICATION_CHANGE".equals(log.getAction()))
                .filter(log -> log.getDetailJson().contains(publisher))
                .sorted(java.util.Comparator.comparing(com.iflytek.skillhub.domain.audit.AuditLog::getId))
                .toList();
    }

    private void grant() {
        memberService.setTeamAutoPublication(namespaceId, publisher, true, owner);
        recorder.events.clear();
    }

    private Submitted submit(String version) {
        return new TransactionTemplate(transactions).execute(status -> {
            var result = publishing.publishFromEntries(slug, entries(skillMd(version)), publisher,
                    SkillVisibility.NAMESPACE_ONLY, Set.of());
            Long versionId = result.version().getId();
            ReviewTask task = reviews.findBySkillVersionIdAndStatus(versionId, ReviewTaskStatus.PENDING).orElseThrow();
            SecurityAudit audit = audits.findLatestActiveByVersionIdAndScannerType(versionId, ScannerType.SKILL_SCANNER)
                    .orElseThrow();
            return new Submitted(result.skillId(), versionId, task.getId(), audit.getTaskId());
        });
    }

    private void complete(Submitted submitted, SecurityVerdict verdict) {
        scanning.processScanResult(submitted.scanTaskId(), submitted.versionId(), ScannerType.SKILL_SCANNER,
                new SecurityScanResponse("controlled-" + submitted.scanTaskId(), verdict,
                        verdict == SecurityVerdict.SAFE ? 0 : 1, null, List.of(), 0.1));
    }

    private SkillVersion version(Long id) {
        return versions.findById(id).orElseThrow();
    }

    private static String skillMd(String version) {
        return "---\nname: auto-sample\ndescription: Example\nversion: " + version + "\n---\nBody";
    }

    private static List<PackageEntry> entries(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return List.of(new PackageEntry("SKILL.md", bytes, bytes.length, "text/markdown"));
    }

    private record Submitted(Long skillId, Long versionId, Long reviewId, String scanTaskId) {}
}
