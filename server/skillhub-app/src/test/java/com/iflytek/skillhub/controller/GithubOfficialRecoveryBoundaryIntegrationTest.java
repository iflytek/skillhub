package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.support.PostgresTestImage;
import static com.iflytek.skillhub.support.GithubSyncWorkerCapabilities.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.githubsync.*;
import com.iflytek.skillhub.service.GithubCredentialLeaseAppService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "skillhub.builtin-skills.enabled=false")
class GithubOfficialRecoveryBoundaryIntegrationTest {
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
    @Autowired com.iflytek.skillhub.infra.jpa.JdbcGithubOfficialShardRepository shards;
    @Autowired GithubCandidateTaskRepository candidates;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired GithubOfficialDiscoveryRepository scheduler;
    @Autowired GithubSyncLeaseService leases;
    @Autowired GithubOfficialLifecycleRepository lifecycle;
    @Autowired GithubCredentialLeaseAppService credentialClaims;

    @BeforeEach void setup() {
        jdbc.update("UPDATE github_deep_discovery_config SET enabled=TRUE WHERE id");
        jdbc.update("UPDATE github_sync_admission_policy SET global_high=10000,global_low=5000,namespace_high=10000,namespace_low=5000 WHERE id");
    }

    @Test void catalogUpgradePreservesAdminPolicyCompatibleCheckpointAndHistoricalStart() throws Exception {
        var lease=claim();UUID query=query(lease);String cp=checkpoint(lease,"IN_PROGRESS");
        report(lease,null,cp,"IN_PROGRESS",List.of());
        // Represent an earlier compatible strategy without changing its query semantics.
        cp=((com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(cp)).put("strategyVersion","previous").toString();
        jdbc.update("UPDATE github_sync_task SET snapshot_json=jsonb_set(snapshot_json,'{officialDiscovery,strategyVersion}','\"previous\"'),discovery_checkpoint=? WHERE id=?",cp,lease.task().taskId());
        jdbc.update("UPDATE github_deep_discovery_query SET strategy_version='previous',checkpoint=?,covered_until='2020-01-01T00:00:00Z' WHERE id=?",cp,query);
        String original=jdbc.queryForObject("SELECT snapshot_json::text FROM github_sync_task WHERE id=?",String.class,lease.task().taskId());
        UUID missing=jdbc.queryForObject("SELECT id FROM github_deep_discovery_query WHERE id<>? ORDER BY query_key LIMIT 1",UUID.class,query);
        String key=jdbc.queryForObject("SELECT query_key FROM github_deep_discovery_query WHERE id=?",String.class,missing);
        jdbc.update("DELETE FROM github_deep_discovery_query WHERE id=?",missing);
        jdbc.update("UPDATE github_deep_discovery_config SET enabled=FALSE,budget_profile='ECONOMY',config_version=41,exclusions_json='[{\"kind\":\"OWNER\",\"value\":\"excluded\"}]',strategy_generation=?,strategy_version='previous' WHERE id",GithubOfficialDiscoveryCatalog.GENERATION-1);
        assertThat(scheduler.enqueueDue(Instant.now(),10)).isZero();
        assertThat(jdbc.queryForMap("SELECT enabled,budget_profile,config_version,exclusions_json::text FROM github_deep_discovery_config WHERE id"))
                .containsEntry("enabled",false).containsEntry("budget_profile","ECONOMY").containsEntry("config_version",41L);
        assertThat(jdbc.queryForObject("SELECT exclusions_json->0->>'value' FROM github_deep_discovery_config WHERE id",String.class)).isEqualTo("excluded");
        assertThat(jdbc.queryForObject("SELECT strategy_generation FROM github_deep_discovery_config WHERE id",Long.class)).isEqualTo(GithubOfficialDiscoveryCatalog.GENERATION);
        assertThat(jdbc.queryForObject("SELECT checkpoint FROM github_deep_discovery_query WHERE id=?",String.class,query)).isEqualTo(cp);
        assertThat(jdbc.queryForObject("SELECT snapshot_json::text FROM github_sync_task WHERE id=?",String.class,lease.task().taskId())).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT covered_until FROM github_deep_discovery_query WHERE id=?",java.sql.Timestamp.class,query).toInstant()).isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));
        UUID added=jdbc.queryForObject("SELECT id FROM github_deep_discovery_query WHERE query_key=?",UUID.class,key);
        assertThat(added).isNotEqualTo(missing);
        jdbc.update("UPDATE github_deep_discovery_config SET enabled=TRUE WHERE id");
        onlyDue(added);assertThat(scheduler.enqueueDue(Instant.now(),1)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT snapshot_json#>>'{officialDiscovery,range,since}' FROM github_sync_task WHERE official_query_id=?",String.class,added)).isEqualTo(Instant.EPOCH.toString());
        jdbc.update("UPDATE github_deep_discovery_config SET strategy_generation=?,strategy_version='future-release' WHERE id",GithubOfficialDiscoveryCatalog.GENERATION+1);
        int count=jdbc.queryForObject("SELECT count(*) FROM github_deep_discovery_query",Integer.class);
        assertThat(scheduler.enqueueDue(Instant.now().plusSeconds(86400),100)).isZero();
        assertThat(jdbc.queryForObject("SELECT strategy_version FROM github_deep_discovery_config WHERE id",String.class)).isEqualTo("future-release");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_deep_discovery_query",Integer.class)).isEqualTo(count);
    }

    @Test void retiredQueryFinishesExistingRootWithoutStartingAnother() throws Exception {
        var root=claim();UUID query=query(root);var official=snapshot(root);
        jdbc.update("UPDATE github_deep_discovery_query SET query_key='retired-fixture' WHERE id=?",query);
        jdbc.update("UPDATE github_deep_discovery_config SET enabled=FALSE WHERE id");
        scheduler.enqueueDue(Instant.now(),1);
        assertThat(jdbc.queryForObject("SELECT retired_at FROM github_deep_discovery_query WHERE id=?",java.sql.Timestamp.class,query)).isNotNull();
        jdbc.update("UPDATE github_deep_discovery_config SET enabled=TRUE WHERE id");onlyDue(query);
        String cp=checkpoint(root,"SPLIT");report(root,null,cp,"SPLIT",halves(official));
        lifecycle.complete(root.task().taskId(),root.task().leaseGeneration(),root.token(),cp,"COMPLETE",null);
        for(int i=0;i<2;i++) {
            var child=claim();assertThat(query(child)).isEqualTo(query);
            String done=checkpoint(child,"COMPLETE");report(child,null,done,"COMPLETE",List.of());
            lifecycle.complete(child.task().taskId(),child.task().leaseGeneration(),child.token(),done,"COMPLETE",null);
        }
        assertThat(jdbc.queryForObject("SELECT covered_until FROM github_deep_discovery_query WHERE id=?",java.sql.Timestamp.class,query).toInstant()).isEqualTo(Instant.parse(official.path("range").path("until").asText()));
        onlyDue(query);
        assertThat(scheduler.enqueueDue(Instant.now(),1)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_official_discovery_shard WHERE query_id=? AND parent_id IS NULL",Integer.class,query)).isEqualTo(1);
    }

    @Test void threeOriginsRemainIsolatedAcrossDisableExclusionAndResume() {
        long ns=namespace();UUID independent=UUID.randomUUID();
        jdbc.update("INSERT INTO github_discovery_entry(id,namespace_id,entry_type,title,criteria_json,enabled,target_interval_seconds) VALUES (?,?,'SEARCH_RULE','independent fixture','{}',TRUE,3600)",independent,ns);
        for(long repo:List.of(101L,102L,103L)) {
            long candidate=jdbc.queryForObject("INSERT INTO github_discovery_candidate(namespace_id,repository_id,owner_login,repository_name) VALUES (?,?,'fixture','skills') RETURNING id",Long.class,ns,repo);
            jdbc.update("INSERT INTO github_discovery_candidate_origin(candidate_id,origin_type,origin_key) VALUES (?,'OFFICIAL_DISCOVERY',?)",candidate,UUID.randomUUID().toString());
            if(repo==102) jdbc.update("INSERT INTO github_discovery_candidate_origin(candidate_id,origin_type,origin_key) VALUES (?,'SEARCH_RULE',?)",candidate,independent.toString());
        }
        long source=jdbc.queryForObject("INSERT INTO github_source_binding(namespace_id,repository_id,owner_login,repository_name,visibility) VALUES (?,103,'fixture','skills','PUBLIC') RETURNING id",Long.class,ns);
        assertThat(candidates.enqueueDue(Instant.now().plusSeconds(1),10)).isEqualTo(2);
        UUID run=UUID.randomUUID(),sourceTask=UUID.randomUUID();
        jdbc.update("INSERT INTO github_sync_run(id,namespace_id,kind) VALUES (?,?,'REPOSITORY_SYNC')",run,ns);
        jdbc.update("INSERT INTO github_sync_task(id,run_id,namespace_id,source_id,kind,mode,dedupe_key,snapshot_json) VALUES (?,?,?,?,'REPOSITORY_SYNC','HEAD_ONLY',?,'{\"repositoryId\":103}')",sourceTask,run,ns,source,sourceTask.toString());
        jdbc.update("UPDATE github_sync_task SET fixed_commit_sha=?,path_checkpoint='saved-path' WHERE kind='REPOSITORY_SYNC' AND source_id IS NULL","a".repeat(40));
        for(String phase:List.of("disabled","resumed","excluded","unexcluded")) {
            jdbc.update("UPDATE github_deep_discovery_config SET enabled=?,exclusions_json=?::jsonb WHERE id",!phase.equals("disabled"),phase.equals("excluded")?"[{\"kind\":\"OWNER\",\"value\":\"fixture\"}]":"[]");
            resetRepositoryTasks();
            Set<Long> expected=("disabled".equals(phase)||"excluded".equals(phase))?Set.of(102L,103L):Set.of(101L,102L,103L);
            assertThat(claimRepositories()).containsExactlyInAnyOrderElementsOf(expected);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM github_sync_task WHERE path_checkpoint='saved-path'",Integer.class)).isEqualTo(2);
        }
        jdbc.update("UPDATE github_source_binding SET status='PAUSED' WHERE id=?",source);resetRepositoryTasks();
        assertThat(claimRepositories()).containsExactlyInAnyOrder(101L,102L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_source_binding WHERE namespace_id=?",Integer.class,ns)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_sync_task WHERE kind='REPOSITORY_SYNC'",Integer.class)).isEqualTo(3);
    }

    @Test void depthThirtyTwoAndCumulativeChildren126AreEnforced() throws Exception {
        var lease=claim();UUID query=query(lease);var official=snapshot(lease);
        UUID root=jdbc.queryForObject("SELECT id FROM github_official_discovery_shard WHERE task_id=?",UUID.class,lease.task().taskId());
        jdbc.update("UPDATE github_official_discovery_shard SET task_id=NULL,state='SPLIT' WHERE id=?",root);
        UUID parent=root;JsonNode remaining=official;
        for(int depth=1;depth<=31;depth++) {
            Instant since=Instant.parse(remaining.path("range").path("since").asText());
            Instant until=Instant.parse(remaining.path("range").path("until").asText());
            insertShard(query,root,parent,depth,withRange(remaining,since,since.plusSeconds(1)),"COMPLETE",null);
            remaining=withRange(remaining,since.plusSeconds(1),until);
            parent=insertShard(query,root,parent,depth,remaining,depth==31?"ACTIVE":"SPLIT",depth==31?lease.task().taskId():null);
        }
        shards.split(lease.task().taskId(),query,remaining,halves(remaining));
        assertThat(jdbc.queryForObject("SELECT max(depth) FROM github_official_discovery_shard WHERE root_id=?",Integer.class,root)).isEqualTo(32);
        UUID deepest=jdbc.queryForObject("SELECT id FROM github_official_discovery_shard WHERE depth=32 LIMIT 1",UUID.class);
        jdbc.update("UPDATE github_official_discovery_shard SET task_id=NULL WHERE task_id=?",lease.task().taskId());
        jdbc.update("UPDATE github_official_discovery_shard SET task_id=?,state='ACTIVE' WHERE id=?",lease.task().taskId(),deepest);
        var deepestSnapshot=mapper.readTree(jdbc.queryForObject("SELECT snapshot_json::text FROM github_official_discovery_shard WHERE id=?",String.class,deepest));
        assertThatThrownBy(()->shards.split(lease.task().taskId(),query,deepestSnapshot,halves(deepestSnapshot))).isInstanceOf(GithubOfficialProtocolException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_official_discovery_shard WHERE root_id=?",Integer.class,root)).isEqualTo(65);
    }

    @Test void cumulativeChildrenBudgetRejects127thAnd128thNodes() throws Exception {
        var lease=claim();UUID query=query(lease);var official=snapshot(lease);
        UUID root=jdbc.queryForObject("SELECT id FROM github_official_discovery_shard WHERE task_id=?",UUID.class,lease.task().taskId());
        jdbc.update("UPDATE github_official_discovery_shard SET state='SPLIT',task_id=NULL WHERE id=?",root);
        record Node(UUID id,int depth,JsonNode snapshot) {}
        var queue=new java.util.ArrayDeque<Node>();queue.add(new Node(root,0,official));
        for(int index=0;index<62;index++) {
            var parent=queue.removeFirst();
            jdbc.update("UPDATE github_official_discovery_shard SET state='SPLIT' WHERE id=?",parent.id());
            for(var range:halves(parent.snapshot())) {
                JsonNode childSnapshot=withRange(parent.snapshot(),range.since(),range.until());
                UUID child=insertShard(query,root,parent.id(),parent.depth()+1,childSnapshot,"COMPLETE",null);
                queue.add(new Node(child,parent.depth()+1,childSnapshot));
            }
        }
        var selected=queue.removeFirst();UUID leaf=selected.id();
        jdbc.update("UPDATE github_official_discovery_shard SET state='ACTIVE',task_id=? WHERE id=?",lease.task().taskId(),leaf);
        shards.split(lease.task().taskId(),query,selected.snapshot(),halves(selected.snapshot()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_official_discovery_shard WHERE root_id=? AND parent_id IS NOT NULL",Integer.class,root)).isEqualTo(126);
        UUID child=jdbc.queryForObject("SELECT id FROM github_official_discovery_shard WHERE parent_id=? LIMIT 1",UUID.class,leaf);
        jdbc.update("UPDATE github_official_discovery_shard SET task_id=NULL WHERE task_id=?",lease.task().taskId());
        jdbc.update("UPDATE github_official_discovery_shard SET task_id=?,state='ACTIVE' WHERE id=?",lease.task().taskId(),child);
        var childSnapshot=mapper.readTree(jdbc.queryForObject("SELECT snapshot_json::text FROM github_official_discovery_shard WHERE id=?",String.class,child));
        assertThatThrownBy(()->shards.split(lease.task().taskId(),query,childSnapshot,halves(childSnapshot))).isInstanceOf(GithubOfficialProtocolException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_official_discovery_shard WHERE root_id=? AND parent_id IS NOT NULL",Integer.class,root)).isEqualTo(126);
    }

    @Test void deletingDiagnosticTaskPreservesGapBackoffCoverageAndCheckpoint() throws Exception {
        var rootLease=claim();UUID query=query(rootLease);onlyDue(query);
        var original=snapshot(rootLease);String split=checkpoint(rootLease,"SPLIT");
        report(rootLease,null,split,"SPLIT",halves(original));
        lifecycle.complete(rootLease.task().taskId(),rootLease.task().leaseGeneration(),rootLease.token(),split,"COMPLETE",null);
        var gapLease=claim();String cp=checkpoint(gapLease,"GAP");report(gapLease,null,cp,"GAP",List.of());
        Instant retry=Instant.now().plusSeconds(600);
        lifecycle.complete(gapLease.task().taskId(),gapLease.task().leaseGeneration(),gapLease.token(),cp,"INCOMPLETE",retry);
        var sibling=claim();String done=checkpoint(sibling,"COMPLETE");report(sibling,null,done,"COMPLETE",List.of());
        lifecycle.complete(sibling.task().taskId(),sibling.task().leaseGeneration(),sibling.token(),done,"COMPLETE",null);
        UUID gap=jdbc.queryForObject("SELECT id FROM github_official_discovery_shard WHERE task_id=?",UUID.class,gapLease.task().taskId());
        jdbc.update("DELETE FROM github_sync_task WHERE id=?",gapLease.task().taskId());
        assertThat(jdbc.queryForObject("SELECT task_id FROM github_official_discovery_shard WHERE id=?",UUID.class,gap)).isNull();
        assertThat(jdbc.queryForObject("SELECT gap_checkpoint FROM github_official_discovery_shard WHERE id=?",String.class,gap)).isEqualTo(cp);
        assertThat(jdbc.queryForObject("SELECT retry_at FROM github_official_discovery_shard WHERE id=?",java.sql.Timestamp.class,gap).toInstant()).isCloseTo(retry,org.assertj.core.api.Assertions.within(1,java.time.temporal.ChronoUnit.MICROS));
        assertThat(scheduler.enqueueDue(retry.minusSeconds(1),1)).isZero();
        assertThat(jdbc.queryForObject("SELECT covered_until FROM github_deep_discovery_query WHERE id=?",java.sql.Timestamp.class,query)).isNull();
        assertThat(scheduler.enqueueDue(retry.plusSeconds(1),1)).isEqualTo(1);
        UUID replacement=jdbc.queryForObject("SELECT task_id FROM github_official_discovery_shard WHERE id=?",UUID.class,gap);
        assertThat(replacement).isNotEqualTo(gapLease.task().taskId());
        JsonNode saved=mapper.readTree(jdbc.queryForObject("SELECT discovery_checkpoint FROM github_sync_task WHERE id=?",String.class,replacement));
        assertThat(saved.path("shardId").asText()).isEqualTo(replacement.toString());
        assertThat(saved.path("nextPage").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT lease_generation FROM github_sync_task WHERE id=?",Long.class,replacement)).isEqualTo(gapLease.task().leaseGeneration());
        assertThat(jdbc.queryForObject("SELECT (snapshot_json#>>'{officialDiscovery,budget,maxChildShards}')::integer FROM github_sync_task WHERE id=?",Integer.class,replacement)).isEqualTo(original.path("budget").path("maxChildShards").asInt()-2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM github_official_discovery_shard WHERE query_id=?",Integer.class,query)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT covered_until FROM github_deep_discovery_query WHERE id=?",java.sql.Timestamp.class,query)).isNull();
    }

    private GithubSyncLeaseService.LeasedTask claim() {
        assertThat(scheduler.enqueueDue(Instant.now(),1)).isEqualTo(1);
        jdbc.update("UPDATE github_official_discovery_budget SET next_lease_eligible_at=clock_timestamp()-INTERVAL '1 second'");
        return credentialClaims.claim(GithubSyncTaskKind.DISCOVERY,"recovery",platform(GithubOfficialDiscoveryCatalog.REQUIRED_CAPABILITIES)).orElseThrow().lease();
    }
    private UUID query(GithubSyncLeaseService.LeasedTask lease) throws Exception { return UUID.fromString(snapshot(lease).path("queryId").asText()); }
    private JsonNode snapshot(GithubSyncLeaseService.LeasedTask lease) throws Exception { return mapper.readTree(lease.task().snapshotJson()).path("officialDiscovery"); }
    private String checkpoint(GithubSyncLeaseService.LeasedTask lease,String coverage) throws Exception {
        var cp=mapper.createObjectNode().put("formatVersion",1).put("nextPage",2).put("coverage",coverage);
        for(String field:List.of("strategyVersion","queryId","queryVersion","shardId")) cp.set(field,snapshot(lease).get(field));
        return cp.toString();
    }
    private List<GithubOfficialDiscoveryBatch.Range> halves(JsonNode snapshot) {
        Instant since=Instant.parse(snapshot.path("range").path("since").asText()),until=Instant.parse(snapshot.path("range").path("until").asText());
        Instant middle=since.plusSeconds(java.time.Duration.between(since,until).getSeconds()/2);
        return List.of(new GithubOfficialDiscoveryBatch.Range(since,middle,null),new GithubOfficialDiscoveryBatch.Range(middle,until,null));
    }
    private void report(GithubSyncLeaseService.LeasedTask lease,String previous,String cp,String state,List<GithubOfficialDiscoveryBatch.Range> children) {
        lifecycle.report(lease.task().taskId(),lease.task().leaseGeneration(),lease.token(),new GithubDiscoveryBatch(previous,cp,List.of(),0,null),
                new GithubOfficialDiscoveryBatch(lease.task().taskId(),state,1,children,"GAP".equals(state)?"UNSPLITTABLE":null));
    }
    private void onlyDue(UUID query) { jdbc.update("UPDATE github_deep_discovery_query SET next_due_at=CASE WHEN id=? THEN CURRENT_TIMESTAMP-INTERVAL '1 second' ELSE CURRENT_TIMESTAMP+INTERVAL '2 days' END",query); }
    private long namespace() { return jdbc.queryForObject("SELECT namespace_id FROM github_deep_discovery_config WHERE id",Long.class); }
    private void resetRepositoryTasks() { jdbc.update("UPDATE github_sync_task SET status='QUEUED',lease_token_hash=NULL,lease_expires_at=NULL,leased_by=NULL,due_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE kind='REPOSITORY_SYNC'"); }
    private Set<Long> claimRepositories() {
        Set<Long> result=new java.util.HashSet<>();
        for(int i=0;i<4;i++) {
            var lease=leases.claim(GithubSyncTaskKind.REPOSITORY_SYNC,"origin-worker",platform(List.of("official-pause/v1")));
            if(lease.isEmpty()) return result;
            try { result.add(mapper.readTree(lease.get().task().snapshotJson()).path("repositoryId").asLong()); }
            catch(Exception error) { throw new AssertionError(error); }
        }
        throw new AssertionError("Unexpected duplicate repository lease");
    }
    private JsonNode withRange(JsonNode snapshot,Instant since,Instant until) {
        var result=(com.fasterxml.jackson.databind.node.ObjectNode)snapshot.deepCopy();
        result.putObject("range").put("since",since.toString()).put("until",until.toString());
        return result;
    }
    private UUID insertShard(UUID query,UUID root,UUID parent,int depth,JsonNode snapshot,String state,UUID task) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO github_official_discovery_shard(id,query_id,root_id,parent_id,depth,range_since,range_until,snapshot_json,state,task_id) VALUES (?,?,?,?,?,?::timestamptz,?::timestamptz,?::jsonb,?,?)",
                id,query,root,parent,depth,snapshot.path("range").path("since").asText(),snapshot.path("range").path("until").asText(),snapshot.toString(),state,task);
        return id;
    }
}
