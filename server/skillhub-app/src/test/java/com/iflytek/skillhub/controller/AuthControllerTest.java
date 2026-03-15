package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.security.AuthFailureThrottleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.security.oauth2.client.registration.github.client-name=GitHub",
    "spring.security.oauth2.client.registration.gitee.client-id=placeholder",
    "spring.security.oauth2.client.registration.gitee.client-secret=placeholder",
    "spring.security.oauth2.client.registration.gitee.provider=gitee",
    "spring.security.oauth2.client.registration.gitee.authorization-grant-type=authorization_code",
    "spring.security.oauth2.client.registration.gitee.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
    "spring.security.oauth2.client.registration.gitee.scope=user_info",
    "spring.security.oauth2.client.registration.gitee.client-name=Gitee",
    "spring.security.oauth2.client.provider.gitee.authorization-uri=https://gitee.com/oauth/authorize",
    "spring.security.oauth2.client.provider.gitee.token-uri=https://gitee.com/oauth/token",
    "spring.security.oauth2.client.provider.gitee.user-info-uri=https://gitee.com/api/v5/user",
    "spring.security.oauth2.client.provider.gitee.user-name-attribute=id"
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private AuthFailureThrottleService authFailureThrottleService;

    @Test
    void meShouldReturnUnauthorizedForAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void meShouldReturnCurrentPrincipal() throws Exception {
        given(namespaceMemberRepository.findByUserId("user-42")).willReturn(List.of());

        PlatformPrincipal principal = new PlatformPrincipal(
            "user-42",
            "tester",
            "tester@example.com",
            "https://example.com/avatar.png",
            "github",
            Set.of("SUPER_ADMIN")
        );

        var auth = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );

        mockMvc.perform(get("/api/v1/auth/me").with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.msg").isNotEmpty())
            .andExpect(jsonPath("$.data.userId").value("user-42"))
            .andExpect(jsonPath("$.data.displayName").value("tester"))
            .andExpect(jsonPath("$.data.oauthProvider").value("github"))
            .andExpect(jsonPath("$.data.platformRoles[0]").value("SUPER_ADMIN"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty())
            .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void providersShouldExposeGithubLoginEntry() throws Exception {
        mockMvc.perform(get("/api/v1/auth/providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.msg").isNotEmpty())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[*].id", hasItems("github", "gitee")))
            .andExpect(jsonPath("$.data[*].authorizationUrl", hasItems(
                "/oauth2/authorization/github",
                "/oauth2/authorization/gitee"
            )))
            .andExpect(jsonPath("$.timestamp").isNotEmpty())
            .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void providersShouldAppendReturnToWhenRequested() throws Exception {
        mockMvc.perform(get("/api/v1/auth/providers").param("returnTo", "/dashboard/publish"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].authorizationUrl", hasItems(
                "/oauth2/authorization/github?returnTo=%2Fdashboard%2Fpublish",
                "/oauth2/authorization/gitee?returnTo=%2Fdashboard%2Fpublish"
            )));
    }

    @Test
    void methodsShouldExposeStandardLoginCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/auth/methods").param("returnTo", "/dashboard/publish"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[*].id", hasItems("local-password", "oauth-github", "oauth-gitee")))
            .andExpect(jsonPath("$.data[?(@.id=='local-password')].methodType").value(hasItems("PASSWORD")))
            .andExpect(jsonPath("$.data[?(@.id=='oauth-github')].actionUrl")
                .value(hasItems("/oauth2/authorization/github?returnTo=%2Fdashboard%2Fpublish")));
    }

    @Test
    void sessionBootstrapShouldBeForbiddenWhenFeatureIsDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/auth/session/bootstrap")
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {"provider":"private-sso"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.msg").isNotEmpty());
    }

    @Test
    void directLoginShouldBeForbiddenWhenFeatureIsDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/auth/direct/login")
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {"provider":"private-sso","username":"alice","password":"secret"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.msg").isNotEmpty());
    }
}
