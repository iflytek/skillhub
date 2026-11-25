package com.iflytek.skillhub.controller.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.connection.oidc.EnterpriseOidcProperties;
import com.iflytek.skillhub.auth.connection.oidc.OidcConnectionProbe;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.organization.MembershipSourceType;
import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import com.iflytek.skillhub.domain.organization.OrganizationRole;
import com.iflytek.skillhub.domain.organization.OrganizationRoleBinding;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.search.SearchRebuildService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.support.PostgresTestImage;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "skillhub.builtin-skills.enabled=false",
        "skillhub.bootstrap.admin.enabled=false",
        "skillhub.enterprise.oidc.enabled=true",
        "skillhub.enterprise.secret-envelope.active-key-id=fixture"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@Testcontainers
class EnterpriseConnectionControllerIntegrationTest {
    private static final String FAKE_CREDENTIAL = "fixture-oidc-credential";
    private static final String CONNECTION_CONFIG = """
            {"displayName":"Company login","issuer":"https://idp.example.com","clientId":"skillhub"}
            """;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        // Dedicated test key, deterministic and never used by a running deployment.
        registry.add("skillhub.enterprise.secret-envelope.keys",
                () -> "fixture:" + Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    @Autowired TransactionTemplate transaction;
    @Autowired EnterpriseOidcProperties settings;
    @MockBean ObjectStorageService storage;
    @MockBean SearchRebuildService searchRebuild;
    @MockBean OidcConnectionProbe probe;
    @SpyBean AuditLogService audit;

    @Test
    void separateAdminsConfigureProbeAndActivateWithoutDisclosingCredential() throws Exception {
        Tenant tenant = tenant();
        Tenant outsider = tenant();
        String base = tenant.base();

        submit(tenant.secrets(), base, CONNECTION_CONFIG).andExpect(status().isForbidden());
        submit(tenant.owner(), base, CONNECTION_CONFIG).andExpect(status().isForbidden());
        submit(tenant.identity(), base, CONNECTION_CONFIG.replace(
                "\"clientId\":\"skillhub\"", "\"clientId\":\"skillhub\",\"clientSecret\":\"" + FAKE_CREDENTIAL + "\""))
                .andExpect(status().isBadRequest());

        JsonNode draft = data(submit(tenant.identity(), base, CONNECTION_CONFIG));
        String connectionId = draft.path("id").asText();
        String connection = base + "/" + connectionId;
        String initial = latestId(draft);
        assertThat(draft.path("latestRevision").path("secretConfigured").asBoolean()).isFalse();
        mvc.perform(get(outsider.base() + "/" + connectionId).with(authentication(auth(outsider.identity()))))
                .andExpect(status().isNotFound());
        submit(tenant.identity(), revision(connection, initial) + "/test", null).andExpect(status().isConflict());
        submit(tenant.identity(), revision(connection, initial) + "/secret", secretBody()).andExpect(status().isForbidden());
        submit(tenant.secrets(), revision(connection, initial) + "/secret",
                "{\"clientSecret\":\"" + FAKE_CREDENTIAL + "\",\"issuer\":\"https://other.example.com\"}")
                .andExpect(status().isBadRequest());

        JsonNode wired = data(submit(tenant.secrets(), revision(connection, initial) + "/secret", secretBody()));
        String wiredRevision = latestId(wired);
        assertThat(wiredRevision).isNotEqualTo(initial);
        assertThat(wired.path("latestRevision").path("secretConfigured").asBoolean()).isTrue();
        assertThat(wired.toString()).doesNotContain(FAKE_CREDENTIAL, "secretBindingVersion", "ciphertext");
        submit(tenant.secrets(), revision(connection, initial) + "/secret", secretBody()).andExpect(status().isConflict());
        submit(tenant.secrets(), revision(connection, wiredRevision) + "/test", null).andExpect(status().isForbidden());
        submit(tenant.identity(), revision(connection, wiredRevision) + "/activate", "{\"confirmed\":true}")
                .andExpect(status().isConflict());
        submit(tenant.identity(), revision(connection, wiredRevision) + "/test", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.success").value(true));
        submit(tenant.identity(), revision(connection, wiredRevision) + "/activate", "{\"confirmed\":false}")
                .andExpect(status().isBadRequest());
        submit(tenant.identity(), revision(connection, wiredRevision) + "/activate", "{\"confirmed\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.activeRevisionId").value(wiredRevision));

        JsonNode next = data(submit(tenant.identity(), connection + "/revisions",
                revisionBody(wiredRevision, "https://new-idp.example.com")));
        assertThat(next.path("activeRevisionId").asText()).isEqualTo(wiredRevision);
        assertThat(next.path("latestRevision").path("secretConfigured").asBoolean()).isFalse();
        submit(tenant.identity(), revision(connection, wiredRevision) + "/activate", "{\"confirmed\":true}")
                .andExpect(status().isConflict());
        submit(tenant.secrets(), connection + "/suspend", "{\"confirmed\":true}").andExpect(status().isForbidden());
        submit(tenant.identity(), connection + "/suspend", "{\"confirmed\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUSPENDED"));
        submit(tenant.identity(), connection + "/disable", "{\"confirmed\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DISABLED"));
        submit(tenant.secrets(), revision(connection, latestId(next)) + "/secret", secretBody())
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForList(
                "SELECT ciphertext FROM login_connection_secret_version WHERE connection_id = ?",
                byte[].class, connectionId)).singleElement().satisfies(ciphertext ->
                assertThat(ciphertext).isNotEqualTo(FAKE_CREDENTIAL.getBytes(StandardCharsets.UTF_8)));
        assertThat(jdbc.queryForList("""
                SELECT detail_json::text FROM audit_log WHERE organization_id = ? AND target_ref = ?
                """, String.class, tenant.id(), connectionId)).isNotEmpty().allSatisfy(detail ->
                assertThat(detail).doesNotContain(FAKE_CREDENTIAL, "idp.example.com", "\"skillhub\""));
    }

    @Test
    void resumesPreviouslyActivatedRevisionWhilePreservingFailedDraft() throws Exception {
        Tenant tenant = tenant();
        Tenant outsider = tenant();
        String confirm = "{\"confirmed\":true}";
        JsonNode created = data(submit(tenant.identity(), tenant.base(), CONNECTION_CONFIG));
        String connectionId = created.path("id").asText();
        String connection = tenant.base() + "/" + connectionId;
        String initialRevision = latestId(created);
        assertThat(created.path("activeRevision").isNull()).isTrue();
        submit(tenant.identity(), revision(connection, initialRevision) + "/resume", confirm)
                .andExpect(status().isConflict());

        String active = latestId(data(submit(tenant.secrets(),
                revision(connection, initialRevision) + "/secret", secretBody())));
        submit(tenant.identity(), revision(connection, active) + "/test", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.success").value(true));
        submit(tenant.identity(), revision(connection, active) + "/activate", confirm)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.activeRevision.revision").value(2));

        JsonNode draft = data(submit(tenant.identity(), connection + "/revisions",
                revisionBody(active, "https://new-idp.example.com")));
        String failed = latestId(data(submit(tenant.secrets(),
                revision(connection, latestId(draft)) + "/secret", secretBody())));
        doThrow(new OidcConnectionProbe.Failure(OidcConnectionProbe.Reason.INVALID_METADATA)).when(probe).verify(any());
        submit(tenant.identity(), revision(connection, failed) + "/test", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.success").value(false));

        submit(tenant.identity(), connection + "/suspend", confirm)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUSPENDED"));
        String resume = revision(connection, active) + "/resume";
        submit(tenant.secrets(), resume, confirm).andExpect(status().isForbidden());
        submit(outsider.identity(), revision(outsider.base() + "/" + connectionId, active) + "/resume", confirm)
                .andExpect(status().isNotFound());
        submit(tenant.identity(), resume, "{\"confirmed\":false}").andExpect(status().isBadRequest());
        submit(tenant.identity(), revision(connection, failed) + "/resume", confirm)
                .andExpect(status().isConflict());
        submit(tenant.identity(), revision(connection, failed) + "/activate", confirm)
                .andExpect(status().isConflict());

        JsonNode resumed = data(submit(tenant.identity(), resume, confirm));
        assertThat(resumed.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(resumed.path("activeRevisionId").asText()).isEqualTo(active);
        assertThat(resumed.path("activeRevision").path("id").asText()).isEqualTo(active);
        assertThat(resumed.path("activeRevision").path("issuer").asText()).isEqualTo("https://idp.example.com");
        assertThat(resumed.path("activeRevision").path("secretConfigured").asBoolean()).isTrue();
        assertThat(resumed.path("latestRevision").path("issuer").asText()).isEqualTo("https://new-idp.example.com");
        assertThat(latestId(resumed)).isEqualTo(failed);
        assertThat(resumed.path("health").path("status").asText()).isEqualTo("FAILED");
        assertThat(resumed.toString()).doesNotContain(FAKE_CREDENTIAL, "secretBindingVersion", "ciphertext");
        submit(tenant.identity(), resume, confirm).andExpect(status().isOk());
        assertThat(jdbc.queryForList("""
                SELECT detail_json::text FROM audit_log
                WHERE organization_id = ? AND target_ref = ? AND action = 'LOGIN_CONNECTION_RESUMED'
                """, String.class, tenant.id(), connectionId)).singleElement()
                .satisfies(detail -> assertThat(detail).contains(active, "SUSPENDED", "ACTIVE"));

        submit(tenant.identity(), connection + "/disable", confirm).andExpect(status().isOk());
        submit(tenant.identity(), resume, confirm).andExpect(status().isConflict());
    }

    @Test
    void failedRetestClearsSuccessAndDisabledSwitchKeepsSummaryReadable() throws Exception {
        Tenant tenant = tenant();
        JsonNode draft = data(submit(tenant.identity(), tenant.base(), CONNECTION_CONFIG));
        String connection = tenant.base() + "/" + draft.path("id").asText();
        String withSecret = latestId(data(submit(tenant.secrets(),
                revision(connection, latestId(draft)) + "/secret", secretBody())));
        String test = revision(connection, withSecret) + "/test";
        submit(tenant.identity(), test, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.success").value(true));
        doThrow(new OidcConnectionProbe.Failure(OidcConnectionProbe.Reason.INVALID_METADATA)).when(probe).verify(any());
        submit(tenant.identity(), test, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.failureReason").value("INVALID_METADATA"));
        mvc.perform(get(connection).with(authentication(auth(tenant.identity()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.health.status").value("FAILED"));
        submit(tenant.identity(), revision(connection, withSecret) + "/activate", "{\"confirmed\":true}")
                .andExpect(status().isConflict());

        settings.setEnabled(false);
        try {
            mvc.perform(get(tenant.base() + "/management").with(authentication(auth(tenant.identity()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
            mvc.perform(get(tenant.base()).with(authentication(auth(tenant.identity()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
            submit(tenant.identity(), test, null).andExpect(status().isNotFound());
        } finally {
            settings.setEnabled(true);
        }
    }

    @Test
    void successfulProbeCannotMarkAReplacedRevisionAsCurrent() throws Exception {
        Tenant tenant = tenant();
        JsonNode draft = data(submit(tenant.identity(), tenant.base(), CONNECTION_CONFIG));
        String connectionId = draft.path("id").asText();
        String connection = tenant.base() + "/" + connectionId;
        String tested = latestId(data(submit(tenant.secrets(),
                revision(connection, latestId(draft)) + "/secret", secretBody())));
        var entered = new CountDownLatch(1);
        var allowProbeToFinish = new CountDownLatch(1);
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            entered.countDown();
            assertThat(allowProbeToFinish.await(10, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(probe).verify(any());

        try (var executor = Executors.newSingleThreadExecutor()) {
            var result = executor.submit(() -> submit(tenant.identity(), revision(connection, tested) + "/test", null)
                    .andReturn().getResponse().getStatus());
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                submit(tenant.identity(), connection + "/revisions", revisionBody(tested, "https://other.example.com"))
                        .andExpect(status().isOk());
            } finally {
                allowProbeToFinish.countDown();
            }
            assertThat(result.get(15, TimeUnit.SECONDS)).isEqualTo(409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_operation WHERE connection_id = ?",
                Integer.class, connectionId)).isZero();
    }

    @Test
    void auditFailureRollsBackSecretAndRevision() throws Exception {
        Tenant tenant = tenant();
        JsonNode draft = data(submit(tenant.identity(), tenant.base(), CONNECTION_CONFIG));
        String id = draft.path("id").asText();
        String connection = tenant.base() + "/" + id;
        doThrow(new RuntimeException("test audit failure")).when(audit).recordOrganizationLoginConnection(
                eq(tenant.secrets()), eq("LOGIN_CONNECTION_SECRET_WRITTEN"), eq(tenant.id()), eq(id),
                any(), anyBoolean(), anyString());
        submit(tenant.secrets(), revision(connection, latestId(draft)) + "/secret", secretBody())
                .andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_connection_secret_version WHERE connection_id = ?",
                Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_connection_revision WHERE connection_id = ?",
                Integer.class, id)).isEqualTo(1);
    }

    private Tenant tenant() {
        return transaction.execute(ignored -> {
            String suffix = UUID.randomUUID().toString();
            String owner = "owner-" + suffix;
            String identity = "identity-" + suffix;
            String secrets = "secrets-" + suffix;
            Instant now = Instant.now();
            for (String userId : List.of(owner, identity, secrets)) {
                entities.persist(new UserAccount(userId, userId, userId + "@example.com", ""));
            }
            entities.flush();
            var organization = Organization.create("team-" + suffix, "Company", owner, now);
            entities.persist(organization);
            entities.flush();
            for (String userId : List.of(owner, identity, secrets)) {
                var membership = OrganizationMembership.provisioned(organization.getId(), MembershipSourceType.MANUAL,
                        "manual-" + userId, userId, userId + "@example.com", now);
                membership.activate(userId, now);
                entities.persist(membership);
            }
            entities.flush();
            entities.persist(OrganizationRoleBinding.grant(
                    organization.getId(), owner, OrganizationRole.ORG_OWNER, owner, now));
            entities.persist(OrganizationRoleBinding.grant(
                    organization.getId(), identity, OrganizationRole.IDENTITY_ADMIN, owner, now));
            entities.persist(OrganizationRoleBinding.grant(
                    organization.getId(), secrets, OrganizationRole.LOGIN_SECRET_ADMIN, owner, now));
            return new Tenant(organization.getId(), owner, identity, secrets);
        });
    }

    private ResultActions submit(String userId, String path, String body) throws Exception {
        var request = post(path).with(authentication(auth(userId))).with(csrf());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }

    private JsonNode data(ResultActions response) throws Exception {
        return mapper.readTree(response.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray())
                .path("data");
    }

    private String secretBody() {
        return "{\"clientSecret\":\"" + FAKE_CREDENTIAL + "\"}";
    }

    private static String latestId(JsonNode response) {
        return response.path("latestRevision").path("id").asText();
    }

    private static String revision(String connection, String revisionId) {
        return connection + "/revisions/" + revisionId;
    }

    private static String revisionBody(String revisionId, String issuer) {
        return "{\"expectedRevisionId\":\"" + revisionId + "\",\"issuer\":\"" + issuer
                + "\",\"clientId\":\"skillhub\"}";
    }

    private static UsernamePasswordAuthenticationToken auth(String userId) {
        var principal = new PlatformPrincipal(userId, userId, userId + "@example.com", "", "github", Set.of());
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    private record Tenant(String id, String owner, String identity, String secrets) {
        private String base() {
            return "/api/v1/organizations/" + id + "/login-connections";
        }
    }
}
