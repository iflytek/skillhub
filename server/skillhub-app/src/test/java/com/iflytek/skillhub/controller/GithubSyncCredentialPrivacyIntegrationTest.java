package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.support.PostgresTestImage;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.githubsync.GithubDiscoveryEntryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "skillhub.builtin-skills.enabled=false",
        "skillhub.github-sync.service-tokens=fixture-service-token-for-privacy-check-32-chars"
})
class GithubSyncCredentialPrivacyIntegrationTest {
    private static final String CREDENTIALS = "/api/v1/admin/github-sync/credentials";
    private static final String CLAIM = "/internal/github-sync/v1/tasks/claim";
    private static final String SERVICE_TOKEN = "fixture-service-token-for-privacy-check-32-chars";
    private static final byte[] CREDENTIAL_KEY = new byte[32];

    static {
        new SecureRandom().nextBytes(CREDENTIAL_KEY);
    }

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PostgresTestImage.name());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("skillhub.github-sync.credential-keys",
                () -> "v1=" + Base64.getEncoder().encodeToString(CREDENTIAL_KEY));
        registry.add("skillhub.github-sync.active-credential-key-version", () -> "v1");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GithubDiscoveryEntryRepository discoveryEntries;
    @Autowired private MeterRegistry meterRegistry;
    @LocalServerPort private int port;

    @Test
    void credentialRemainsInInternalLeaseAndOutOfOrdinaryOutputs() throws Exception {
        String token = "ghp_" + UUID.randomUUID().toString().replace("-", "");
        String contentMarker = "SKILL_BODY_" + UUID.randomUUID().toString().replace("-", "");
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        try {
            var admin = authentication(new UsernamePasswordAuthenticationToken(
                    new PlatformPrincipal("github-sync-publisher", "privacy-admin", null,
                            null, null, Set.of("SUPER_ADMIN")), null,
                    List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
            String created = mockMvc.perform(post(CREDENTIALS).with(admin).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(Map.of("label", "Privacy fixture", "token", token))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String listed = mockMvc.perform(get(CREDENTIALS).with(admin))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(created).doesNotContain(token, "ciphertext", "nonce");
            assertThat(listed).doesNotContain(token, "ciphertext", "nonce");

            HttpClient client = HttpClient.newHttpClient();
            String baseUrl = "http://127.0.0.1:" + port;
            var ordinary = client.send(HttpRequest.newBuilder(URI.create(baseUrl
                            + "/api/v1/admin/github-sync/discovery-entries?unexpected=" + contentMarker))
                    .header("User-Agent", contentMarker)
                    .header("Authorization", "Bearer " + token).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(ordinary.statusCode()).isEqualTo(401);
            assertThat(ordinary.body()).doesNotContain(token, contentMarker);
            String malformed = "{\"token\":\"" + token + "\",\"body\":\"" + contentMarker + "\"";
            String error = mockMvc.perform(post(CREDENTIALS).with(admin).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(malformed))
                    .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
            assertThat(error).doesNotContain(token, contentMarker);
            var internalError = client.send(HttpRequest.newBuilder(URI.create(baseUrl + CLAIM))
                    .header("Authorization", "Bearer " + SERVICE_TOKEN)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(malformed)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(internalError.statusCode()).isEqualTo(400);
            assertThat(internalError.body()).doesNotContain(token, contentMarker);

            var entry = discoveryEntries.createCuratedRepository("privacy-owner", "privacy-repo");
            assertThat(discoveryEntries.enqueueDue(Instant.now(), 10)).isEqualTo(1);
            var claim = client.send(HttpRequest.newBuilder(URI.create(baseUrl + CLAIM))
                    .header("Authorization", "Bearer " + SERVICE_TOKEN)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"workerId\":\"privacy-worker\",\"protocolMajor\":1,\"capabilities\":[\"platform-pause/v1\"],\"kind\":\"DISCOVERY\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(claim.statusCode()).isEqualTo(200);
            String lease = claim.body();
            assertThat(lease).contains(token, entry.id().toString());
            UUID taskId = UUID.fromString(mapper.readTree(lease).path("taskId").asText());
            String task = jdbc.queryForObject("""
                    SELECT row_to_json(task)::text FROM (
                        SELECT snapshot_json, credential_id, credential_fingerprint,
                               last_error_code, discovery_checkpoint
                        FROM github_sync_task WHERE id = ?
                    ) task
                    """, String.class, taskId);
            assertThat(task).doesNotContain(token, contentMarker);
            String audit = jdbc.queryForObject("""
                    SELECT detail_json::text FROM audit_log
                    WHERE action = 'CREATE_GITHUB_CREDENTIAL'
                    ORDER BY id DESC LIMIT 1
                    """, String.class);
            assertThat(audit).doesNotContain(token, contentMarker);

            String logText = logs.list.stream().map(event -> event.getFormattedMessage()
                    + (event.getThrowableProxy() == null ? ""
                    : ThrowableProxyUtil.asString(event.getThrowableProxy()))
                    + event.getMDCPropertyMap()).collect(Collectors.joining("\n"));
            assertThat(logText).doesNotContain(token, contentMarker, SERVICE_TOKEN);
            String metricIds = meterRegistry.getMeters().stream().map(meter -> meter.getId().toString())
                    .collect(Collectors.joining("\n"));
            assertThat(metricIds).doesNotContain(token, contentMarker, SERVICE_TOKEN);
        } finally {
            root.detachAppender(logs);
            logs.stop();
        }
    }
}
