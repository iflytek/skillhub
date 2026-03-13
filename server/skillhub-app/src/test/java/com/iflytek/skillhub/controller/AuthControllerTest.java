package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

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
            .andExpect(jsonPath("$.data[0].id").value("github"))
            .andExpect(jsonPath("$.data[0].authorizationUrl").value("/oauth2/authorization/github"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty())
            .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
}
