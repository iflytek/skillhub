package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.support.PostgresTestImage;
import static com.iflytek.skillhub.support.GithubSyncWorkerCapabilities.platform;

import com.iflytek.skillhub.githubsync.*;
import com.iflytek.skillhub.repository.GithubDeepDiscoveryQueryRepository;
import com.iflytek.skillhub.repository.GithubSyncWorkerPresenceRepository;
import com.iflytek.skillhub.service.GithubDeepDiscoveryReadAppService;
import com.iflytek.skillhub.dto.GithubDeepDiscoveryReadResponse.*;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.time.Instant;
import java.util.List;
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
class GithubDeepDiscoveryReadModelIntegrationTest {
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
    @Autowired GithubDeepDiscoveryQueryRepository queries;
    @Autowired GithubDeepDiscoveryReadAppService reads;
    @Autowired GithubSyncWorkerPresenceRepository presence;
    private long namespace;

    @BeforeEach void setup() {
        jdbc.update("UPDATE github_deep_discovery_config SET enabled=TRUE WHERE id");
        jdbc.update("UPDATE github_sync_admission_policy SET global_high=10000,global_low=5000,namespace_high=10000,namespace_low=5000 WHERE id");
        namespace=jdbc.queryForObject("SELECT namespace_id FROM github_deep_discovery_config WHERE id",Long.class);
    }

