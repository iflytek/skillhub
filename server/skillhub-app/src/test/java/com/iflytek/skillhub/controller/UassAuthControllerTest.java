package com.iflytek.skillhub.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.uass.UassCallbackFlowService;
import com.iflytek.skillhub.auth.uass.UassLoginInitiationService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "skillhub.auth.uass.enabled=true")
class UassAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UassLoginInitiationService uassLoginInitiationService;

    @MockBean
    private UassCallbackFlowService uassCallbackFlowService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @Test
    void loginUrlReturnsProviderRedirectUrlInApiEnvelope() throws Exception {
        given(uassLoginInitiationService.buildLoginUrl(
                eq("/dashboard/review"),
                eq(URI.create("http://localhost/api/v1/auth/uass/login-url"))))
                .willReturn("https://uass.example.com/login?state=state-1");

        mockMvc.perform(get("/api/v1/auth/uass/login-url")
                        .param("returnTo", "/dashboard/review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginUrl").value("https://uass.example.com/login?state=state-1"));

        verify(uassLoginInitiationService)
                .buildLoginUrl(eq("/dashboard/review"), eq(URI.create("http://localhost/api/v1/auth/uass/login-url")));
    }

    @Test
    void redirectInitiatesLoginAndRespondsWithProviderLocation() throws Exception {
        given(uassLoginInitiationService.buildLoginUrl(
                eq("/dashboard/publish"),
                eq(URI.create("http://localhost/api/v1/auth/uass/redirect"))))
                .willReturn("https://uass.example.com/login?state=state-2");

        mockMvc.perform(get("/api/v1/auth/uass/redirect")
                        .param("returnTo", "/dashboard/publish"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://uass.example.com/login?state=state-2"));

        verify(uassLoginInitiationService)
                .buildLoginUrl(eq("/dashboard/publish"), eq(URI.create("http://localhost/api/v1/auth/uass/redirect")));
    }

    @Test
    void callbackCompletesLoginAndRedirectsToStoredReturnTo() throws Exception {
        given(uassCallbackFlowService.completeLogin(eq("auth-code"), eq("state-1"), eq(URI.create("http://localhost/api/v1/auth/uass/callback")), any()))
                .willReturn("/dashboard/publish");

        mockMvc.perform(get("/api/v1/auth/uass/callback")
                        .param("code", "auth-code")
                        .param("state", "state-1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/dashboard/publish"));

        verify(uassCallbackFlowService)
                .completeLogin(eq("auth-code"), eq("state-1"), eq(URI.create("http://localhost/api/v1/auth/uass/callback")), any());
    }
}
