package com.iflytek.skillhub.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.iflytek.skillhub.domain.audit.AuditLogRepository;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.ReviewPermissionChecker;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.suite.SkillSuiteActionContext;
import com.iflytek.skillhub.domain.suite.SkillSuiteLifecycleService;
import com.iflytek.skillhub.domain.suite.SkillSuiteMemberSelection;
import com.iflytek.skillhub.domain.suite.SkillSuitePublicationValidator;
import com.iflytek.skillhub.domain.suite.SkillSuiteRepository;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersion;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionMember;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionMemberRepository;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionRepository;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionStatus;
import com.iflytek.skillhub.domain.user.UserAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Import({SkillSuiteLifecycleService.class, ReviewPermissionChecker.class,
        AuditLogService.class, SkillSuiteHardDeleteIntegrationTest.ClockConfiguration.class})
@TestPropertySource(properties = "skillhub.suite.review-writes-enabled=true")
class SkillSuiteHardDeleteIntegrationTest {

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

    @Autowired private TestEntityManager entityManager;
    @SpyBean private SkillSuiteRepository suiteRepository;
    @Autowired private SkillSuiteVersionRepository suiteVersionRepository;
    @Autowired private SkillSuiteVersionMemberRepository suiteMemberRepository;
    @Autowired private ReviewTaskRepository reviewTaskRepository;
    @Autowired private NamespaceRepository namespaceRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private SkillSuiteLifecycleService service;

    @MockBean private SkillSuitePublicationValidator publicationValidator;
    @SpyBean private AuditLogService auditLogService;

    private void persistUsers(String ownerId, String authorId) {
        entityManager.persist(new UserAccount(ownerId, "Owner", null, null));
        entityManager.persist(new UserAccount(authorId, "Author", null, null));
        entityManager.flush();
    }

