package com.iflytek.skillhub.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.uass.UassCallbackFlowService;
import com.iflytek.skillhub.auth.uass.UassLoginInitiationService;
import com.iflytek.skillhub.auth.uass.UassSessionFlowService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import java.net.URI;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("qa")
@TestPropertySource(properties = "skillhub.auth.uass.enabled=true")
class UassAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UassLoginInitiationService uassLoginInitiationService;

    @MockBean
    private UassCallbackFlowService uassCallbackFlowService;

    @MockBean
    private UassSessionFlowService uassSessionFlowService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @Test
    void loginUrlReturnsProviderRedirectUrlInApiEnvelope() throws Exception {
        given(uassLoginInitiationService.buildLoginUrl(
                "/dashboard/review",
                URI.create("http://localhost/api/v1/auth/uass/login-url")))
                .willReturn("https://uass.example.com/login?state=state-1");

        mockMvc.perform(get("/api/v1/auth/uass/login-url")
                        .param("returnTo", "/dashboard/review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginUrl").value("https://uass.example.com/login?state=state-1"));
    }

    @Test
    void loginInitiatesLoginAndRespondsWithProviderLocation() throws Exception {
        given(uassLoginInitiationService.buildLoginUrl(
                "/dashboard/publish",
                URI.create("http://localhost/api/v1/auth/uass")))
                .willReturn("https://uass.example.com/login?state=state-0");

        mockMvc.perform(get("/api/v1/auth/uass")
                        .param("returnTo", "/dashboard/publish"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://uass.example.com/login?state=state-0"));
    }

    @Test
    void redirectInitiatesLoginAndRespondsWithProviderLocation() throws Exception {
        given(uassLoginInitiationService.buildLoginUrl(
                "/dashboard/publish",
                URI.create("http://localhost/api/v1/auth/uass/redirect")))
                .willReturn("https://uass.example.com/login?state=state-2");

        mockMvc.perform(get("/api/v1/auth/uass/redirect")
                        .param("returnTo", "/dashboard/publish"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://uass.example.com/login?state=state-2"));
    }

    @Test
    void callbackCompletesLoginAndRedirectsToStoredReturnTo() throws Exception {
        given(uassCallbackFlowService.completeLogin(
                eq("auth-code"),
                eq("state-1"),
                eq(URI.create("http://localhost/api/v1/auth/uass/callback")),
                any()))
                .willReturn("/dashboard/publish");

        mockMvc.perform(get("/api/v1/auth/uass/callback")
                        .param("code", "auth-code")
                        .param("state", "state-1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/dashboard/publish"));
    }

    @Test
    void statusReturnsLocalSessionStatusEnvelope() throws Exception {
        given(uassSessionFlowService.status(any(), any()))
                .willReturn(new UassSessionFlowService.UassSessionStatus(true, "uass", true));

        mockMvc.perform(get("/api/v1/auth/uass/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.provider").value("uass"))
                .andExpect(jsonPath("$.data.remoteAuthenticated").value(true));
    }

    @Test
    void logoutClearsAuthenticatedUassSession() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "usr_1",
                "UASS User",
                "user@example.com",
                null,
                "uass",
                Set.of("USER")
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        mockMvc.perform(post("/api/v1/auth/uass/logout")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authentication))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNoContent());

        verify(uassSessionFlowService).logout(any());
    }
}
