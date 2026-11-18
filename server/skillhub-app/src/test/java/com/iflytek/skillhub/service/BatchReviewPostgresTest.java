package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.governance.GovernanceNotificationService;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.review.*;
import com.iflytek.skillhub.domain.security.*;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.skill.service.SkillGovernanceService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.dto.BatchReviewRequest;
import com.iflytek.skillhub.infra.jpa.*;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.GovernanceQueryRepository;
import com.iflytek.skillhub.support.PostgresTestImage;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.support.StaticMessageSource;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BatchReviewPostgresTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PostgresTestImage.name());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired ReviewTaskJpaRepository reviews;
    @Autowired SkillJpaRepository skills;
    @Autowired SkillVersionJpaRepository versions;
    @Autowired SecurityAuditJpaRepository audits;
    @Autowired NamespaceJpaRepository namespaces;
    @Autowired NamespaceMemberJpaRepository members;
    @Autowired UserNotificationJpaRepository notifications;
    @Autowired AuditLogJpaRepository auditLogs;
    @Autowired JdbcTemplate jdbc;
    ReviewPortalAppService portal;
    BatchReviewAppService batch;
    RbacService rbac;
    String actor;

    @BeforeEach void setup() {
        actor = "reviewer-" + UUID.randomUUID();
        new TransactionTemplate(manager).executeWithoutResult(tx -> em.persist(new UserAccount(actor,"Reviewer",null,null)));
        rbac = mock(RbacService.class);
        when(rbac.getUserRoleCodes(actor)).thenReturn(Set.of("SUPER_ADMIN"));
        var domain = new ReviewService(mock(SkillOwnerAccessPolicy.class), reviews, versions, skills,
                namespaces, new ReviewPermissionChecker(), mock(ApplicationEventPublisher.class), new ObjectMapper(),
                mock(SkillGovernanceService.class), new GovernanceNotificationService(notifications, Clock.systemUTC()), em, Clock.systemUTC());
        portal = spy(new ReviewPortalAppService(domain, reviews, namespaces, mock(GovernanceQueryRepository.class),
                mock(com.iflytek.skillhub.repository.ReviewProgressQueryRepository.class), rbac,
                new AuditLogService(auditLogs, Clock.systemUTC()), mock(RequestIdAccessor.class),
                mock(com.iflytek.skillhub.domain.suite.SkillSuiteLifecycleService.class),
                mock(com.iflytek.skillhub.repository.ReviewQueueQueryRepository.class), new ReviewPermissionChecker()));
        batch = new BatchReviewAppService(portal,reviews,skills,versions,audits,namespaces,members,
                new ReviewPermissionChecker(),rbac,em,new StaticMessageSource(),manager);
    }
    record Fixture(long reviewId, long versionId, long skillId) {}
    Fixture fixture() {
        return new TransactionTemplate(manager).execute(tx -> {
            String owner = "author-"+UUID.randomUUID();
            em.persist(new UserAccount(owner,"Author",null,null));
            Namespace ns = new Namespace("batch-"+UUID.randomUUID(),"Batch",owner);
            em.persist(ns);em.flush();
            Skill skill = new Skill(ns.getId(),"same-name",owner,SkillVisibility.PUBLIC);
            em.persist(skill);em.flush();
            SkillVersion version = new SkillVersion(skill.getId(),"1.0.0",owner);
            version.setStatus(SkillVersionStatus.PENDING_REVIEW);em.persist(version);em.flush();
            SecurityAudit scan = new SecurityAudit(version.getId(),ScannerType.SKILL_SCANNER,"scan-"+UUID.randomUUID());
            scan.setScannedAt(Instant.now());scan.setVerdict(SecurityVerdict.SAFE);scan.setIsSafe(true);em.persist(scan);
            ReviewTask task = new ReviewTask(version.getId(),skill.getId(),ns.getId(),version.getVersion(),owner);
            em.persist(task);em.flush();
            return new Fixture(task.getId(),version.getId(),skill.getId());
        });
    }
    BatchReviewRequest request(Fixture... fixtures) {
        return new BatchReviewRequest(java.util.Arrays.stream(fixtures).map(Fixture::reviewId).toList(),BatchReviewRequest.Action.APPROVE,"Reviewed");
    }
    String status(String table,long id) { return jdbc.queryForObject("SELECT status FROM "+table+" WHERE id=?",String.class,id); }

    @Test void rollsBackDecisionVersionPointerNotificationsAndAuditWhenOneItemFails() {
        var first=fixture();var second=fixture();
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("failure after domain and audit writes"); })
                .when(portal).approveReview(eq(first.reviewId()),any(),eq(actor),any(),any());
        var response=batch.process(request(first,second),actor,null,false);
        assertThat(response.items()).extracting(item->item.outcome()).containsExactly("UNKNOWN","APPROVED");
        assertThat(status("review_task",first.reviewId())).isEqualTo("PENDING");
        assertThat(status("skill_version",first.versionId())).isEqualTo("PENDING_REVIEW");
        assertThat(jdbc.queryForObject("SELECT latest_version_id FROM skill WHERE id=?",Long.class,first.skillId())).isNull();
        assertThat(status("review_task",second.reviewId())).isEqualTo("APPROVED");
        assertThat(status("skill_version",second.versionId())).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_id=? AND action='REVIEW_APPROVE'",Integer.class,first.reviewId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_id=? AND action='REVIEW_APPROVE'",Integer.class,second.reviewId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_notification WHERE entity_id=? AND entity_type='REVIEW_TASK'",Integer.class,first.reviewId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_notification WHERE entity_id=? AND entity_type='REVIEW_TASK'",Integer.class,second.reviewId())).isEqualTo(1);
    }

    @Test void approvingNewThenOldCannotMoveLatestPointerBackwards() {
        var old=fixture();
        var newer=new TransactionTemplate(manager).execute(tx -> {
            var prior=((ReviewTaskRepository) reviews).findById(old.reviewId()).orElseThrow();
            var version=new SkillVersion(old.skillId(),"2.0.0",prior.getSubmittedBy());
            version.setStatus(SkillVersionStatus.PENDING_REVIEW);em.persist(version);em.flush();
            var scan=new SecurityAudit(version.getId(),ScannerType.SKILL_SCANNER,"newer-scan");
            scan.setScannedAt(Instant.now());scan.setVerdict(SecurityVerdict.SAFE);scan.setIsSafe(true);em.persist(scan);
            var task=new ReviewTask(version.getId(),old.skillId(),prior.getNamespaceId(),"2.0.0",prior.getSubmittedBy());
            em.persist(task);em.flush();return new Fixture(task.getId(),version.getId(),old.skillId());
        });
        assertThat(batch.process(request(newer,old),actor,null,false).items())
                .extracting(item->item.outcome()).containsExactly("APPROVED","SKIPPED");
        assertThat(jdbc.queryForObject("SELECT latest_version_id FROM skill WHERE id=?",Long.class,old.skillId())).isEqualTo(newer.versionId());
        assertThat(status("review_task",old.reviewId())).isEqualTo("PENDING");
    }

    @RepeatedTest(3) void simultaneousDecisionsPublishAndNotifyExactlyOnce() throws Exception {
        var fixture=fixture();var start=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var a=executor.submit(()->{start.await();return batch.process(request(fixture),actor,null,false);});
            var b=executor.submit(()->{start.await();return batch.process(request(fixture),actor,null,false);});
            start.countDown();
            var outcomes=List.of(a.get(20,TimeUnit.SECONDS).items().getFirst().outcome(),b.get(20,TimeUnit.SECONDS).items().getFirst().outcome());
            assertThat(outcomes).containsExactlyInAnyOrder("APPROVED","SKIPPED");
        }
        assertThat(status("skill_version",fixture.versionId())).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_id=? AND action='REVIEW_APPROVE'",Integer.class,fixture.reviewId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_notification WHERE entity_id=? AND entity_type='REVIEW_TASK'",Integer.class,fixture.reviewId())).isEqualTo(1);
        assertThat(batch.process(request(fixture),actor,null,false).items().getFirst().outcome()).isEqualTo("SKIPPED");
    }
}
