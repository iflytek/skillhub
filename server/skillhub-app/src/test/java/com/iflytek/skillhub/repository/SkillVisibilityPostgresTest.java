package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.review.*;
import static org.mockito.Mockito.*;
import com.iflytek.skillhub.domain.security.*;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.skill.service.SkillVisibilityService;
import com.iflytek.skillhub.domain.skill.service.SkillReviewSubmitService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.infra.jpa.*;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Import({com.iflytek.skillhub.domain.skill.SkillOwnerAccessPolicy.class, SkillVisibilityService.class, SkillReviewSubmitService.class,
        JpaSkillRepositoryAdapter.class, PromotionService.class, ReviewPermissionChecker.class, SkillVisibilityPostgresTest.Configuration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SkillVisibilityPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
    @TestConfiguration static class Configuration { @Bean Clock clock() { return Clock.systemUTC(); } @Bean com.fasterxml.jackson.databind.ObjectMapper mapper() { return new com.fasterxml.jackson.databind.ObjectMapper(); } }
    @MockBean SecurityScanService scanner;
    @MockBean com.iflytek.skillhub.domain.governance.GovernanceNotificationService notifications;
    @org.springframework.boot.test.mock.mockito.SpyBean JpaSkillRepositoryAdapter skillAdapter;
    @Autowired PromotionService promotions;
    @Autowired PromotionRequestJpaRepository promotionRows;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired SkillVisibilityService visibility;
    @Autowired SkillReviewSubmitService submissions;
    @Autowired SkillJpaRepository skills;
    @Autowired SkillVersionJpaRepository versions;
    @Autowired NamespaceJpaRepository namespaces;
    @Autowired NamespaceMemberJpaRepository members;
    @Autowired UserAccountJpaRepository users;
    @Autowired SecurityAuditJpaRepository audits;
    @Autowired ReviewTaskJpaRepository reviews;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void concurrentScopeSubmissionsPersistOnlyOnePendingRequest() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> change(start, fixture));
            var two = pool.submit(() -> change(start, fixture));
            start.countDown();
            assertThat(one.get(30, TimeUnit.SECONDS) + two.get(30, TimeUnit.SECONDS)).isEqualTo(1);
        }
        var pending = reviews.findBySkillIdAndRequestedVisibilityIsNotNullAndStatus(fixture.skillId(), ReviewTaskStatus.PENDING).orElseThrow();
        assertThat(pending.getPreviousVisibility()).isEqualTo(SkillVisibility.PRIVATE);
        assertThat(pending.getRequestedVisibility()).isEqualTo(SkillVisibility.PUBLIC);
        assertThat(pending.getVisibilityVersionId()).isEqualTo(fixture.publishedId());
        assertThat(((SkillRepository) skills).findById(fixture.skillId()).orElseThrow().getVisibility()).isEqualTo(SkillVisibility.PRIVATE);
        assertThat(((SkillVersionRepository) versions).findById(fixture.publishedId()).orElseThrow().getStatus()).isEqualTo(SkillVersionStatus.PUBLISHED);
    }

    @Test
    void versionSubmissionAndScopeSubmissionAreMutuallyExclusiveAcrossTransactions() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var scope = pool.submit(() -> change(start, fixture));
            var content = pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                try {
                    submissions.submitForReview(fixture.skillId(), fixture.uploadedId(), SkillVisibility.PUBLIC, fixture.owner(), Map.of());
                    return 1;
                } catch (com.iflytek.skillhub.domain.shared.exception.DomainConflictException conflict) { return 0; }
            });
            start.countDown();
            assertThat(scope.get(30, TimeUnit.SECONDS) + content.get(30, TimeUnit.SECONDS)).isEqualTo(1);
        }
        boolean pendingScope = reviews.existsBySkillIdAndRequestedVisibilityIsNotNullAndStatus(fixture.skillId(), ReviewTaskStatus.PENDING);
        boolean pendingContent = ((SkillVersionRepository) versions).findById(fixture.uploadedId()).orElseThrow().getStatus() == SkillVersionStatus.PENDING_REVIEW;
        assertThat(pendingScope ^ pendingContent).isTrue();
    }

    @Test
    void stalePublicSearchIndexDoesNotLeakNarrowedScopeOrCounts() {
        Fixture fixture = fixture();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Skill skill = ((SkillRepository) skills).findById(fixture.skillId()).orElseThrow();
            skill.setVisibility(SkillVisibility.PUBLIC);
            skills.saveAndFlush(skill);
            Namespace ns = namespaces.getReferenceById(skill.getNamespaceId());
            jdbc.update("INSERT INTO skill_search_document (skill_id, namespace_id, namespace_slug, owner_id, title, visibility, status) VALUES (?, ?, ?, ?, ?, 'PUBLIC', 'ACTIVE')",
                    skill.getId(), ns.getId(), ns.getSlug(), fixture.owner(), "private-summary");
        });
        assertThat(search(fixture, null, Set.of()).total()).isEqualTo(1);
        visibility.change(fixture.skillId(), SkillVisibility.NAMESPACE_ONLY, fixture.owner(), Map.of(), Set.of());
        assertThat(search(fixture, null, Set.of()).skillIds()).isEmpty();
        assertThat(search(fixture, null, Set.of()).total()).isZero();
        assertThat(search(fixture, "outsider", Set.of()).total()).isZero();
        Long namespaceId = ((SkillRepository) skills).findById(fixture.skillId()).orElseThrow().getNamespaceId();
        assertThat(search(fixture, "member", Set.of(namespaceId)).total()).isEqualTo(1);
        visibility.change(fixture.skillId(), SkillVisibility.PRIVATE, fixture.owner(), Map.of(), Set.of());
        assertThat(search(fixture, "member", Set.of(namespaceId)).total()).isZero();
        assertThat(jdbc.queryForObject("SELECT visibility FROM skill_search_document WHERE skill_id = ?", String.class, fixture.skillId())).isEqualTo("PUBLIC");
    }

    private com.iflytek.skillhub.search.SearchResult search(Fixture fixture, String user, Set<Long> memberships) {
        return new TransactionTemplate(transactions).execute(status -> {
            Skill skill = ((SkillRepository) skills).findById(fixture.skillId()).orElseThrow();
            return new com.iflytek.skillhub.search.postgres.PostgresFullTextQueryService(entityManager).search(
                    new com.iflytek.skillhub.search.SearchQuery(null, skill.getNamespaceId(),
                            new com.iflytek.skillhub.search.SearchVisibilityScope(user, memberships, Set.of()),
                            "updated", 0, 10));
        });
    }

    @Test
    void promotionApprovalRefreshesItsPreloadedTargetAfterScopeNarrowing() throws Exception {
        Fixture fixture = fixture();
        PromotionFixture promotion = promotionFixture(fixture);
        CountDownLatch targetLoaded = new CountDownLatch(1);
        CountDownLatch scopeCommitted = new CountDownLatch(1);
        doAnswer(invocation -> {
            var result = invocation.callRealMethod();
            if (Thread.currentThread().getName().startsWith("scope-promotion-")) {
                targetLoaded.countDown();
                if (!scopeCommitted.await(20, TimeUnit.SECONDS)) { throw new IllegalStateException("scope commit timeout"); }
            }
            return result;
        }).when(skillAdapter).findById(promotion.targetId());
        try (var pool = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "scope-promotion-approval"))) {
            var approval = pool.submit(() -> promotions.approvePromotion(promotion.requestId(), fixture.owner(), "checked",
                    Set.of("SUPER_ADMIN"), Set.of()));
            assertThat(targetLoaded.await(20, TimeUnit.SECONDS)).isTrue();
            visibility.change(promotion.targetId(), SkillVisibility.PRIVATE, fixture.owner(), Map.of(), Set.of());
            scopeCommitted.countDown();
            approval.get(30, TimeUnit.SECONDS);
        } finally { scopeCommitted.countDown(); reset(skillAdapter); }
        Skill target = ((SkillRepository) skills).findById(promotion.targetId()).orElseThrow();
        assertThat(target.getVisibility()).isEqualTo(SkillVisibility.PRIVATE);
        assertThat(target.getLatestVersionId()).isNotEqualTo(promotion.oldVersionId());
    }

    @Test
    void pendingScopeBlocksPromotionApprovalAndRollsBackItsDecision() {
        Fixture fixture = fixture();
        PromotionFixture promotion = promotionFixture(fixture);
        visibility.change(promotion.targetId(), SkillVisibility.PRIVATE, fixture.owner(), Map.of(), Set.of());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            SecurityAudit audit = new SecurityAudit(promotion.oldVersionId(), ScannerType.SKILL_SCANNER, "target-safe-" + promotion.targetId());
            audit.setScannedAt(Instant.now()); audit.setIsSafe(true); audits.saveAndFlush(audit);
        });
        visibility.change(promotion.targetId(), SkillVisibility.PUBLIC, fixture.owner(), Map.of(), Set.of());
        assertThatThrownBy(() -> promotions.approvePromotion(promotion.requestId(), fixture.owner(), "checked", Set.of("SUPER_ADMIN"), Set.of()))
                .hasMessageContaining("error.skill.visibility.pending");
        Skill target = ((SkillRepository) skills).findById(promotion.targetId()).orElseThrow();
        assertThat(target.getVisibility()).isEqualTo(SkillVisibility.PRIVATE);
        assertThat(target.getLatestVersionId()).isEqualTo(promotion.oldVersionId());
        assertThat(((PromotionRequestRepository) promotionRows).findById(promotion.requestId()).orElseThrow().getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
    }

    private PromotionFixture promotionFixture(Fixture fixture) {
        return new TransactionTemplate(transactions).execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Namespace global = new Namespace("scope-global-" + suffix, "Global", fixture.owner());
            global.setType(com.iflytek.skillhub.domain.namespace.NamespaceType.GLOBAL);
            global = namespaces.saveAndFlush(global);
            Skill target = new Skill(global.getId(), "foo", fixture.owner(), SkillVisibility.PUBLIC);
            target.setSourceSkillId(fixture.skillId()); target = skills.saveAndFlush(target);
            SkillVersion old = new SkillVersion(target.getId(), "0.9.0", fixture.owner());
            old.setStatus(SkillVersionStatus.PUBLISHED); old.setBundleReady(true);
            old = versions.saveAndFlush(old); target.setLatestVersionId(old.getId()); skills.saveAndFlush(target);
            PromotionRequest initial = new PromotionRequest(fixture.skillId(), fixture.publishedId(), global.getId(), fixture.owner());
            initial.setStatus(ReviewTaskStatus.APPROVED); initial.setTargetSkillId(target.getId()); initial.setTargetVersionId(old.getId());
            promotionRows.saveAndFlush(initial);
            PromotionRequest update = new PromotionRequest(fixture.skillId(), fixture.publishedId(), global.getId(), fixture.owner());
            update.setRequestKind(PromotionRequestKind.UPDATE); update.setTargetSkillId(target.getId());
            update = promotionRows.saveAndFlush(update);
            return new PromotionFixture(target.getId(), old.getId(), update.getId());
        });
    }
    private record PromotionFixture(Long targetId, Long oldVersionId, Long requestId) {}

    @Test
    void rollbackDoesNotLeaveAnApprovedScopeOrPendingRequest() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            visibility.change(fixture.skillId(), SkillVisibility.PUBLIC, fixture.owner(), Map.of(), Set.of());
            throw new IllegalStateException("rollback");
        })).hasMessage("rollback");
        assertThat(reviews.existsBySkillIdAndRequestedVisibilityIsNotNullAndStatus(fixture.skillId(), ReviewTaskStatus.PENDING)).isFalse();
        assertThat(((SkillRepository) skills).findById(fixture.skillId()).orElseThrow().getVisibility()).isEqualTo(SkillVisibility.PRIVATE);
    }

    private int change(CountDownLatch start, Fixture fixture) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try {
            visibility.change(fixture.skillId(), SkillVisibility.PUBLIC, fixture.owner(), Map.of(), Set.of());
            return 1;
        } catch (com.iflytek.skillhub.domain.shared.exception.DomainConflictException conflict) { return 0; }
    }

    private Fixture fixture() {
        return new TransactionTemplate(transactions).execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String owner = "scope-owner-" + suffix;
            users.saveAndFlush(new UserAccount(owner, owner, null, null));
            Namespace ns = namespaces.saveAndFlush(new Namespace("scope-" + suffix, "Scope", owner));
            members.saveAndFlush(new com.iflytek.skillhub.domain.namespace.NamespaceMember(
                    ns.getId(), owner, com.iflytek.skillhub.domain.namespace.NamespaceRole.OWNER));
            Skill skill = skills.saveAndFlush(new Skill(ns.getId(), "foo", owner, SkillVisibility.PRIVATE));
            SkillVersion published = new SkillVersion(skill.getId(), "1.0.0", owner);
            published.setStatus(SkillVersionStatus.PUBLISHED);
            published.setBundleReady(true);
            published = versions.saveAndFlush(published);
            skill.setLatestVersionId(published.getId());
            skills.saveAndFlush(skill);
            SecurityAudit scan = new SecurityAudit(published.getId(), ScannerType.SKILL_SCANNER, "scope-scan-" + suffix);
            scan.setScannedAt(Instant.now()); scan.setIsSafe(true);
            audits.saveAndFlush(scan);
            SkillVersion uploaded = new SkillVersion(skill.getId(), "2.0.0", owner);
            uploaded.setStatus(SkillVersionStatus.UPLOADED);
            uploaded = versions.saveAndFlush(uploaded);
            return new Fixture(skill.getId(), published.getId(), uploaded.getId(), owner);
        });
    }
    private record Fixture(Long skillId, Long publishedId, Long uploadedId, String owner) {}
}
