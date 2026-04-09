package com.iflytek.skillhub.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.local.LocalAuthService;
import com.iflytek.skillhub.auth.local.PasswordResetService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import com.iflytek.skillhub.security.AuthFailureThrottleService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocalAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LocalAuthService localAuthService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private SkillHubMetrics skillHubMetrics;

    @MockBean
    private AuthFailureThrottleService authFailureThrottleService;

    @MockBean
    private PasswordResetService passwordResetService;

    @Test
    void login_returnsCurrentUserEnvelope() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
            "usr_1",
            "alice",
            "alice@example.com",
            "",
            "local",
            Set.of("SUPER_ADMIN")
        );
        given(localAuthService.login("alice", "Abcd123!")).willReturn(principal);

        mockMvc.perform(post("/api/v1/auth/local/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"alice","password":"Abcd123!"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value("usr_1"))
            .andExpect(jsonPath("$.data.oauthProvider").value("local"));
        verify(skillHubMetrics).recordLocalLogin(true);
        verify(skillHubMetrics, never()).recordLocalLogin(false);
        verify(authFailureThrottleService).resetIdentifier("local", "alice");
    }

    @Test
    void register_returnsCreatedEnvelope() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
            "usr_2",
            "bob",
            "bob@example.com",
            "",
            "local",
            Set.of()
        );
        given(localAuthService.register("bob", "Abcd123!", "bob@example.com")).willReturn(principal);

        mockMvc.perform(post("/api/v1/auth/local/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"bob","password":"Abcd123!","email":"bob@example.com"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.displayName").value("bob"));
        verify(skillHubMetrics).incrementUserRegister();
    }

    @Test
    void register_rejectsInvalidEmailFormat() throws Exception {
        given(localAuthService.register("bob", "Abcd123!", "not-an-email"))
            .willThrow(new AuthFlowException(HttpStatus.BAD_REQUEST, "validation.auth.local.email.invalid"));

        mockMvc.perform(post("/api/v1/auth/local/register")
                .with(csrf())
                .header("Accept-Language", "zh-CN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"bob","password":"Abcd123!","email":"not-an-email"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));

        verify(localAuthService).register("bob", "Abcd123!", "not-an-email");
    }

    @Test
    void register_rejectsBlankEmail() throws Exception {
        given(localAuthService.register("bob", "Abcd123!", " "))
            .willThrow(new AuthFlowException(HttpStatus.BAD_REQUEST, "validation.auth.local.email.notBlank"));

        mockMvc.perform(post("/api/v1/auth/local/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"bob","password":"Abcd123!","email":" "}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));

        verify(localAuthService).register("bob", "Abcd123!", " ");
    }

    @Test
    void login_failure_recordsFailureMetric() throws Exception {
        given(localAuthService.login("alice", "wrong"))
            .willThrow(new AuthFlowException(HttpStatus.UNAUTHORIZED, "error.auth.local.invalidCredentials"));

        mockMvc.perform(post("/api/v1/auth/local/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"alice","password":"wrong"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
        verify(authFailureThrottleService).recordFailure("local", "alice", "127.0.0.1");
        verify(skillHubMetrics).recordLocalLogin(false);
        verify(skillHubMetrics, never()).recordLocalLogin(true);
    }

    @Test
    void changePassword_requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/local/change-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"old","newPassword":"Newpass123!"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void changePassword_withAuthentication_returnsUpdated() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
            "usr_3",
            "carol",
            "carol@example.com",
            "",
            "local",
            Set.of("SUPER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );

        mockMvc.perform(post("/api/v1/auth/local/change-password")
                .with(authentication(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"old","newPassword":"Newpass123!"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void requestPasswordReset_returnsGenericSuccessEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/auth/local/password-reset/request")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"alice@example.com"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        verify(passwordResetService).requestPasswordReset("alice@example.com");
    }

    @Test
    void requestPasswordReset_rejectsInvalidEmailFormat() throws Exception {
        willThrow(new AuthFlowException(HttpStatus.BAD_REQUEST, "validation.auth.password.reset.email.invalid"))
            .given(passwordResetService).requestPasswordReset("alice");

        mockMvc.perform(post("/api/v1/auth/local/password-reset/request")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"alice"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));

        verify(passwordResetService).requestPasswordReset("alice");
    }

    @Test
    void confirmPasswordReset_returnsUpdatedEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/auth/local/password-reset/confirm")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"alice@example.com","code":"123456","newPassword":"Abcd123!"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        verify(passwordResetService).confirmPasswordReset("alice@example.com", "123456", "Abcd123!");
    }

    @Test
    void confirmPasswordReset_rejectsInvalidEmailFormat() throws Exception {
        willThrow(new AuthFlowException(HttpStatus.BAD_REQUEST, "validation.auth.password.reset.email.invalid"))
            .given(passwordResetService).confirmPasswordReset("alice", "123456", "Abcd123!");

        mockMvc.perform(post("/api/v1/auth/local/password-reset/confirm")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"alice","code":"123456","newPassword":"Abcd123!"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));

        verify(passwordResetService).confirmPasswordReset("alice", "123456", "Abcd123!");
    }

}