    @Test void exclusionProjectionMatchesOriginalViewAfterRenamesAndPolicyChanges() throws Exception {
        String migration = new org.springframework.core.io.ClassPathResource(
                "db/migration/V111__github_official_candidate_controls.sql")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String original = migration.substring(migration.indexOf("WITH origin_state"));
        original = original.substring(0, original.lastIndexOf(';'));
        long other = otherNamespace();
        for (long repo : List.of(700L, 701L, 702L, 703L, 704L)) candidate(namespace, repo, repo != 704);
        candidate(other, 700, true); // Missing configuration produces the same nullable source fields.
        UUID entry = independent(namespace, 701);
        source(namespace, 702, "ACTIVE");
        source(namespace, 703, "PAUSED");
        for (int step = 0; step < 7; step++) {
            var expected = jdbc.queryForList("SELECT * FROM (" + original + ") original ORDER BY candidate_id");
            var actual = jdbc.queryForList("SELECT * FROM github_candidate_discovery_eligibility ORDER BY candidate_id");
            assertThat(actual).as("complete eligibility rows at step %s", step).isEqualTo(expected);
            assertThat(actual.getFirst().keySet()).containsExactly("candidate_id", "namespace_id", "repository_id",
                    "source_id", "source_status", "has_official", "has_independent", "official_only",
                    "configuration_enabled", "excluded", "acquisition_allowed");
            switch (step) {
                case 0 -> jdbc.update("UPDATE github_deep_discovery_config SET exclusions_json='[{\"kind\":\"OWNER\",\"value\":\"renamed\"},{\"kind\":\"REPOSITORY\",\"value\":\"fixture/updated\"},{}]' WHERE id");
                case 1 -> jdbc.update("UPDATE github_discovery_candidate SET owner_login='ReNamed' WHERE repository_id IN (700,702,703)");
                case 2 -> jdbc.update("UPDATE github_discovery_candidate SET repository_name='Updated' WHERE namespace_id=? AND repository_id=701", namespace);
                case 3 -> jdbc.update("UPDATE github_discovery_entry SET enabled=FALSE WHERE id=?", entry);
                case 4 -> jdbc.update("UPDATE github_deep_discovery_config SET enabled=FALSE,exclusions_json='[]' WHERE id");
                case 5 -> jdbc.update("UPDATE github_deep_discovery_config SET enabled=TRUE,exclusions_json='[{\"kind\":\"OWNER\",\"value\":null},{\"kind\":null,\"value\":\"renamed\"}]' WHERE id");
                default -> { }
            }
        }
        java.util.function.Function<String, List<String>> shape = sql -> jdbc.query(sql, rs -> {
            var metadata = rs.getMetaData();
            var columns = new java.util.ArrayList<String>();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                columns.add(metadata.getColumnName(i) + ":" + metadata.getColumnType(i));
            }
            return columns;
        });
        assertThat(shape.apply("SELECT * FROM github_candidate_discovery_eligibility WHERE FALSE"))
                .isEqualTo(shape.apply("SELECT * FROM (" + original + ") original WHERE FALSE"));
    }

    @Test void targetedRepositoryMatchesListEligibilityAcrossNamespacesAndAllOrigins() {
        long other=otherNamespace();
        for(long repo:List.of(100L,101L,102L,103L)) candidate(namespace,repo,true);
        candidate(other,100,true);
        independent(namespace,101);
        source(namespace,102,"ACTIVE");source(namespace,103,"PAUSED");
        jdbc.update("UPDATE github_discovery_candidate SET status='BOUND' WHERE namespace_id=? AND repository_id IN (102,103)",namespace);
        candidate(namespace,104,false);
        for(int step=0;step<3;step++) {
            var all=queries.repositories(namespace,0,20,Instant.now(),null,null,null,null).items();
            assertThat(all).hasSize(4);
            for(var expected:all) assertThat(queries.repository(namespace,expected.repositoryId())).isEqualTo(expected);
            assertThat(queries.repository(namespace,101).paused()).isFalse();
            assertThat(queries.repository(namespace,102).paused()).isFalse();
            assertThat(queries.repository(namespace,103).paused()).isTrue();
            if(step>0) assertThat(queries.repository(namespace,100).paused()).isTrue();
            assertThat(queries.repository(other,100).candidateId()).isNotEqualTo(queries.repository(namespace,100).candidateId());
            assertThatThrownBy(() -> queries.repository(other,101)).isInstanceOf(DomainNotFoundException.class);
            assertThatThrownBy(() -> queries.repository(namespace,104)).isInstanceOf(DomainNotFoundException.class);
            if(step==0) jdbc.update("UPDATE github_deep_discovery_config SET enabled=FALSE WHERE id");
            else jdbc.update("UPDATE github_deep_discovery_config SET enabled=TRUE,exclusions_json='[{\"kind\":\"OWNER\",\"value\":\"fixture\"}]' WHERE id");
        }
    }

    @Test void existenceShortCircuitKeepsNamespaceAndIndependentAndBoundSemantics() {
        long other=otherNamespace();candidate(other,200,true);
        assertOfficialOnly(false);candidate(namespace,200,false);assertOfficialOnly(false);
        jdbc.update("INSERT INTO github_discovery_candidate_origin(candidate_id,origin_type,origin_key) SELECT id,'OFFICIAL_DISCOVERY',? FROM github_discovery_candidate WHERE namespace_id=?",UUID.randomUUID().toString(),namespace);
        assertOfficialOnly(true);UUID entry=independent(namespace,200);assertOfficialOnly(false);
        jdbc.update("UPDATE github_discovery_entry SET enabled=FALSE WHERE id=?",entry);assertOfficialOnly(true);
        source(namespace,200,"PAUSED");assertOfficialOnly(false);
        assertThat(queries.hasOfficialOnlyCandidates(other)).isTrue();
    }

    @Test void channelFastPathEqualsOriginalAcrossTaskLeaseControlAndRepositoryWork() {
        long other=otherNamespace();
        UUID search=officialTask(Channel.REPOSITORY_METADATA);officialTask(Channel.README);UUID owner=officialTask(Channel.OWNER_EXPANSION);
        jdbc.update("UPDATE github_sync_task SET snapshot_json='{\"repositoryId\":399}' WHERE id=?",search);
        candidate(namespace,300,true);long source=source(namespace,300,"ACTIVE");repositoryTask(300,source);
        for(int stage=0;stage<7;stage++) {
            assertEquivalentProgress(namespace);assertEquivalentProgress(other);
            switch(stage) {
                case 0 -> jdbc.update("UPDATE github_deep_discovery_config SET exclusions_json='[{\"kind\":\"OWNER\",\"value\":\"fixture-owner\"}]' WHERE id");
                case 1 -> jdbc.update("UPDATE github_deep_discovery_config SET enabled=FALSE WHERE id");
                case 2 -> { jdbc.update("UPDATE github_deep_discovery_config SET enabled=TRUE,exclusions_json='[]' WHERE id");jdbc.update("UPDATE github_sync_task SET lease_expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE id=?",owner); }
                case 3 -> jdbc.update("UPDATE github_sync_task SET last_progress_at=CURRENT_TIMESTAMP-INTERVAL '91 seconds'");
                case 4 -> jdbc.update("UPDATE github_sync_task SET last_progress_at=CURRENT_TIMESTAMP,lease_expires_at=CURRENT_TIMESTAMP+INTERVAL '1 hour'");
                case 5 -> jdbc.update("UPDATE github_sync_task SET status='RETRY_WAIT',lease_expires_at=NULL,lease_token_hash=NULL,leased_by=NULL,official_pause_reason='BUDGET'");
                default -> { }
            }
        }
    }

    @Test void workerCapabilitiesBudgetAndPauseFeedbackStayAuthoritative() {
        officialTask(Channel.REPOSITORY_METADATA);officialTask(Channel.OWNER_EXPANSION);
        assertThat(reads.progress().channels()).allSatisfy(c -> assertThat(c.waits()).anyMatch(w -> w.reason().equals("WORKER_UNAVAILABLE")));
        presence.heartbeat("search",1,platform(GithubOfficialDiscoveryCatalog.REQUIRED_CAPABILITIES));
        assertThat(channel(Channel.REPOSITORY_METADATA).state()).isEqualTo("RUNNING");
        assertThat(channel(Channel.OWNER_EXPANSION).waits()).anyMatch(w -> w.reason().equals("CAPABILITY_UNAVAILABLE"));
        jdbc.update("UPDATE github_official_discovery_budget SET next_lease_eligible_at=CURRENT_TIMESTAMP+INTERVAL '1 hour'");
        assertThat(channel(Channel.REPOSITORY_METADATA).waits()).anySatisfy(w -> {
            assertThat(w.reason()).isEqualTo("BUDGET");assertThat(w.continuingWork()).contains("NEW_DISCOVERY");
        });
        presence.heartbeat("owner",1,platform(GithubOfficialDiscoveryCatalog.OWNER_REQUIRED_CAPABILITIES));
        assertThat(channel(Channel.OWNER_EXPANSION).state()).isEqualTo("RUNNING");
        jdbc.update("UPDATE github_deep_discovery_config SET enabled=FALSE WHERE id");
        assertThat(reads.progress().channels()).allSatisfy(c -> {assertThat(c.state()).isEqualTo("DISABLED");assertThat(c.waits()).allSatisfy(w -> assertThat(w.continuingWork()).doesNotContain("NEW_DISCOVERY"));});
    }

    @Test void paginatedCandidateProjectionMatchesOriginalForFiltersAndStableOrdering() {
        long other=otherNamespace();
        UUID metadataTask=officialTask(Channel.REPOSITORY_METADATA),readmeTask=officialTask(Channel.README);
        UUID metadata=jdbc.queryForObject("SELECT official_query_id FROM github_sync_task WHERE id=?",UUID.class,metadataTask);
        UUID readme=jdbc.queryForObject("SELECT official_query_id FROM github_sync_task WHERE id=?",UUID.class,readmeTask);
        String[] states={"PENDING","VALIDATING","BOUND","REJECTED","REJECTED","UNREACHABLE"};
        for(int i=0;i<42;i++) {
            long repo=1000+i,id=candidate(namespace,repo,true);
            UUID validationTask=null;
            if (states[i%states.length].equals("VALIDATING")) {
                validationTask=UUID.randomUUID();
                jdbc.update("INSERT INTO github_sync_task(id,run_id,namespace_id,kind,mode,dedupe_key,snapshot_json) VALUES (?,?,?,'REPOSITORY_SYNC','FULL',?,'{}')",
                        validationTask,run("REPOSITORY_SYNC"),namespace,validationTask.toString());
            }
            jdbc.update("UPDATE github_discovery_candidate SET status=?,repository_name=?,last_error_code=?,validation_task_id=? WHERE id=?",
                    states[i%states.length],"skills-"+i,i%states.length==3?"NO_VALID_SKILL":null,validationTask,id);
            jdbc.update("INSERT INTO github_discovery_candidate_origin(candidate_id,origin_type,origin_key) VALUES (?,'OFFICIAL_DISCOVERY',?)",id,metadata.toString());
            if(i%3==0) jdbc.update("INSERT INTO github_discovery_candidate_origin(candidate_id,origin_type,origin_key) VALUES (?,'OFFICIAL_DISCOVERY',?)",id,readme.toString());
            if(i%states.length==2) source(namespace,repo,i%12==2?"PAUSED":"ACTIVE");
            if(i%5==0) independent(namespace,repo);
        }
        candidate(other,1000,true);candidate(namespace,2000,false);
        long future=candidate(namespace,2001,true);
        jdbc.update("UPDATE github_discovery_candidate SET first_seen_at=CURRENT_TIMESTAMP+INTERVAL '1 day' WHERE id=?",future);
        jdbc.update("UPDATE github_deep_discovery_config SET exclusions_json='[{\"kind\":\"OWNER\",\"value\":\"fixture\"}]' WHERE id");
        Instant asOf=Instant.now();
        var filters=new java.util.ArrayList<ListFilter>();filters.add(new ListFilter(null,null,null));
        for(var status:RepositoryStatus.values()) filters.add(new ListFilter(null,status,null));
        for(var channel:Channel.values()) filters.add(new ListFilter(null,null,channel));
        for(String search:List.of("  FIXTURE/skills-1  ","skills","missing","   ")) filters.add(new ListFilter(search,null,null));
        filters.add(new ListFilter("skills-1",RepositoryStatus.VALIDATION_FAILED,Channel.README));
        filters.add(new ListFilter("fixture",RepositoryStatus.PENDING,Channel.REPOSITORY_METADATA));
        for(long ns:List.of(namespace,other)) for(Boolean paused:java.util.Arrays.asList(null,true,false))
            for(int page:List.of(0,5,100)) for(var filter:filters) {
                var expected=originalPage(ns,page,7,asOf,filter,paused);
                assertThat(queries.repositories(ns,page,7,asOf,filter.search(),filter.status(),filter.channel(),paused))
                        .as("ns=%s page=%s paused=%s filters=%s",ns,page,paused,filter).isEqualTo(expected);
            }
        var first=queries.repositories(namespace,0,7,asOf,null,null,null,null);
        var next=queries.repositories(namespace,1,7,asOf,null,null,null,null);
        assertThat(first.total()).isEqualTo(42);assertThat(next.total()).isEqualTo(42);
        assertThat(first.items()).extracting(RepositorySummary::candidateId).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(first.items()).extracting(RepositorySummary::candidateId)
                .doesNotContainAnyElementsOf(next.items().stream().map(RepositorySummary::candidateId).toList());
        assertThat(queries.repositories(namespace,0,7,asOf,null,null,null,null)).isEqualTo(first);
    }

    private record ListFilter(String search,RepositoryStatus status,Channel channel) {}
    private DiscoveryPage<RepositorySummary> originalPage(long ns,int page,int size,Instant asOf,ListFilter filter,Boolean paused) {
        var params=new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("ns",ns)
                .addValue("asOf",java.sql.Timestamp.from(asOf)).addValue("limit",size).addValue("offset",page*size)
                .addValue("search",filter.search()==null || filter.search().isBlank()?null:filter.search().trim())
                .addValue("status",filter.status()==null?null:filter.status().name())
                .addValue("channel",filter.channel()==null?null:filter.channel().name()).addValue("paused",paused);
        var template=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc);
        long total=template.queryForObject("SELECT count(*) "+ORIGINAL_LIST_FROM,params,Long.class);
        var rows=template.query("WITH selected AS ("+ORIGINAL_LIST_PROJECTION+ORIGINAL_LIST_FROM
                +" ORDER BY c.first_seen_at DESC,c.id DESC LIMIT :limit OFFSET :offset) "+ORIGINAL_LIST_CHANNELS
                +" ORDER BY s.first_seen_at DESC,s.id DESC",params,(rs,row) -> {
            var values=rs.getArray("channels");
            var channels=values==null?List.<Channel>of():java.util.Arrays.stream((String[])values.getArray())
                    .filter(java.util.Objects::nonNull).map(Channel::valueOf).sorted().toList();
            return new RepositorySummary(rs.getLong("repository_id"),rs.getString("owner_login"),rs.getString("repository_name"),
                    rs.getTimestamp("first_seen_at").toInstant(),rs.getTimestamp("last_discovered_at")==null?null:rs.getTimestamp("last_discovered_at").toInstant(),
                    RepositoryStatus.valueOf(rs.getString("display_status")),channels,rs.getObject("source_id",Long.class),
                    rs.getBoolean("paused"),rs.getString("wait_reason"),rs.getLong("id"));
        });
        return new DiscoveryPage<>(rows,total,page,size,asOf);
    }
    private static final String ORIGINAL_LIST_STATUS="CASE c.status WHEN 'BOUND' THEN 'VALID' WHEN 'REJECTED' THEN CASE WHEN c.last_error_code='NO_VALID_SKILL' THEN 'NO_VALID_SKILL' ELSE 'VALIDATION_FAILED' END ELSE c.status END";
    private static final String ORIGINAL_LIST_FROM="""
        FROM github_discovery_candidate c JOIN github_candidate_discovery_eligibility e ON e.candidate_id=c.id
        WHERE c.namespace_id=:ns AND e.has_official AND c.first_seen_at<=:asOf
          AND (CAST(:search AS text) IS NULL OR strpos(lower(c.owner_login || '/' || c.repository_name),lower(:search))>0)
          AND (CAST(:paused AS boolean) IS NULL OR NOT e.acquisition_allowed=:paused)
          AND (CAST(:channel AS text) IS NULL OR EXISTS(SELECT 1 FROM github_discovery_candidate_origin origin
              JOIN github_deep_discovery_query q ON q.id::text=origin.origin_key
              WHERE origin.candidate_id=c.id AND origin.origin_type='OFFICIAL_DISCOVERY' AND q.definition_json->>'channel'=:channel))
        """+" AND (CAST(:status AS text) IS NULL OR ("+ORIGINAL_LIST_STATUS+")=:status)";
    private static final String ORIGINAL_LIST_PROJECTION="""
        SELECT c.*,e.source_id,NOT e.acquisition_allowed AS paused,
            CASE WHEN e.source_status='PAUSED' THEN 'SOURCE_PAUSED'
              WHEN NOT e.acquisition_allowed AND e.excluded THEN 'EXCLUDED'
              WHEN NOT e.acquisition_allowed THEN 'DISABLED' ELSE NULL END AS wait_reason,
        """+ORIGINAL_LIST_STATUS+" AS display_status ";
    private static final String ORIGINAL_LIST_CHANNELS="""
        SELECT s.*,o.channels,o.last_discovered_at FROM selected s
        LEFT JOIN LATERAL (SELECT array_agg(DISTINCT q.definition_json->>'channel') AS channels,max(o.last_seen_at) AS last_discovered_at
            FROM github_discovery_candidate_origin o JOIN github_deep_discovery_query q ON q.id::text=o.origin_key
            WHERE o.candidate_id=s.id AND o.origin_type='OFFICIAL_DISCOVERY') o ON TRUE
        """;

    private ChannelProgress channel(Channel channel) { return reads.progress().channels().stream().filter(c -> c.channel()==channel).findFirst().orElseThrow(); }
    private void assertOfficialOnly(boolean expected) {
        assertThat(queries.hasOfficialOnlyCandidates(namespace)).isEqualTo(expected);
        assertThat(queries.hasOfficialOnlyCandidates(namespace)).isEqualTo(jdbc.queryForObject("SELECT count(*)>0 FROM github_candidate_discovery_eligibility WHERE namespace_id=? AND has_official AND official_only",Boolean.class,namespace));
    }
    private void assertEquivalentProgress(long ns) {
        var channels=new java.util.ArrayList<Channel>();channels.add(null);channels.addAll(List.of(Channel.values()));
        for(Channel channel:channels) for(Long repo:java.util.Arrays.asList(null,300L,399L)) {
            var params=new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("ns",ns)
                    .addValue("repo",repo==null?null:repo.toString()).addValue("channel",channel==null?null:channel.name());
            var original=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc)
                    .query(ORIGINAL_PROGRESS,params,(rs,row) -> rs.getString("work"));
            assertThat(queries.progressingWork(ns,repo,channel)).as("ns=%s repo=%s channel=%s",ns,repo,channel).containsExactlyInAnyOrderElementsOf(original);
        }
    }
    private long otherNamespace() { return jdbc.queryForObject("INSERT INTO namespace(slug,display_name,type) VALUES ('read-model-other','Other','TEAM') RETURNING id",Long.class); }
    private long candidate(long ns,long repo,boolean official) {
        long id=jdbc.queryForObject("INSERT INTO github_discovery_candidate(namespace_id,repository_id,owner_login,repository_name) VALUES (?,?,'fixture','skills') RETURNING id",Long.class,ns,repo);
        if(official) jdbc.update("INSERT INTO github_discovery_candidate_origin(candidate_id,origin_type,origin_key) VALUES (?,'OFFICIAL_DISCOVERY',?)",id,UUID.randomUUID().toString());
        return id;
    }
    private UUID independent(long ns,long repo) {
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO github_discovery_entry(id,namespace_id,entry_type,title,criteria_json,enabled,target_interval_seconds) VALUES (?, ?,'SEARCH_RULE',?,'{}',TRUE,3600)",id,ns,"fixture-"+repo);
        jdbc.update("INSERT INTO github_discovery_candidate_origin(candidate_id,origin_type,origin_key) SELECT id,'SEARCH_RULE',? FROM github_discovery_candidate WHERE namespace_id=? AND repository_id=?",id.toString(),ns,repo);return id;
    }
    private long source(long ns,long repo,String status) { return jdbc.queryForObject("INSERT INTO github_source_binding(namespace_id,repository_id,owner_login,repository_name,visibility,status) VALUES (?,?,'fixture','skills','PUBLIC',?) RETURNING id",Long.class,ns,repo,status); }
    private UUID officialTask(Channel channel) {
        UUID query=UUID.randomUUID(),task=UUID.randomUUID(),run=run("DISCOVERY");
        jdbc.update("INSERT INTO github_deep_discovery_query(id,namespace_id,query_key,query_version,strategy_version,definition_json) VALUES (?,?,?,1,'fixture',jsonb_build_object('channel',?::text))",query,namespace,query.toString(),channel.name());
        if(channel==Channel.OWNER_EXPANSION) jdbc.update("UPDATE github_deep_discovery_query SET query_source='OWNER',github_owner_id=900,owner_login='fixture-owner',seed_repository_id=300 WHERE id=?",query);
        jdbc.update("INSERT INTO github_sync_task(id,run_id,namespace_id,kind,dedupe_key,snapshot_json,official_query_id,required_capabilities,status,lease_token_hash,lease_expires_at,leased_by,last_progress_at) VALUES (?,?,?,'DISCOVERY',?,'{}',?,ARRAY['official-discovery/v1','official-checkpoint/v1','official-pause/v1'],'LEASED',?,CURRENT_TIMESTAMP+INTERVAL '1 hour','fixture',CURRENT_TIMESTAMP)",task,run,namespace,task.toString(),query,"a".repeat(64));
        jdbc.update("UPDATE github_deep_discovery_query SET active_task_id=? WHERE id=?",task,query);return task;
    }
    private void repositoryTask(long repo,long source) {
        UUID task=UUID.randomUUID(),run=run("REPOSITORY_SYNC");
        jdbc.update("INSERT INTO github_sync_task(id,run_id,namespace_id,source_id,kind,mode,dedupe_key,snapshot_json,status,lease_token_hash,lease_expires_at,leased_by,last_progress_at) VALUES (?,?,?,?,'REPOSITORY_SYNC','FULL',?,jsonb_build_object('repositoryId',?::bigint),'LEASED',?,CURRENT_TIMESTAMP+INTERVAL '1 hour','fixture',CURRENT_TIMESTAMP)",task,run,namespace,source,task.toString(),repo,"b".repeat(64));
    }
    private UUID run(String kind) { UUID id=UUID.randomUUID();jdbc.update("INSERT INTO github_sync_run(id,namespace_id,kind) VALUES (?,?,?)",id,namespace,kind);return id; }
    // Frozen pre-optimization SQL is an equivalence oracle, not a new production path.
    private static final String ORIGINAL_PROGRESS = """
            WITH tasks AS (
              SELECT t.kind,t.mode,COALESCE(e.source_id,t.source_id) source_id FROM github_sync_task t
              LEFT JOIN github_discovery_candidate c ON c.namespace_id=t.namespace_id
                AND c.repository_id::text=t.snapshot_json->>'repositoryId'
              LEFT JOIN github_candidate_discovery_eligibility e ON e.candidate_id=c.id
              LEFT JOIN github_deep_discovery_query q ON q.id=t.official_query_id
              JOIN github_deep_discovery_config config ON config.namespace_id=t.namespace_id
              WHERE t.namespace_id=:ns AND t.status='LEASED' AND t.lease_expires_at>CURRENT_TIMESTAMP
                AND t.last_progress_at>CURRENT_TIMESTAMP-INTERVAL '90 seconds'
                AND (CAST(:repo AS text) IS NULL OR t.snapshot_json->>'repositoryId'=:repo)
                AND (CAST(:channel AS text) IS NULL OR q.definition_json->>'channel'=:channel)
                AND ((t.official_query_id IS NOT NULL AND config.enabled AND NOT EXISTS(
                     SELECT 1 FROM github_official_owner_eligibility oe WHERE oe.query_id=q.id AND oe.excluded))
                  OR (e.has_official AND e.acquisition_allowed))
            ) SELECT DISTINCT CASE WHEN kind='DISCOVERY' THEN 'NEW_DISCOVERY'
                WHEN source_id IS NULL THEN 'CANDIDATE_VALIDATION' ELSE 'SOURCE_UPDATE' END work FROM tasks
              UNION SELECT 'PACKAGE_ACQUISITION' FROM tasks WHERE kind='REPOSITORY_SYNC' AND mode='FULL'
            """;
}
