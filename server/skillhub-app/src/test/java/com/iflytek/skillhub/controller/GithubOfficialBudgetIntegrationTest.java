package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.support.PostgresTestImage;
import static com.iflytek.skillhub.support.GithubSyncWorkerCapabilities.platform;

import com.iflytek.skillhub.githubsync.*;
import com.iflytek.skillhub.infra.jpa.JdbcGithubOfficialDiscoveryBudgetRepository;
import com.iflytek.skillhub.service.GithubCredentialLeaseAppService;
import com.iflytek.skillhub.repository.GithubDeepDiscoveryQueryRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "skillhub.builtin-skills.enabled=false")
class GithubOfficialBudgetIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled",()->true);
        registry.add("spring.jpa.hibernate.ddl-auto",()->"validate");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired GithubOfficialDiscoveryRepository scheduler;
    @Autowired GithubSyncTaskRepository tasks;
    @Autowired GithubOfficialDiscoveryBudgetRepository budget;
    @Autowired GithubOfficialQuotaRepository quota;
    @Autowired GithubCredentialLeaseAppService claims;
    @Autowired GithubDeepDiscoveryQueryRepository queries;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE github_sync_task,github_deep_discovery_query,github_system_credential,github_discovery_candidate CASCADE");
        jdbc.execute("TRUNCATE github_official_discovery_reservation");
        jdbc.execute("DELETE FROM github_official_resource_cooldown");
        jdbc.execute("DELETE FROM github_official_secondary_cooldown");
        jdbc.update("UPDATE github_deep_discovery_config SET enabled=TRUE,budget_profile='BALANCED' WHERE id");
        advanceBudget();
        jdbc.update("UPDATE github_sync_admission_policy SET global_high=10000,global_low=5000,namespace_high=10000,namespace_low=5000,candidate_pending_limit=10000 WHERE id");
    }

    @Test void profilesChargeSnapshotRequestsWithoutIdleCredit() {
        for (var entry: java.util.Map.of("ECONOMY",600,"BALANCED",200,"ACCELERATED",100).entrySet()) {
            jdbc.update("UPDATE github_deep_discovery_config SET budget_profile=? WHERE id",entry.getKey());
            expireAll();advanceBudget();enqueue(1);
            var lease=claim();
            assertThat(jdbc.queryForObject("SELECT reserved_requests FROM github_official_discovery_reservation WHERE task_id=? AND lease_generation=?",Integer.class,lease.task().taskId(),lease.task().leaseGeneration())).isEqualTo(10);
            assertThat(jdbc.queryForObject("SELECT extract(epoch from next_lease_eligible_at-granted_at)::integer FROM github_official_discovery_reservation WHERE task_id=? AND lease_generation=?",Integer.class,lease.task().taskId(),lease.task().leaseGeneration())).isEqualTo(entry.getValue());
            assertThat(queries.discoveryBudgetWait().retryAt()).isAfter(Instant.now());
        }
    }

    @Test void profilePauseAndRepositoryRecreationCannotResetReservation() {
        enqueue(1);var lease=claim();Instant next=next();
        new TransactionTemplate(transactions).executeWithoutResult(status -> new JdbcGithubOfficialDiscoveryBudgetRepository(jdbc).reserveSuccessfulClaim(lease.task().taskId(),lease.task().leaseGeneration()));
        assertThat(next()).isEqualTo(next);
        jdbc.update("UPDATE github_deep_discovery_config SET enabled=FALSE,budget_profile='ACCELERATED' WHERE id");
        jdbc.update("UPDATE github_deep_discovery_config SET enabled=TRUE WHERE id");
        expireAll();
        assertThat(claimOptional()).isEmpty();assertThat(next()).isEqualTo(next);
        advanceBudget();var second=claim();
        assertThat(second.task().leaseGeneration()).isGreaterThan(lease.task().leaseGeneration());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_official_discovery_reservation",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT extract(epoch from next_lease_eligible_at-granted_at)::integer FROM github_official_discovery_reservation WHERE task_id=? AND lease_generation=?",Integer.class,second.task().taskId(),second.task().leaseGeneration())).isEqualTo(100);
    }

    @Test void concurrentWorkersShareOnePermitAndActiveLeaseSurvivesPacingExpiry() throws Exception {
        enqueue(3);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{start.await();return claimOptional();});
            var second=pool.submit(()->{start.await();return claimOptional();});start.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS)).stream().filter(java.util.Optional::isPresent).count()).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_official_discovery_reservation",Integer.class)).isEqualTo(1);
        advanceBudget();assertThat(claimOptional()).isEmpty();
    }

    @Test void unavailableCredentialsAndQuotaDoNotSpendBudget() {
        enqueue(1);Instant previous=next();
        jdbc.update("INSERT INTO github_system_credential(id,label,fingerprint,key_version,nonce,ciphertext,status) VALUES (?,'fixture','budgetfixture','v1',?,?,'INVALID')",UUID.randomUUID(),new byte[12],new byte[17]);
        assertThat(claimOptional()).isEmpty();assertThat(next()).isEqualTo(previous);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_official_discovery_reservation",Integer.class)).isZero();
        jdbc.update("DELETE FROM github_system_credential");
        jdbc.update("UPDATE github_sync_task SET due_at=clock_timestamp()-INTERVAL '1 second'");
        jdbc.update("INSERT INTO github_official_resource_cooldown(resource,primary_until) VALUES ('SEARCH',clock_timestamp()+INTERVAL '1 hour')");
        assertThat(claimOptional()).isEmpty();assertThat(next()).isEqualTo(previous);
        jdbc.update("DELETE FROM github_official_resource_cooldown");
        jdbc.update("UPDATE github_sync_task SET due_at=clock_timestamp()-INTERVAL '1 second'");
        assertThat(claimOptional()).isPresent();
    }

    @Test void blockedOfficialBudgetDoesNotStarveLegacyDiscovery() {
        enqueue(1);claim();
        UUID task=queueLegacy();
        assertThat(claim().task().taskId()).isEqualTo(task);
        UUID nextLegacy=queueLegacy();
        expireAll();advanceBudget();
        var official=claim();
        assertThat(official.task().taskId()).isNotEqualTo(nextLegacy);
        assertThat(claim().task().taskId()).isEqualTo(nextLegacy);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_official_discovery_reservation",Integer.class)).isEqualTo(2);
    }

    @Test void pendingCapacityFilledAfterEnqueuePreventsDebitButKeepsLegacyRunnable() {
        enqueue(1);Instant before=next();
        jdbc.update("UPDATE github_sync_admission_policy SET candidate_pending_limit=1 WHERE id");
        jdbc.update("INSERT INTO github_discovery_candidate(namespace_id,repository_id,owner_login,repository_name) SELECT namespace_id,999,'fixture','repo' FROM github_deep_discovery_config WHERE id");
        assertThat(claimOptional()).isEmpty();assertThat(next()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_official_discovery_reservation",Integer.class)).isZero();
        UUID legacy=queueLegacy();assertThat(claim().task().taskId()).isEqualTo(legacy);
        jdbc.update("UPDATE github_discovery_candidate SET status='REJECTED'");
        assertThat(claimOptional()).isPresent();
    }

    @Test void actualSnapshotRequestAllowanceDeterminesTheCharge() {
        enqueue(1);
        jdbc.update("UPDATE github_sync_task SET snapshot_json=jsonb_set(snapshot_json,'{officialDiscovery,budget,maxRequests}','3') WHERE official_query_id IS NOT NULL");
        claim();
        assertThat(jdbc.queryForObject("SELECT reserved_requests FROM github_official_discovery_reservation",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT extract(epoch from next_lease_eligible_at-granted_at)::integer FROM github_official_discovery_reservation",Integer.class)).isEqualTo(60);
    }

    @Test void oldestSuccessfullyClaimedQueryWinsOverPriorityAndRenewals() {
        enqueue(3);var first=claim();
        jdbc.update("UPDATE github_sync_task SET priority=100000 WHERE id=?",first.task().taskId());
        expireAll();advanceBudget();var second=claim();
        assertThat(second.task().taskId()).isNotEqualTo(first.task().taskId());
        expireAll();advanceBudget();var third=claim();
        assertThat(third.task().taskId()).isNotIn(first.task().taskId(),second.task().taskId());
    }

    @Test void skewedCallerClockCannotCreateExpiredOfficialLeaseOrRenewal() {
        enqueue(1);
        Instant skew=Instant.now().minusSeconds(3600);
        var lease=new TransactionTemplate(transactions).execute(status -> {
            var result=tasks.claim(GithubSyncTaskKind.DISCOVERY,"skewed","a".repeat(64),skew,skew.plusSeconds(300),platform(GithubOfficialDiscoveryCatalog.REQUIRED_CAPABILITIES)).orElseThrow();
            budget.reserveSuccessfulClaim(result.taskId(),result.leaseGeneration());
            return result;
        });
        assertThat(lease.leaseExpiresAt()).isAfter(Instant.now().plusSeconds(290));
        Instant future=Instant.now().plusSeconds(3600);
        var renewal=new TransactionTemplate(transactions).execute(status -> tasks.renew(lease.taskId(),lease.leaseGeneration(),"a".repeat(64),future,future.plusSeconds(300)));
        assertThat(renewal.orElseThrow()).isBetween(Instant.now().plusSeconds(290),Instant.now().plusSeconds(310));
        Boolean accepted=new TransactionTemplate(transactions).execute(status -> quota.record(lease.taskId(),lease.leaseGeneration(),"a".repeat(64),null,
                new GithubOfficialQuotaRepository.Observation("SEARCH",future,"OBSERVED",100,future.plusSeconds(600),null),future));
        assertThat(accepted).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_official_discovery_reservation",Integer.class)).isEqualTo(1);
    }

    @Test void expiredBeforeCredentialFinalizationRollsBackWithoutCharge() {
        enqueue(1);Instant before=next();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var lease=tasks.claim(GithubSyncTaskKind.DISCOVERY,"expired","b".repeat(64),Instant.now(),Instant.now().plusSeconds(300),platform(GithubOfficialDiscoveryCatalog.REQUIRED_CAPABILITIES)).orElseThrow();
            expireAll();budget.reserveSuccessfulClaim(lease.taskId(),lease.leaseGeneration());
        })).isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(next()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_official_discovery_reservation",Integer.class)).isZero();
        assertThat(claimOptional()).isPresent();
    }

    @Test void retainedTaskCleanupDoesNotResetGlobalPacing() {
        enqueue(1);var lease=claim();Instant before=next();
        jdbc.update("UPDATE github_deep_discovery_query SET active_task_id=NULL WHERE active_task_id=?",lease.task().taskId());
        jdbc.update("DELETE FROM github_official_discovery_shard WHERE task_id=?",lease.task().taskId());
        jdbc.update("DELETE FROM github_sync_task WHERE id=?",lease.task().taskId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_official_discovery_reservation",Integer.class)).isZero();
        assertThat(next()).isEqualTo(before);
    }

    private UUID queueLegacy() {
        long ns=jdbc.queryForObject("SELECT namespace_id FROM github_deep_discovery_config WHERE id",Long.class);
        UUID run=UUID.randomUUID(),task=UUID.randomUUID();
        jdbc.update("INSERT INTO github_sync_run(id,namespace_id,kind) VALUES (?,?,'DISCOVERY')",run,ns);
        jdbc.update("INSERT INTO github_sync_task(id,run_id,namespace_id,kind,dedupe_key,snapshot_json,priority) VALUES (?,?,?,'DISCOVERY',?,?::jsonb,100000)",task,run,ns,task.toString(),"{\"entryType\":\"SEARCH_RULE\",\"ruleId\":\""+UUID.randomUUID()+"\"}");
        return task;
    }

    private void enqueue(int count) { assertThat(scheduler.enqueueDue(Instant.now(),count)).isEqualTo(count); }
    private void advanceBudget() { jdbc.update("UPDATE github_official_discovery_budget SET next_lease_eligible_at=clock_timestamp()-INTERVAL '1 day'"); }
    private void expireAll() { jdbc.update("UPDATE github_sync_task SET lease_expires_at=clock_timestamp()-INTERVAL '1 second' WHERE status='LEASED' AND official_query_id IS NOT NULL"); }
    private Instant next() { return jdbc.queryForObject("SELECT next_lease_eligible_at FROM github_official_discovery_budget",java.sql.Timestamp.class).toInstant(); }
    private java.util.Optional<GithubCredentialLeaseAppService.Claimed> claimOptional() { return claims.claim(GithubSyncTaskKind.DISCOVERY,"budget-worker",platform(GithubOfficialDiscoveryCatalog.REQUIRED_CAPABILITIES)); }
    private GithubSyncLeaseService.LeasedTask claim() { return claimOptional().orElseThrow().lease(); }
}
