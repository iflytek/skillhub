package com.iflytek.skillhub.authoring;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.authoring.SkillDraft;
import com.iflytek.skillhub.domain.authoring.service.SkillDraftService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.util.List;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level authorization for the draft-authoring endpoints: only the draft
 * owner (or a SUPER_ADMIN) may list a draft's files and validation runs.
 * Every case runs against both route prefixes (/api/v1/authoring and
 * /api/web/authoring), covering owner success, admin success and the
 * authenticated non-owner rejection.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestRedisConfig.class, SkillAuthoringControllerAuthorizationTest.InMemoryStorageConfig.class})
@Testcontainers
class SkillAuthoringControllerAuthorizationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PostgresTestImage.name());

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

    private static final String OWNER = "auth-owner";
    private static final String OTHER_USER = "auth-other";
    private static final String ADMIN = "auth-admin";
    private static final String NS_SLUG = "authoring-auth-ns";

    @Autowired private MockMvc mockMvc;
    @Autowired private SkillDraftService draftService;
    @Autowired private NamespaceRepository namespaceRepository;
    @Autowired private NamespaceMemberRepository namespaceMemberRepository;
    @Autowired private UserAccountRepository userAccountRepository;

    @TestConfiguration
    static class InMemoryStorageConfig {

        @Bean
        @Primary
        com.iflytek.skillhub.storage.ObjectStorageService inMemoryObjectStorage() {
            return new AuthoringDraftFlowIntegrationTest.InMemoryObjectStorage();
        }
    }

    @BeforeEach
    void setUpUsersAndNamespace() {
        ensureUser(OWNER);
        ensureUser(OTHER_USER);
        ensureUser(ADMIN);
        if (namespaceRepository.findBySlug(NS_SLUG).isEmpty()) {
            Namespace namespace = new Namespace(NS_SLUG, "Auth Test", OWNER);
            namespace.setStatus(com.iflytek.skillhub.domain.namespace.NamespaceStatus.ACTIVE);
            Namespace saved = namespaceRepository.save(namespace);
            namespaceMemberRepository.save(new NamespaceMember(saved.getId(), OWNER, NamespaceRole.MEMBER));
        }
    }

    private void ensureUser(String userId) {
        if (userAccountRepository.findById(userId).isEmpty()) {
            userAccountRepository.save(new UserAccount(userId, userId, userId + "@example.com", null));
        }
    }

    private static RequestPostProcessor asUser(String userId, String... platformRoles) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId, userId, userId + "@example.com", null, "local", Set.of(platformRoles));
        List<SimpleGrantedAuthority> authorities = platformRoles.length == 0
                ? List.of()
                : List.of(new SimpleGrantedAuthority("ROLE_" + String.join("_", platformRoles)));
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    @Test
    void fileListRequiresOwnershipOnBothPrefixes() throws Exception {
        Long draftId = draftService.createDraft(NS_SLUG, OWNER, "authz-files", null, null).getId();

        for (String prefix : new String[] {"/api/v1/authoring", "/api/web/authoring"}) {
            // the owner may list the files...
            mockMvc.perform(get(prefix + "/drafts/" + draftId + "/files")
                            .with(asUser(OWNER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
            // ...and so may a super admin...
            mockMvc.perform(get(prefix + "/drafts/" + draftId + "/files")
                            .with(asUser(ADMIN, "SUPER_ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
            // ...but another authenticated user may not
            mockMvc.perform(get(prefix + "/drafts/" + draftId + "/files")
                            .with(asUser(OTHER_USER)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void runListRequiresOwnershipOnBothPrefixes() throws Exception {
        SkillDraft draft = draftService.createDraft(NS_SLUG, OWNER, "authz-runs", null, null);

        for (String prefix : new String[] {"/api/v1/authoring", "/api/web/authoring"}) {
            mockMvc.perform(get(prefix + "/drafts/" + draft.getId() + "/runs")
                            .with(asUser(OWNER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
            mockMvc.perform(get(prefix + "/drafts/" + draft.getId() + "/runs")
                            .with(asUser(ADMIN, "SUPER_ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
            mockMvc.perform(get(prefix + "/drafts/" + draft.getId() + "/runs")
                            .with(asUser(OTHER_USER)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void openApiDescribesBinarySafeDraftContent() throws Exception {
        String schema = mockMvc.perform(get("/v3/api-docs").with(asUser(ADMIN, "SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.DraftFileContentResponse.properties.encoding").exists())
                .andReturn().getResponse().getContentAsString();
        Files.writeString(Path.of("target", "authoring-openapi.json"), schema);
    }
}
