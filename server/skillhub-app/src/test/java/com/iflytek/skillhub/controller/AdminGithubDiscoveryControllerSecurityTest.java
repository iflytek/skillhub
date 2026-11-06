package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.service.GithubDiscoveryAdminAppService;
import com.iflytek.skillhub.service.GithubSearchPreviewAppService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
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

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "skillhub.builtin-skills.enabled=false")
class AdminGithubDiscoveryControllerSecurityTest {
    private static final String ENTRIES = "/api/v1/admin/github-sync/discovery-entries";
    private static final String RULE = """
            {"title":"Public skills","selectors":[
              {"kind":"KEYWORD","value":"agent skills","priority":"CORE"}]}
            """;
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

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @MockBean private GithubDiscoveryAdminAppService discovery;
    @MockBean private GithubSearchPreviewAppService previews;

    @Test
    void anonymousAndOrdinaryUserCannotReadRulesOrQueuePreview() throws Exception {
        mockMvc.perform(get(ENTRIES)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ENTRIES).with(authentication(auth("ordinary", "USER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ENTRIES + "/search-rules/preview")
                        .with(authentication(auth("ordinary", "USER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(RULE))
                .andExpect(status().isForbidden());
        verifyNoInteractions(discovery, previews);
    }

    @Test
    void superAdminCanReadRulesAndQueuePreviewThroughSecurityChain() throws Exception {
        when(discovery.list()).thenReturn(List.of());
        var preview = new GithubSearchPreviewAppService.Preview(
                UUID.randomUUID(), "QUEUED", null, null, mapper.createObjectNode(),
                Instant.now().toString(), null, Instant.now(), Instant.now());
        when(previews.queue(any(), eq("admin"))).thenReturn(preview);

        mockMvc.perform(get(ENTRIES).with(authentication(auth("admin", "SUPER_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        mockMvc.perform(post(ENTRIES + "/search-rules/preview")
                        .with(authentication(auth("admin", "SUPER_ADMIN"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(RULE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(preview.id().toString()));
        verify(previews).queue(any(), eq("admin"));
    }

    private UsernamePasswordAuthenticationToken auth(String userId, String role) {
        var principal = new PlatformPrincipal(userId, userId, null, null, null, Set.of(role));
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