    @Test
    void hardDeleteRemovesOnlySuiteOwnedGraphAndKeepsAuditAndMemberSkill() {
        persistUsers("owner", "author");
        Namespace namespace = entityManager.persistFlushFind(
                new Namespace("delete-suite-team", "Delete Suite Team", "owner"));
        Skill memberSkill = entityManager.persistFlushFind(
                new Skill(namespace.getId(), "member-skill", "author", SkillVisibility.PUBLIC));
        SkillVersion memberVersion = entityManager.persistFlushFind(
                new SkillVersion(memberSkill.getId(), "1.0.0", "author"));

        SuiteGraph target = persistSuiteGraph(
                namespace, memberSkill, memberVersion, "target-suite", "author");
        SuiteGraph retained = persistSuiteGraph(
                namespace, memberSkill, memberVersion, "retained-suite", "author");
        ReviewTask skillReview = new ReviewTask(
                memberVersion.getId(), memberSkill.getId(), namespace.getId(), "1.0.0", "author");
        skillReview.setStatus(ReviewTaskStatus.APPROVED);
        skillReview = entityManager.persistFlushFind(skillReview);
        entityManager.flush();

        service.delete(target.suite().getId(), ownerContext(namespace.getId(), "owner"));
        entityManager.flush();
        entityManager.clear();

        assertThat(suiteRepository.findById(target.suite().getId())).isEmpty();
        assertThat(suiteVersionRepository.findById(target.version().getId())).isEmpty();
        assertThat(suiteMemberRepository.findBySuiteVersionIdOrderByPosition(target.version().getId())).isEmpty();
        assertThat(reviewTaskRepository.findById(target.reviewTask().getId())).isEmpty();

        assertThat(suiteRepository.findById(retained.suite().getId())).isPresent();
        assertThat(suiteMemberRepository.findBySuiteVersionIdOrderByPosition(retained.version().getId()))
                .extracting(SkillSuiteVersionMember::getId)
                .containsExactly(retained.member().getId());
        assertThat(reviewTaskRepository.findById(retained.reviewTask().getId())).isPresent();
        assertThat(reviewTaskRepository.findById(skillReview.getId())).isPresent();
        assertThat(entityManager.find(Skill.class, memberSkill.getId())).isNotNull();
        assertThat(entityManager.find(SkillVersion.class, memberVersion.getId())).isNotNull();

        assertThat(auditLogRepository.search(
                "owner", "DELETE_SKILL_SUITE", PageRequest.of(0, 10))).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("DELETE_SKILL_SUITE");
            assertThat(log.getTargetType()).isEqualTo("SKILL_SUITE");
            assertThat(log.getTargetId()).isEqualTo(target.suite().getId());
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void hardDeleteRollsBackReviewCleanupWhenSuiteDeleteFails() {
        RollbackFixture fixture = transactionTemplate.execute(status -> {
            persistUsers("rollback-owner", "rollback-author");
            Namespace namespace = entityManager.persistFlushFind(
                    new Namespace("rollback-suite-team", "Rollback Suite Team", "rollback-owner"));
            Skill memberSkill = entityManager.persistFlushFind(
                    new Skill(namespace.getId(), "rollback-member", "rollback-author", SkillVisibility.PUBLIC));
            SkillVersion memberVersion = entityManager.persistFlushFind(
                    new SkillVersion(memberSkill.getId(), "1.0.0", "rollback-author"));
            SuiteGraph graph = persistSuiteGraph(
                    namespace, memberSkill, memberVersion, "rollback-suite", "rollback-author");
            return new RollbackFixture(
                    namespace.getId(), graph.suite().getId(), graph.version().getId(),
                    graph.member().getId(), graph.reviewTask().getId());
        });

        doThrow(new DataIntegrityViolationException("forced Suite delete failure"))
                .when(suiteRepository).delete(argThat(
                        suite -> suite.getId().equals(fixture.suiteId())));

        assertThatThrownBy(() -> service.delete(
                fixture.suiteId(), ownerContext(fixture.namespaceId(), "rollback-owner")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(suiteRepository.findById(fixture.suiteId())).isPresent();
        assertThat(suiteVersionRepository.findById(fixture.versionId())).isPresent();
        assertThat(suiteMemberRepository.findBySuiteVersionIdOrderByPosition(fixture.versionId()))
                .extracting(SkillSuiteVersionMember::getId)
                .containsExactly(fixture.memberId());
        assertThat(reviewTaskRepository.findById(fixture.reviewTaskId())).isPresent();
        assertThat(auditLogRepository.search(
                "rollback-owner", "DELETE_SKILL_SUITE", PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void rejectedSuiteCanBeReopenedAndResubmittedWithoutLosingReviewHistory() {
        persistUsers("review-owner", "review-author");
        Namespace namespace = entityManager.persistFlushFind(
                new Namespace("review-history-team", "Review History Team", "review-owner"));
        SkillSuite suite = entityManager.persistFlushFind(
                new SkillSuite(namespace.getId(), "review-history-suite", "Review History Suite", "review-author"));
        SkillSuiteVersion version = new SkillSuiteVersion(
                suite.getId(), "1.0.0", SkillVisibility.PUBLIC, "review-author");
        version.setStatus(SkillSuiteVersionStatus.PENDING_REVIEW);
        version = entityManager.persistFlushFind(version);
        ReviewTask firstRound = entityManager.persistFlushFind(ReviewTask.forSuiteVersion(
                version.getId(), suite.getId(), namespace.getId(), version.getVersion(), "review-author"));

        service.rejectReview(
                firstRound.getId(), "Please update the member description",
                ownerContext(namespace.getId(), "review-owner"));
        service.reopenRejected(
                suite.getId(), version.getId(), authorContext(namespace.getId(), "review-author"));
        ReviewTask secondRound = service.submitForReview(
                suite.getId(), version.getId(), authorContext(namespace.getId(), "review-author"));
        entityManager.flush();
        entityManager.clear();

        assertThat(reviewTaskRepository
                .findBySubmittedByAndSubjectTypeAndSubjectIdAndSubjectVersionOrderBySubmittedAtDescIdDesc(
                        "review-author", firstRound.getSubjectType(), suite.getId(), version.getVersion()))
                .satisfiesExactly(
                        review -> {
                            assertThat(review.getId()).isEqualTo(secondRound.getId());
                            assertThat(review.getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
                        },
                        review -> {
                            assertThat(review.getId()).isEqualTo(firstRound.getId());
                            assertThat(review.getStatus()).isEqualTo(ReviewTaskStatus.REJECTED);
                            assertThat(review.getReviewedBy()).isEqualTo("review-owner");
                            assertThat(review.getReviewComment()).isEqualTo("Please update the member description");
                        });
        assertThat(suiteVersionRepository.findById(version.getId()))
                .get()
                .extracting(SkillSuiteVersion::getStatus)
                .isEqualTo(SkillSuiteVersionStatus.PENDING_REVIEW);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void approveReviewRollsBackTheDecisionAndPublicationWhenAuditFails() {
        ApprovalRollbackFixture fixture = transactionTemplate.execute(status -> {
            persistUsers("approval-owner", "approval-author");
            Namespace namespace = entityManager.persistFlushFind(
                    new Namespace("approval-rollback-team", "Approval Rollback Team", "approval-owner"));
            SkillSuite suite = entityManager.persistFlushFind(
                    new SkillSuite(namespace.getId(), "approval-rollback-suite", "Approval Suite", "approval-author"));
            SkillSuiteVersion version = new SkillSuiteVersion(
                    suite.getId(), "1.0.0", SkillVisibility.PUBLIC, "approval-author");
            version.setStatus(SkillSuiteVersionStatus.PENDING_REVIEW);
            version = entityManager.persistFlushFind(version);
            ReviewTask review = entityManager.persistFlushFind(ReviewTask.forSuiteVersion(
                    version.getId(), suite.getId(), namespace.getId(), version.getVersion(), "approval-author"));
            return new ApprovalRollbackFixture(namespace.getId(), suite.getId(), version.getId(), review.getId());
        });

        doThrow(new IllegalStateException("forced audit failure after publication"))
                .when(auditLogService).record(
                        eq("approval-owner"), eq("APPROVE_SKILL_SUITE_REVIEW"),
                        eq("REVIEW_TASK"), eq(fixture.reviewTaskId()),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> service.approveReview(
                fixture.reviewTaskId(), "Approved",
                ownerContext(fixture.namespaceId(), "approval-owner")))
                .isInstanceOf(IllegalStateException.class);

        transactionTemplate.executeWithoutResult(status -> {
            ReviewTask review = reviewTaskRepository.findById(fixture.reviewTaskId()).orElseThrow();
            SkillSuiteVersion version = suiteVersionRepository.findById(fixture.versionId()).orElseThrow();
            SkillSuite suite = suiteRepository.findById(fixture.suiteId()).orElseThrow();
            assertThat(review.getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
            assertThat(review.getReviewedBy()).isNull();
            assertThat(version.getStatus()).isEqualTo(SkillSuiteVersionStatus.PENDING_REVIEW);
            assertThat(suite.getLatestVersionId()).isNull();
            assertThat(auditLogRepository.search(
                    "approval-owner", "APPROVE_SKILL_SUITE_REVIEW", PageRequest.of(0, 10))).isEmpty();
        });
    }

    private SuiteGraph persistSuiteGraph(
            Namespace namespace,
            Skill memberSkill,
            SkillVersion memberVersion,
            String slug,
            String authorId) {
        SkillSuite suite = entityManager.persistFlushFind(
                new SkillSuite(namespace.getId(), slug, slug, authorId));
        SkillSuiteVersion version = new SkillSuiteVersion(
                suite.getId(), "1.0.0", SkillVisibility.PUBLIC, authorId);
        version.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        version = entityManager.persistFlushFind(version);
        SkillSuiteVersionMember member = entityManager.persistFlushFind(new SkillSuiteVersionMember(
                version.getId(),
                new SkillSuiteMemberSelection(
                        memberSkill.getId(), memberVersion.getId(), namespace.getSlug(),
                        memberSkill.getSlug(), memberVersion.getVersion(), "a".repeat(64)),
                0,
                true));
        ReviewTask reviewTask = ReviewTask.forSuiteVersion(
                version.getId(), suite.getId(), namespace.getId(), version.getVersion(), authorId);
        reviewTask.setStatus(ReviewTaskStatus.APPROVED);
        reviewTask = entityManager.persistFlushFind(reviewTask);
        return new SuiteGraph(suite, version, member, reviewTask);
    }

    private SkillSuiteActionContext ownerContext(Long namespaceId, String ownerId) {
        return new SkillSuiteActionContext(
                ownerId,
                Map.of(namespaceId, NamespaceRole.OWNER),
                Set.of(),
                "delete-suite-request",
                "127.0.0.1",
                "integration-test");
    }

    private SkillSuiteActionContext authorContext(Long namespaceId, String authorId) {
        return new SkillSuiteActionContext(
                authorId,
                Map.of(namespaceId, NamespaceRole.MEMBER),
                Set.of(),
                "suite-review-request",
                "127.0.0.1",
                "integration-test");
    }

    private record SuiteGraph(
            SkillSuite suite,
            SkillSuiteVersion version,
            SkillSuiteVersionMember member,
            ReviewTask reviewTask) {}

    private record RollbackFixture(
            Long namespaceId,
            Long suiteId,
            Long versionId,
            Long memberId,
            Long reviewTaskId) {}

    private record ApprovalRollbackFixture(
            Long namespaceId,
            Long suiteId,
            Long versionId,
            Long reviewTaskId) {}

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-09-08T06:00:00Z"), ZoneOffset.UTC);
        }
    }
}
