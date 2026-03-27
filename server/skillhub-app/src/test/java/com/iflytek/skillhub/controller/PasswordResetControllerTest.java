package com.iflytek.skillhub.controller;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private SkillHubMetrics skillHubMetrics;

    @MockBean
    private AuthFailureThrottleService authFailureThrottleService;

    @Test
    void requestPasswordReset_returnsGenericSuccessEvenForIneligibleUser() throws Exception {
        mockMvc.perform(post("/api/v1/auth/local/password-reset/request")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"identifier":"nonexistent@example.com"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        verify(passwordResetService).requestPasswordReset("nonexistent@example.com");
    }

    @Test
    void confirmPasswordReset_withValidToken_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/local/password-reset/confirm")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"token":"valid-token","newPassword":"NewPass123!"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        verify(passwordResetService).confirmPasswordReset("valid-token", "NewPass123!");
    }

    @Test
    void confirmPasswordReset_withInvalidToken_returnsBadRequest() throws Exception {
        willThrow(new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.password.reset.invalid.token"))
            .given(passwordResetService).confirmPasswordReset("invalid-token", "NewPass123!");

        mockMvc.perform(post("/api/v1/auth/local/password-reset/confirm")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"token":"invalid-token","newPassword":"NewPass123!"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void adminTriggerPasswordReset_withAdminRole_returns200() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
            "admin_1",
            "admin",
            "admin@example.com",
            "",
            "local",
            Set.of("USER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER_ADMIN"))
        );

        mockMvc.perform(post("/api/v1/admin/users/usr_1/password-reset")
                .with(authentication(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        verify(passwordResetService).adminTriggerPasswordReset("usr_1", "admin_1");
    }

    @Test
    void adminTriggerPasswordReset_withoutAdminRole_returnsForbidden() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
            "usr_1",
            "alice",
            "alice@example.com",
            "",
            "local",
            Set.of("USER")
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        mockMvc.perform(post("/api/v1/admin/users/usr_2/password-reset")
                .with(authentication(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }
}
