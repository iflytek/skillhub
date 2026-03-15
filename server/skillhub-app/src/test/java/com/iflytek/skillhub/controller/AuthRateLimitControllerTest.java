package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.local.LocalAuthService;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import com.iflytek.skillhub.ratelimit.RateLimiter;
import com.iflytek.skillhub.security.AuthFailureThrottleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRateLimitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LocalAuthService localAuthService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private SkillHubMetrics skillHubMetrics;

    @MockBean
    private RateLimiter rateLimiter;

    @MockBean
    private AuthFailureThrottleService authFailureThrottleService;

    @Test
    void localLoginShouldReturnTooManyRequestsWhenRateLimitIsExceeded() throws Exception {
        given(rateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).willReturn(false);

        mockMvc.perform(post("/api/v1/auth/local/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"alice","password":"wrong"}
                    """))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value(429))
            .andExpect(jsonPath("$.msg").isNotEmpty());

        verify(localAuthService, never()).login(anyString(), anyString());
    }

    @Test
    void localLoginShouldRecordCredentialFailuresForBruteForceTracking() throws Exception {
        given(rateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).willReturn(true);
        given(localAuthService.login("alice", "wrong"))
                .willThrow(new AuthFlowException(org.springframework.http.HttpStatus.UNAUTHORIZED, "error.auth.local.invalidCredentials"));

        mockMvc.perform(post("/api/v1/auth/local/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"username":"alice","password":"wrong"}
                    """))
                .andExpect(status().isUnauthorized());

        verify(authFailureThrottleService).assertAllowed("local", "alice", "127.0.0.1");
        verify(authFailureThrottleService).recordFailure("local", "alice", "127.0.0.1");
    }

    @Test
    void localLoginShouldResetIdentifierThrottleAfterSuccess() throws Exception {
        given(rateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).willReturn(true);
        given(localAuthService.login("alice", "correct")).willReturn(new PlatformPrincipal(
                "usr_1",
                "alice",
                "alice@example.com",
                "",
                "local",
                java.util.Set.of("USER")
        ));

        mockMvc.perform(post("/api/v1/auth/local/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"username":"alice","password":"correct"}
                    """))
                .andExpect(status().isOk());

        verify(authFailureThrottleService).resetIdentifier("local", "alice");
    }
}
