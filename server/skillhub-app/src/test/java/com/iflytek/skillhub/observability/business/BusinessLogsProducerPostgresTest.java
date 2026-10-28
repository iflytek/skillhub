package com.iflytek.skillhub.observability.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.governance.GovernanceNotificationService;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.review.*;
import com.iflytek.skillhub.domain.security.*;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.skill.metadata.*;
import com.iflytek.skillhub.domain.skill.service.*;
import com.iflytek.skillhub.domain.skill.validation.*;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.infra.jpa.*;
import com.iflytek.skillhub.storage.ObjectMetadata;
import com.iflytek.skillhub.storage.ObjectStorageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.resources.Resource;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/** Real service proxies, JPA repositories, schema migrations, commit/rollback and analytics adapter. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Import({SkillOwnerAccessPolicy.class, JpaSkillRepositoryAdapter.class, SkillPublishService.class,
        SkillReviewSubmitService.class, SkillGovernanceService.class, SkillVisibilityService.class,
        SkillDownloadService.class, SkillSlugResolutionService.class, ReviewService.class, PromotionService.class,
        ReviewPermissionChecker.class, BusinessLogsProducerPostgresTest.Configuration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BusinessLogsProducerPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"))
            .withCreateContainerCmdModifier(cmd -> cmd.withName("skillhub-1009-producers-postgres"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @TestConfiguration
    static class Configuration {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean VisibilityChecker visibilityChecker() { return new VisibilityChecker(); }
        @Bean BusinessLogsTransactionTest.CapturingExporter exporter() { return new BusinessLogsTransactionTest.CapturingExporter(); }
        @Bean BusinessLogsMetrics metrics() { return new BusinessLogsMetrics(new SimpleMeterRegistry()); }
        @Bean(destroyMethod = "close") BusinessLogsPipeline pipeline(BusinessLogsTransactionTest.CapturingExporter exporter, BusinessLogsMetrics metrics) {
            return new BusinessLogsPipeline(new BusinessLogsProperties(), Resource.empty(), exporter, metrics);
        }
        @Bean BusinessEventListener listener(BusinessLogsPipeline pipeline, BusinessLogsMetrics metrics) {
            return new BusinessEventListener(new BusinessEventMapper(new BusinessLogsProperties()), pipeline, metrics);
        }
    }

    @MockBean SecurityScanService scanner;
    @MockBean GovernanceNotificationService notifications;
    @MockBean ReviewAvatarParticipant avatars;
    @MockBean ObjectStorageService storage;
    @MockBean SkillStorageDeletionCompensationService compensation;
    @MockBean AuditLogService audit;
    @MockBean SkillPackageValidator validator;
    @MockBean SkillMetadataParser parser;
    @MockBean PrePublishValidator prePublish;
    @Autowired SkillPublishService publishing;
    @Autowired SkillReviewSubmitService confirmations;
    @Autowired ReviewService reviewing;
    @Autowired PromotionService promoting;
    @Autowired SkillGovernanceService governance;
    @Autowired SkillVisibilityService visibility;
    @Autowired SkillDownloadService downloads;
    @Autowired SkillJpaRepository skills;
    @Autowired SkillVersionJpaRepository versions;
    @Autowired ReviewTaskJpaRepository reviews;
    @Autowired PromotionRequestJpaRepository promotions;
    @Autowired NamespaceJpaRepository namespaces;
    @Autowired NamespaceMemberJpaRepository members;
    @Autowired UserAccountJpaRepository users;
    @Autowired SecurityAuditJpaRepository audits;
    @Autowired PlatformTransactionManager transactions;
    @Autowired BusinessLogsPipeline pipeline;
    @Autowired BusinessLogsTransactionTest.CapturingExporter exporter;

    @BeforeEach
    void setup() {
        pipeline.forceFlush().join(5, TimeUnit.SECONDS);
        exporter.records.clear();
        when(scanner.isEnabled()).thenReturn(true);
        when(validator.validate(anyList())).thenReturn(ValidationResult.pass());
        when(prePublish.validate(any())).thenReturn(ValidationResult.pass());
        when(parser.parse(anyString())).thenReturn(new SkillMetadata("business-probe", "Never export title", "3.0.0", "Never export body", Map.of()));
        when(storage.exists(anyString())).thenReturn(true);
        when(storage.getMetadata(anyString())).thenReturn(new ObjectMetadata(12, "application/zip", Instant.now()));
        when(storage.generatePresignedUrl(anyString(), any(), anyString())).thenReturn("https://private.invalid/?signed=secret");
    }

    static Stream<Arguments> paths() {
        return Stream.of("auto", "private", "review-approved", "review-rejected", "promotion-initial",
                "promotion-update", "promotion-rejected", "archive", "unarchive", "visibility-narrow",
                "visibility-expanded", "yank", "download").flatMap(path -> Stream.of(Arguments.of(path, false), Arguments.of(path, true)));
    }

    @ParameterizedTest(name = "{0}, rollback={1}")
    @MethodSource("paths")
    void producerFactIsExactlyDocumentedAndOnlyAfterRealCommit(String path, boolean rollback) {
        Fixture fixture = fixture(path);
        List<String> expected = expected(path);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            switch (path) {
                case "auto" -> publishing.publishFromEntries(fixture.namespaceSlug(),
                        List.of(new PackageEntry("SKILL.md", "content".getBytes(java.nio.charset.StandardCharsets.UTF_8), 7, "text/markdown")),
                        fixture.owner(), SkillVisibility.PUBLIC, Set.of("SUPER_ADMIN"));
                case "private" -> confirmations.confirmPublish(fixture.skillId(), fixture.uploadedId(), fixture.owner(), Map.of());
                case "review-approved" -> reviewing.approveReview(fixture.reviewId(), fixture.reviewer(), "checked", Map.of(), Set.of("SUPER_ADMIN"));
                case "review-rejected" -> reviewing.rejectReview(fixture.reviewId(), fixture.reviewer(), "private reason", Map.of(), Set.of("SUPER_ADMIN"));
                case "promotion-initial", "promotion-update" -> promoting.approvePromotion(fixture.promotionId(), fixture.reviewer(), "checked", Set.of("SUPER_ADMIN"), Set.of());
                case "promotion-rejected" -> promoting.rejectPromotion(fixture.promotionId(), fixture.reviewer(), "private reason", Set.of("SUPER_ADMIN"));
                case "archive" -> governance.archiveSkill(fixture.skillId(), fixture.owner(), Map.of(), "not-exported-ip", "not-exported-ua", "private reason");
                case "unarchive" -> governance.unarchiveSkill(fixture.skillId(), fixture.owner(), Map.of(), "not-exported-ip", "not-exported-ua");
                case "visibility-narrow" -> visibility.change(fixture.skillId(), SkillVisibility.PRIVATE, fixture.owner(), Map.of(), Set.of());
                case "visibility-expanded" -> reviewing.approveReview(fixture.reviewId(), fixture.reviewer(), "checked", Map.of(), Set.of("SUPER_ADMIN"));
                case "yank" -> governance.yankVersion(fixture.publishedId(), fixture.reviewer(), "not-exported-ip", "not-exported-ua", "private reason");
                case "download" -> assertThat(downloads.downloadVersion(fixture.namespaceSlug(), "business-probe", "1.0.0", null, Map.of()).presignedUrl()).contains("signed=secret");
                default -> throw new AssertionError(path);
            }
            assertThat(exporter.records).isEmpty();
            if (rollback) { status.setRollbackOnly(); }
        });
        pipeline.forceFlush().join(5, TimeUnit.SECONDS);
        assertThat(exporter.records).extracting(r -> r.getAttributes().get(AttributeKey.stringKey("skillhub.event.name")))
                .containsExactlyInAnyOrderElementsOf(rollback ? List.of() : expected);
        if (!rollback) {
            for (var record : exporter.records) {
                assertThat(record.getAttributes().toString()).doesNotContain("private reason", "not-exported", "secret", "business-probe", "Never export");
                String actor = record.getAttributes().get(AttributeKey.stringKey("user.id"));
                if (path.equals("download")) { assertThat(actor).isNull(); }
                else if (path.startsWith("review") || path.startsWith("promotion") || path.equals("visibility-expanded") || path.equals("yank")) {
                    assertThat(actor).isEqualTo(fixture.reviewer());
                } else { assertThat(actor).isEqualTo(fixture.owner()); }
            }
        }
        // Persistent state independently proves rollback, not just listener suppression.
        if (path.equals("private")) {
            assertThat(((SkillVersionRepository) versions).findById(fixture.uploadedId()).orElseThrow().getStatus())
                    .isEqualTo(rollback ? SkillVersionStatus.UPLOADED : SkillVersionStatus.PUBLISHED);
        }
        if (path.equals("review-approved")) {
            assertThat(((ReviewTaskRepository) reviews).findById(fixture.reviewId()).orElseThrow().getStatus())
                    .isEqualTo(rollback ? ReviewTaskStatus.PENDING : ReviewTaskStatus.APPROVED);
        }
        System.out.printf("BUSINESS_LOGS producer=%s rollback=%s received=%d expected=%d%n", path, rollback, exporter.records.size(), rollback ? 0 : expected.size());
    }

    private List<String> expected(String path) {
        return switch (path) {
            case "auto", "private" -> List.of("skill.publication.effective");
            case "review-approved" -> List.of("skill.publication.effective", "skill.review.approved");
            case "review-rejected" -> List.of("skill.review.rejected");
            case "promotion-initial", "promotion-update" -> List.of("skill.publication.effective", "skill.promotion.approved");
            case "promotion-rejected" -> List.of("skill.promotion.rejected");
            case "archive", "unarchive" -> List.of("skill.status.changed");
            case "visibility-narrow", "visibility-expanded" -> List.of("skill.visibility.changed");
            case "yank" -> List.of("skill.version.yanked");
            case "download" -> List.of("skill.download.prepared");
            default -> throw new AssertionError(path);
        };
    }

    static Stream<String> unauthorizedPaths() {
        return Stream.of("auto", "private", "review-approved", "review-rejected", "promotion-initial",
                "promotion-update", "promotion-rejected", "archive", "unarchive", "visibility-narrow",
                "visibility-expanded", "yank", "download");
    }

    @ParameterizedTest(name = "unauthorized {0}")
    @MethodSource("unauthorizedPaths")
    void deniedProducerPathsPersistNoMutationAndEmitNoFact(String path) {
        Fixture fixture = fixture(path);
        if (path.equals("download")) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                Skill skill = ((SkillRepository) skills).findById(fixture.skillId()).orElseThrow();
                skill.setVisibility(SkillVisibility.PRIVATE);
                skills.saveAndFlush(skill);
            });
        }
        Skill before = ((SkillRepository) skills).findById(fixture.skillId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            switch (path) {
                case "auto" -> publishing.publishFromEntries(fixture.namespaceSlug(),
                        List.of(new PackageEntry("SKILL.md", new byte[]{1}, 1, "text/markdown")),
                        fixture.reviewer(), SkillVisibility.PUBLIC, Set.of());
                case "private" -> confirmations.confirmPublish(fixture.skillId(), fixture.uploadedId(), fixture.reviewer(), Map.of());
                case "review-approved", "visibility-expanded" -> reviewing.approveReview(fixture.reviewId(), fixture.reviewer(), "checked", Map.of(), Set.of());
                case "review-rejected" -> reviewing.rejectReview(fixture.reviewId(), fixture.reviewer(), "reason", Map.of(), Set.of());
                case "promotion-initial", "promotion-update" -> promoting.approvePromotion(fixture.promotionId(), fixture.reviewer(), "checked", Set.of(), Set.of());
                case "promotion-rejected" -> promoting.rejectPromotion(fixture.promotionId(), fixture.reviewer(), "reason", Set.of());
                case "archive" -> governance.archiveSkill(fixture.skillId(), fixture.reviewer(), Map.of(), null, null, "reason");
                case "unarchive" -> governance.unarchiveSkill(fixture.skillId(), fixture.reviewer(), Map.of(), null, null);
                case "visibility-narrow" -> visibility.change(fixture.skillId(), SkillVisibility.PRIVATE, fixture.reviewer(), Map.of(), Set.of());
                case "yank" -> governance.yankVersion(before, ((SkillVersionRepository) versions).findById(fixture.publishedId()).orElseThrow(),
                        fixture.reviewer(), Map.of(), null, null, "reason");
                case "download" -> downloads.downloadVersion(fixture.namespaceSlug(), "business-probe", "1.0.0", null, Map.of());
                default -> throw new AssertionError(path);
            }
        }).isInstanceOfAny(com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException.class,
                com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);
        pipeline.forceFlush().join(5, TimeUnit.SECONDS);
        assertThat(exporter.records).isEmpty();
        Skill after = ((SkillRepository) skills).findById(fixture.skillId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(before.getStatus());
        assertThat(after.getVisibility()).isEqualTo(before.getVisibility());
        assertThat(((SkillVersionRepository) versions).findById(fixture.publishedId()).orElseThrow().getStatus()).isEqualTo(SkillVersionStatus.PUBLISHED);
        if (fixture.reviewId() != null) {
            assertThat(((ReviewTaskRepository) reviews).findById(fixture.reviewId()).orElseThrow().getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
        }
        if (fixture.promotionId() != null) {
            assertThat(((PromotionRequestRepository) promotions).findById(fixture.promotionId()).orElseThrow().getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
        }
        System.out.printf("BUSINESS_LOGS denied producer=%s received=0; persisted state unchanged%n", path);
    }

    private Fixture fixture(String path) {
        return new TransactionTemplate(transactions).execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String owner = "business-owner-" + suffix;
            String reviewer = "business-reviewer-" + suffix;
            users.saveAndFlush(new UserAccount(owner, owner, null, null));
            users.saveAndFlush(new UserAccount(reviewer, reviewer, null, null));
            Namespace namespace = namespaces.saveAndFlush(new Namespace("business-" + suffix, "Never export namespace", owner));
            members.saveAndFlush(new NamespaceMember(namespace.getId(), owner, NamespaceRole.OWNER));
            SkillVisibility scope = path.equals("private") || path.equals("visibility-expanded") ? SkillVisibility.PRIVATE : SkillVisibility.PUBLIC;
            Skill skill = skills.saveAndFlush(new Skill(namespace.getId(), "business-probe", owner, scope));
            SkillVersion published = new SkillVersion(skill.getId(), "1.0.0", owner);
            published.setStatus(SkillVersionStatus.PUBLISHED);
            published.setBundleReady(true);
            published.setDownloadReady(true);
            published.setPublishedAt(Instant.now());
            published = versions.saveAndFlush(published);
            skill.setLatestVersionId(published.getId());
            if (path.equals("unarchive")) { skill.setStatus(SkillStatus.ARCHIVED); }
            skills.saveAndFlush(skill);
            SkillVersion uploaded = new SkillVersion(skill.getId(), "2.0.0", owner);
            uploaded.setStatus(path.startsWith("review") ? SkillVersionStatus.PENDING_REVIEW : SkillVersionStatus.UPLOADED);
            uploaded = versions.saveAndFlush(uploaded);
            Long reviewId = null;
            if (path.startsWith("review")) {
                reviewId = reviews.saveAndFlush(new ReviewTask(uploaded.getId(), skill.getId(), namespace.getId(), uploaded.getVersion(), owner)).getId();
            }
            if (path.equals("visibility-expanded")) {
                SecurityAudit scan = new SecurityAudit(published.getId(), ScannerType.SKILL_SCANNER, "scan-" + suffix);
                scan.setIsSafe(true); scan.setScannedAt(Instant.now()); audits.saveAndFlush(scan);
                var task = ReviewTask.forVisibility(skill, SkillVisibility.PUBLIC, owner);
                task.setVisibilityScanTaskId(scan.getTaskId());
                reviewId = reviews.saveAndFlush(task).getId();
            }
            Long promotionId = null;
            if (path.startsWith("promotion")) {
                Namespace targetNamespace = new Namespace("global-" + suffix, "Never export global name", owner);
                targetNamespace.setType(NamespaceType.GLOBAL);
                targetNamespace = namespaces.saveAndFlush(targetNamespace);
                PromotionRequest request = new PromotionRequest(skill.getId(), published.getId(), targetNamespace.getId(), owner);
                if (path.equals("promotion-update")) {
                    Skill target = new Skill(targetNamespace.getId(), skill.getSlug(), owner, SkillVisibility.PUBLIC);
                    target.setSourceSkillId(skill.getId()); target = skills.saveAndFlush(target);
                    SkillVersion old = new SkillVersion(target.getId(), "0.9.0", owner);
                    old.setStatus(SkillVersionStatus.PUBLISHED); old = versions.saveAndFlush(old);
                    target.setLatestVersionId(old.getId()); skills.saveAndFlush(target);
                    PromotionRequest initial = new PromotionRequest(skill.getId(), published.getId(), targetNamespace.getId(), owner);
                    initial.setStatus(ReviewTaskStatus.APPROVED); initial.setTargetSkillId(target.getId()); initial.setTargetVersionId(old.getId());
                    promotions.saveAndFlush(initial);
                    request.setRequestKind(PromotionRequestKind.UPDATE); request.setTargetSkillId(target.getId());
                }
                promotionId = promotions.saveAndFlush(request).getId();
            }
            return new Fixture(skill.getId(), published.getId(), uploaded.getId(), reviewId, promotionId, namespace.getSlug(), owner, reviewer);
        });
    }

    private record Fixture(Long skillId, Long publishedId, Long uploadedId, Long reviewId, Long promotionId,
                           String namespaceSlug, String owner, String reviewer) {}
}
