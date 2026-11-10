package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.support.PostgresTestImage;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {"skillhub.builtin-skills.enabled=false",
        "skillhub.github-sync.service-tokens=platform-control-test-only-service-token-32"})
class GithubPlatformControlIntegrationTest {
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
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Autowired GithubPlatformControlRepository control;
    @Autowired GithubSyncLeaseService leases;
    @Autowired GithubSyncHistoryRepository history;
    @Autowired PlatformTransactionManager transactions;
    long ns;
    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE github_sync_task CASCADE");
        jdbc.update("UPDATE github_platform_collection_control SET paused=false,version=1");
        ns=control.get().namespaceId();
    }
    @Test void capabilityGatePauseAcknowledgeAndResumePreserveCheckpoint() {
        UUID id=queue(ns);
        assertThat(leases.claim(GithubSyncTaskKind.DISCOVERY,"old-worker")).isEmpty();
        var lease=claim();
        assertThat(lease.task().platformControlVersion()).isEqualTo(1);
        jdbc.update("UPDATE github_sync_task SET discovery_checkpoint='confirmed-page' WHERE id=?",id);
        var paused=control.update(1,true,"tester");
        assertThat(paused.state()).isEqualTo("PAUSING");assertThat(paused.pendingStopCount()).isEqualTo(1);
        assertThat(control.control(id,lease.task().leaseGeneration(),lease.token()).action()).isEqualTo("PAUSE");
        assertThat(leases.claim(GithubSyncTaskKind.DISCOVERY,"worker",List.of(GithubPlatformControlRepository.CAPABILITY))).isEmpty();
        assertThatThrownBy(()->control.pause(id,lease.task().leaseGeneration(),lease.token(),2,"DISCOVERY","unconfirmed",null)).isInstanceOf(GithubOfficialProtocolException.class);
        control.pause(id,lease.task().leaseGeneration(),lease.token(),2,"DISCOVERY","confirmed-page",null);
        control.pause(id,lease.task().leaseGeneration(),lease.token(),2,"DISCOVERY","confirmed-page",null);
        assertThat(control.get().state()).isEqualTo("PAUSED");
        assertThat(control.update(2,false,"tester").state()).isEqualTo("RUNNING");
        var resumed=claim();assertThat(resumed.task().discoveryCheckpoint()).isEqualTo("confirmed-page");
        assertThat(resumed.task().leaseGeneration()).isEqualTo(2);
        assertThat(resumed.task().requiredCapabilities()).containsExactly(GithubPlatformControlRepository.CAPABILITY);
    }
    @Test void expiredWorkRequiresExplicitAckAndCannotContinueAfterResume() {
        UUID id=queue(ns);var lease=claim();control.update(1,true,"tester");
        jdbc.update("UPDATE github_sync_task SET lease_expires_at=clock_timestamp()-interval '1 second' WHERE id=?",id);
        assertThat(control.get().state()).isEqualTo("PAUSING");assertThat(control.get().expiredUnconfirmedCount()).isEqualTo(1);
        assertThat(control.control(id,lease.task().leaseGeneration(),lease.token()).action()).isEqualTo("PAUSE");
        control.pause(id,lease.task().leaseGeneration(),lease.token(),2,"DISCOVERY","must-not-persist",null);
        assertThat(jdbc.queryForObject("SELECT discovery_checkpoint FROM github_sync_task WHERE id=?",String.class,id)).isNull();
        assertThat(control.get().state()).isEqualTo("PAUSED");control.update(2,false,"tester");
        assertThatThrownBy(()->control.control(id,lease.task().leaseGeneration(),lease.token())).isInstanceOf(GithubOfficialProtocolException.class);
    }
    @Test void rapidResumeRejectsOldEpochAndNormalTerminalProofCanAcknowledge() {
        UUID id=queue(ns);var lease=claim();control.update(1,true,"tester");control.update(2,false,"tester");control.update(3,true,"tester");
        assertThatThrownBy(()->control.pause(id,lease.task().leaseGeneration(),lease.token(),2,"DISCOVERY",null,null)).isInstanceOf(GithubOfficialProtocolException.class);
        jdbc.update("UPDATE github_sync_task SET status='ACQUISITION_COMPLETE',lease_token_hash=NULL,lease_expires_at=NULL,leased_by=NULL WHERE id=?",id);
        assertThat(control.get().state()).isEqualTo("PAUSING");
        control.pause(id,lease.task().leaseGeneration(),lease.token(),4,"DISCOVERY",null,null);
        assertThat(control.get().state()).isEqualTo("PAUSED");
        assertThat(jdbc.queryForObject("SELECT status FROM github_sync_task WHERE id=?",String.class,id)).isEqualTo("ACQUISITION_COMPLETE");
        assertThat(control.update(3,true,"tester").version()).isEqualTo(4);
        assertThatThrownBy(()->control.update(1,false,"tester")).isInstanceOf(GithubOfficialProtocolException.class);
    }
    @Test void teamClaimsRemainIndependentWithoutCapability() {
        long team=jdbc.queryForObject("INSERT INTO namespace(slug,display_name,type) VALUES ('platform-team','Platform Team','TEAM') RETURNING id",Long.class);
        UUID id=queue(team);control.update(1,true,"tester");
        var lease=leases.claim(GithubSyncTaskKind.DISCOVERY,"legacy-team").orElseThrow();
        assertThat(lease.task().taskId()).isEqualTo(id);assertThat(lease.task().platformControlVersion()).isNull();
        assertThat(control.get().state()).isEqualTo("PAUSED");
    }
    @Test void pauseAndClaimSerializeAtTheDatabaseGate() throws Exception {
        queue(ns);
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var entered=new CountDownLatch(1);
        var olderPid=new java.util.concurrent.atomic.AtomicInteger();var newerPid=new java.util.concurrent.atomic.AtomicInteger();
        var tx=new TransactionTemplate(transactions);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var pause=pool.submit(()->tx.execute(status->{
                olderPid.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));
                control.update(1,true,"tester");locked.countDown();
                try {if(!release.await(20,TimeUnit.SECONDS)) throw new IllegalStateException("release timeout");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
                return null;
            }));
            java.util.concurrent.Future<java.util.Optional<GithubSyncLeaseService.LeasedTask>> claim=null;
            try {
                assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
                claim=pool.submit(()->tx.execute(status->{
                    newerPid.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));entered.countDown();
                    return leases.claim(GithubSyncTaskKind.DISCOVERY,"blocked-worker",List.of(GithubPlatformControlRepository.CAPABILITY));
                }));
                assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
                try(var connection=java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
                    var statement=connection.prepareStatement("SELECT ? = ANY(pg_blocking_pids(?))")) {
                    statement.setInt(1,olderPid.get());statement.setInt(2,newerPid.get());statement.setQueryTimeout(2);
                    boolean blocked=false;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                    while(System.nanoTime()<deadline){try(var result=statement.executeQuery()){result.next();blocked=result.getBoolean(1);}if(blocked)break;Thread.sleep(10);}
                    assertThat(blocked).as("claim reaches the database and waits on pause transaction").isTrue();
                }
            } finally {release.countDown();}
            pause.get(20,TimeUnit.SECONDS);assertThat(claim.get(20,TimeUnit.SECONDS)).isEmpty();
        }
        assertThat(control.get().state()).isEqualTo("PAUSED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_sync_task WHERE status='LEASED'",Integer.class)).isZero();
    }
    @Test void pauseAndAdmissionSerializeAtTheDatabaseGate() throws Exception {
        UUID id=queue(ns);var lease=claim();
        jdbc.update("UPDATE github_sync_task SET kind='REPOSITORY_SYNC',mode='FULL',snapshot_json=jsonb_build_object('repositoryId',812,'owner','fixture','name','skills') WHERE id=?",id);
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var entered=new CountDownLatch(1);
        var olderPid=new java.util.concurrent.atomic.AtomicInteger();var newerPid=new java.util.concurrent.atomic.AtomicInteger();
        var tx=new TransactionTemplate(transactions);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var pause=pool.submit(()->tx.execute(status->{
                olderPid.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));
                control.update(1,true,"tester");locked.countDown();
                try {if(!release.await(20,TimeUnit.SECONDS)) throw new IllegalStateException("release timeout");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
                return null;
            }));
            java.util.concurrent.Future<GithubPackageAdmissionResult> claim=null;
            try {
                assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
                claim=pool.submit(()->tx.execute(status->{
                    newerPid.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));entered.countDown();
                    return leases.admitPackage(id,lease.task().leaseGeneration(),lease.token(),"a".repeat(40),"new-path");
                }));
                assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
                try(var connection=java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
                    var statement=connection.prepareStatement("SELECT ? = ANY(pg_blocking_pids(?))")) {
                    statement.setInt(1,olderPid.get());statement.setInt(2,newerPid.get());statement.setQueryTimeout(2);
                    boolean blocked=false;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                    while(System.nanoTime()<deadline){try(var result=statement.executeQuery()){result.next();blocked=result.getBoolean(1);}if(blocked)break;Thread.sleep(10);}
                    assertThat(blocked).as("admission reaches the database and waits on pause transaction").isTrue();
                }
            } finally {release.countDown();}
            pause.get(20,TimeUnit.SECONDS);assertThat(claim.get(20,TimeUnit.SECONDS).status()).isEqualTo(GithubPackageAdmissionResult.Status.BACKPRESSURE_ACTIVE);
        }
        assertThat(control.get().state()).isEqualTo("PAUSING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_sync_package_admission",Integer.class)).isZero();
    }
    @Test void fullPausePreservesConfirmedProgressAndOnlyReleasesUnacceptedAdmissions() {
        UUID id=queue(ns);var lease=claim();String commit="a".repeat(40);
        jdbc.update("UPDATE github_sync_task SET kind='REPOSITORY_SYNC',mode='FULL',snapshot_json=jsonb_build_object('repositoryId',811,'owner','fixture','name','skills') WHERE id=?",id);
        jdbc.update("INSERT INTO github_sync_package_admission(id,task_id,namespace_id,repository_id,commit_sha,skill_path,lease_generation,status) VALUES (?,?,?,811,?,'unfinished',1,'ACTIVE'),(?,?,?,811,?,'received',1,'ACCEPTED')",UUID.randomUUID(),id,ns,commit,UUID.randomUUID(),id,ns,commit);
        control.update(1,true,"tester");
        assertThat(leases.admitPackage(id,lease.task().leaseGeneration(),lease.token(),commit,"next").status()).isEqualTo(GithubPackageAdmissionResult.Status.BACKPRESSURE_ACTIVE);
        control.pause(id,lease.task().leaseGeneration(),lease.token(),2,"REPOSITORY_SYNC",null,
                new GithubOfficialLifecycleRepository.RepositoryProgress(commit,"received",List.of("unknown"),List.of()));
        assertThat(control.get().state()).isEqualTo("PAUSED");
        assertThat(jdbc.queryForObject("SELECT fixed_commit_sha FROM github_sync_task WHERE id=?",String.class,id)).isEqualTo(commit);
        assertThat(jdbc.queryForObject("SELECT path_checkpoint FROM github_sync_task WHERE id=?",String.class,id)).isEqualTo("received");
        assertThat(jdbc.queryForObject("SELECT status FROM github_sync_package_admission WHERE task_id=? AND skill_path='unfinished'",String.class,id)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT status FROM github_sync_package_admission WHERE task_id=? AND skill_path='received'",String.class,id)).isEqualTo("ACCEPTED");
    }
    @Test void headOnlyPauseAndResumeDoNotEnableIndependentlyDisabledDiscovery() {
        UUID id=queue(ns);var lease=claim();
        jdbc.update("UPDATE github_sync_task SET kind='REPOSITORY_SYNC',mode='HEAD_ONLY' WHERE id=?",id);
        jdbc.update("UPDATE github_deep_discovery_config SET enabled=false WHERE id");
        control.update(1,true,"tester");
        control.pause(id,lease.task().leaseGeneration(),lease.token(),2,"REPOSITORY_SYNC",null,
                new GithubOfficialLifecycleRepository.RepositoryProgress("b".repeat(40),null,List.of(),List.of()));
        assertThat(control.get().state()).isEqualTo("PAUSED");control.update(2,false,"tester");
        assertThat(jdbc.queryForObject("SELECT enabled FROM github_deep_discovery_config WHERE id",Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM github_sync_task WHERE id=?",String.class,id)).isEqualTo("QUEUED");
    }
    @Test void cleanupRetainsUnconfirmedTerminalLedgerUntilAck() {
        UUID id=queue(ns);var lease=claim();control.update(1,true,"tester");
        jdbc.update("UPDATE github_sync_task SET status='ACQUISITION_COMPLETE',lease_token_hash=NULL,lease_expires_at=NULL,leased_by=NULL,completed_at=clock_timestamp()-interval '60 days' WHERE id=?",id);
        jdbc.update("UPDATE github_sync_run SET status='ACQUISITION_COMPLETE',finished_at=clock_timestamp()-interval '60 days' WHERE id=(SELECT run_id FROM github_sync_task WHERE id=?)",id);
        assertThat(history.deleteExpiredDiscoveryHistory(Instant.now().minusSeconds(86400),100).tasks()).isZero();
        assertThat(control.get().pendingStopCount()).isEqualTo(1);
        control.pause(id,lease.task().leaseGeneration(),lease.token(),2,"DISCOVERY",null,null);
        assertThat(history.deleteExpiredDiscoveryHistory(Instant.now().minusSeconds(86400),100).tasks()).isEqualTo(1);
    }
    @Test void adminHttpConflictAndInternalStaleProofAreMapped() throws Exception {
        var principal=new com.iflytek.skillhub.auth.rbac.PlatformPrincipal("github-sync-publisher","fixture",null,null,null,java.util.Set.of("SUPER_ADMIN"));
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        String endpoint="/api/v1/admin/github-sync/platform-control";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(endpoint)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content("{\"expectedVersion\":1,\"paused\":true}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.state").value("PAUSED"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(endpoint)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content("{\"expectedVersion\":1,\"paused\":false}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/github-sync/v1/tasks/"+UUID.randomUUID()+"/platform-control")
                .header("Authorization","Bearer platform-control-test-only-service-token-32")
                .contentType("application/json").content("{\"lease\":{\"generation\":1,\"token\":\""+"a".repeat(64)+"\"}}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("LEASE_STALE"));
    }
    @Test void realGoExecutorClaimsObservesPauseAcknowledgesAndResumesCheckpoint() throws Exception {
        UUID id=queue(ns);
        jdbc.update("UPDATE github_sync_task SET discovery_checkpoint='confirmed-page',snapshot_json=?::jsonb WHERE id=?",
                "{\"entryType\":\"CURATED_REPOSITORY\",\"ruleVersion\":1,\"owner\":\"fixture\",\"repositoryName\":\"skills\",\"candidateBudget\":100}",id);
        java.nio.file.Path temp=java.nio.file.Files.createTempDirectory("platform-control-interop-");
        var ready=temp.resolve("ready");var output=temp.resolve("go.log");
        var root=java.nio.file.Path.of("").toAbsolutePath();
        while(root!=null && !java.nio.file.Files.isDirectory(root.resolve("github-sync-go"))) root=root.getParent();
        assertThat(root).isNotNull();
        var builder=new ProcessBuilder("go","test","./internal/worker","-run","^TestPlatformJavaInterop$","-count=1","-timeout=45s")
                .directory(root.resolve("github-sync-go").toFile()).redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().put("SKILLHUB_PLATFORM_INTEROP_URL","http://127.0.0.1:"+port+"/internal/github-sync/v1");
        builder.environment().put("SKILLHUB_PLATFORM_INTEROP_TOKEN","platform-control-test-only-service-token-32");
        builder.environment().put("SKILLHUB_PLATFORM_INTEROP_READY",ready.toString());
        var process=builder.start();
        try {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
            while(!java.nio.file.Files.exists(ready) && process.isAlive() && System.nanoTime()<deadline) Thread.sleep(20);
            assertThat(java.nio.file.Files.exists(ready)).withFailMessage(java.nio.file.Files.readString(output)).isTrue();
            var pause=control.update(1,true,"tester");assertThat(pause.pendingStopCount()).isEqualTo(1);
            assertThat(process.waitFor(25,TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).withFailMessage(java.nio.file.Files.readString(output)).isZero();
            assertThat(control.get().state()).isEqualTo("PAUSED");
            assertThat(jdbc.queryForObject("SELECT discovery_checkpoint FROM github_sync_task WHERE id=?",String.class,id)).isEqualTo("confirmed-page");
            control.update(2,false,"tester");assertThat(claim().task().discoveryCheckpoint()).isEqualTo("confirmed-page");
        } finally {
            if(process.isAlive()){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}
            org.springframework.util.FileSystemUtils.deleteRecursively(temp);
        }
    }
    private UUID queue(long namespace) {
        UUID id=UUID.randomUUID(),run=UUID.randomUUID();
        jdbc.update("INSERT INTO github_sync_run(id,namespace_id,kind) VALUES (?,?,'DISCOVERY')",run,namespace);
        jdbc.update("INSERT INTO github_sync_task(id,run_id,namespace_id,kind,dedupe_key,snapshot_json) VALUES (?,?,?,'DISCOVERY',?,?::jsonb)",id,run,namespace,id.toString(),"{\"entryType\":\"SEARCH_RULE\",\"ruleId\":\""+UUID.randomUUID()+"\"}");
        return id;
    }
    private GithubSyncLeaseService.LeasedTask claim() {return leases.claim(GithubSyncTaskKind.DISCOVERY,"worker",List.of(GithubPlatformControlRepository.CAPABILITY)).orElseThrow();}
}
