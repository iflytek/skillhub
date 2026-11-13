package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.SkillhubApplication;
import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.search.SearchEmbeddingService;
import com.iflytek.skillhub.support.PostgresTestImage;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the full filter chain keeps team-only automatic publication grants session-only.
 *
 * <p>Member mutations take PostgreSQL row locks, and this also validates the V125 schema against
 * the JPA mappings after Flyway migrates an empty database.
 */
@SpringBootTest(classes = SkillhubApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@Testcontainers
class TeamAutoPublicationGrantSecurityTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());

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

    @MockBean private SearchEmbeddingService searchEmbeddingService;

    private static final String EVERY_CURRENT_SCOPE =
            "[\"skill:read\",\"skill:publish\",\"skill:delete\",\"token:manage\"]";

    @Autowired private MockMvc mockMvc;
    @Autowired private ApiTokenService apiTokenService;
    @Autowired private UserAccountRepository users;
    @Autowired private NamespaceRepository namespaces;
    @Autowired private NamespaceMemberRepository members;

    private String owner;
    private String publisher;
    private String slug;
    private Long namespaceId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        owner = "grant-owner-" + suffix;
        publisher = "grant-member-" + suffix;
        slug = "grant-" + suffix;
        users.save(new UserAccount(owner, "Owner", owner + "@example.com", null));
        users.save(new UserAccount(publisher, "Publisher", publisher + "@example.com", null));
        Namespace namespace = namespaces.save(new Namespace(slug, "Grant team", owner));
        namespaceId = namespace.getId();
        members.save(new NamespaceMember(namespaceId, owner, NamespaceRole.OWNER));
        members.save(new NamespaceMember(namespaceId, publisher, NamespaceRole.MEMBER));
    }

    @Test
    void ownerTokenWithEveryCurrentScopeCannotGrantOrRevoke() throws Exception {
        String rawToken = apiTokenService.createToken(owner, "grant-" + UUID.randomUUID(), EVERY_CURRENT_SCOPE)
                .rawToken();
        for (String prefix : new String[] {"/api/web", "/api/v1"}) {
            for (boolean granted : new boolean[] {true, false}) {
                mockMvc.perform(put(prefix + "/namespaces/" + slug + "/members/" + publisher + "/team-auto-publication")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"granted\":" + granted + "}"))
                        .andExpect(status().isForbidden());
            }
        }
        assertThat(members.findByNamespaceIdAndUserId(namespaceId, publisher).orElseThrow()
                .isTeamAutoPublicationGranted()).isFalse();
    }

    @Test
    void ownerSessionGrantsAndRevokesThroughMemberManagement() throws Exception {
        MockHttpSession session = session(owner);
        String route = "/api/web/namespaces/" + slug + "/members/" + publisher + "/team-auto-publication";

        mockMvc.perform(put(route).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"granted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamAutoPublicationGranted").value(true))
                .andExpect(jsonPath("$.data.role").value("MEMBER"));
        assertThat(members.findByNamespaceIdAndUserId(namespaceId, publisher).orElseThrow()
                .getTeamAutoPublicationGrantId()).isNotNull();

        mockMvc.perform(put(route).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"granted\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamAutoPublicationGranted").value(false));
        assertThat(members.findByNamespaceIdAndUserId(namespaceId, publisher).orElseThrow()
                .getTeamAutoPublicationGrantId()).isNull();
    }

    @Test
    void ordinaryMemberSessionCannotGrantItself() throws Exception {
        mockMvc.perform(put("/api/web/namespaces/" + slug + "/members/" + publisher + "/team-auto-publication")
                        .session(session(publisher)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"granted\":true}"))
                .andExpect(status().isForbidden());
        assertThat(members.findByNamespaceIdAndUserId(namespaceId, publisher).orElseThrow()
                .isTeamAutoPublicationGranted()).isFalse();
    }

    private MockHttpSession session(String userId) {
        PlatformPrincipal principal = new PlatformPrincipal(userId, userId, userId + "@example.com", null,
                "github", Set.of("USER"));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        session.setAttribute("platformPrincipal", principal);
        return session;
    }
}
