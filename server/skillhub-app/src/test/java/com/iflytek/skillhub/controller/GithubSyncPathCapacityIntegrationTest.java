package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "skillhub.builtin-skills.enabled=false",
        "skillhub.github-sync.service-tokens=fixture-service-token-for-path-capacity-32-chars"
})
class GithubSyncPathCapacityIntegrationTest {
    private static final String SERVICE_TOKEN = "fixture-service-token-for-path-capacity-32-chars";
    private static final String COMMIT = "a".repeat(40);
    private static final int PATHS = 10_000;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PostgresTestImage.name());

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

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @LocalServerPort private int port;

    @Test
    void serviceContractAcceptsTenThousandRepositoryPathResults() throws Exception {
        long namespaceId = jdbc.queryForObject("""
                SELECT id FROM namespace WHERE slug = 'github' AND type = 'CATALOG'
                """, Long.class);
        jdbc.update("""
                UPDATE github_sync_admission_policy
                SET global_high = 20000, global_low = 10000,
                    namespace_high = 20000, namespace_low = 10000
                WHERE id = TRUE
                """);
        long sourceId = jdbc.queryForObject("""
                INSERT INTO github_source_binding
                    (namespace_id, repository_id, owner_login, repository_name, visibility)
                VALUES (?, 3000001, 'fixture', 'many-skills', 'PUBLIC') RETURNING id
                """, Long.class, namespaceId);
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO github_sync_run (id, namespace_id, kind)
                VALUES (?, ?, 'REPOSITORY_SYNC')
                """, runId, namespaceId);
        jdbc.update("""
                INSERT INTO github_sync_task
                    (id, run_id, namespace_id, source_id, kind, mode,
                     dedupe_key, snapshot_json, fixed_commit_sha)
                VALUES (?, ?, ?, ?, 'REPOSITORY_SYNC', 'FULL', 'repository:3000001',
                    '{"repositoryId":3000001,"owner":"fixture","name":"many-skills"}'::jsonb, ?)
                """, taskId, runId, namespaceId, sourceId, COMMIT);

        HttpClient client = HttpClient.newHttpClient();
        String baseUrl = "http://127.0.0.1:" + port + "/internal/github-sync/v1/tasks";
        var claim = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/claim"))
                .header("Authorization", "Bearer " + SERVICE_TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"workerId\":\"capacity-worker\",\"protocolMajor\":1,\"capabilities\":[\"platform-pause/v1\"],\"kind\":\"REPOSITORY_SYNC\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(claim.statusCode()).isEqualTo(200);
        var leased = mapper.readTree(claim.body());
        assertThat(leased.path("taskId").asText()).isEqualTo(taskId.toString());
        assertThat(leased.path("mode").asText()).isEqualTo("FULL");

        var completion = mapper.createObjectNode();
        completion.set("lease", leased.path("lease"));
        completion.put("coverage", "COMPLETE");
        completion.put("observedCommitSha", COMMIT);
        completion.put("pathCheckpoint", "skills/skill-09999");
        var results = completion.putArray("pathResults");
        for (int index = 0; index < PATHS; index++) {
            var result = results.addObject();
            result.put("skillPath", "skills/skill-%05d".formatted(index));
            result.put("outcome", "TERMINAL_FAILURE");
            result.put("reason", "PACKAGE_TOO_LARGE");
        }

        byte[] payload = mapper.writeValueAsBytes(completion);
        long started = System.nanoTime();
        var response = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/" + taskId + "/complete"))
                .header("Authorization", "Bearer " + SERVICE_TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build(), HttpResponse.BodyHandlers.ofString());
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).path("status").asText()).isEqualTo("PARTIAL");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_path_result WHERE task_id = ?
                """, Integer.class, taskId)).isEqualTo(PATHS);
        System.out.printf("GitHub path HTTP baseline: paths=%d, requestBytes=%d, elapsedMs=%d%n",
                PATHS, payload.length, elapsedMillis);
    }
}
