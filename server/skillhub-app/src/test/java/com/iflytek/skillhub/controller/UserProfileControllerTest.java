package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.domain.user.ProfileChangeRequest;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.user.ProfileChangeRequestRepository;
import com.iflytek.skillhub.domain.user.ProfileChangeStatus;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.security.AuthFailureThrottleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link UserProfileController}.
 * Uses MockMvc with Spring Security context to test the full HTTP flow.
 */
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
    "spring.security.oauth2.client.provider.gitee.user-name-attribute=id",
    "skillhub.profile.moderation.machine-review=false",
    "skillhub.profile.moderation.human-review=false"
})
class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private AuthFailureThrottleService authFailureThrottleService;

    @MockBean
    private UserAccountRepository userAccountRepository;

    @MockBean
    private UserRoleBindingRepository userRoleBindingRepository;

    @MockBean
    private ProfileChangeRequestRepository changeRequestRepository;

    @MockBean
    private PlatformSessionService platformSessionService;

    @MockBean
    private AuditLogService auditLogService;
    // -- Helper --

    private PlatformPrincipal testPrincipal() {
        return new PlatformPrincipal(
                "user-1", "OldName", "user@example.com",
                "https://example.com/avatar.png", "github", Set.of("USER"));
    }

    private UsernamePasswordAuthenticationToken testAuth(PlatformPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    // ===== AC-S-001: Unauthorized access to PATCH =====

    @Test
    void updateProfile_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(patch("/api/v1/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"NewName\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ===== AC-S-002: Unauthorized access to GET =====

    @Test
    void getProfile_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/user/profile"))
                .andExpect(status().isUnauthorized());
    }

    // ===== AC-P-001: Successful update =====

    @Test
    void updateProfile_validRequest_shouldReturn200() throws Exception {
        var principal = testPrincipal();
        var user = new UserAccount("user-1", "OldName", "user@example.com", "https://example.com/avatar.png");

        given(userAccountRepository.findById("user-1")).willReturn(Optional.of(user));
        given(namespaceMemberRepository.findByUserId("user-1")).willReturn(List.of());
        given(userRoleBindingRepository.findByUserId("user-1")).willReturn(List.of());

        mockMvc.perform(patch("/api/v1/user/profile")
                        .with(authentication(testAuth(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"NewName\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("APPLIED"));
    }

    @Test
    void updateProfile_displayNameWithSpaces_shouldReturn200() throws Exception {
        var principal = testPrincipal();
        var user = new UserAccount("user-1", "OldName", "user@example.com", "https://example.com/avatar.png");

        given(userAccountRepository.findById("user-1")).willReturn(Optional.of(user));
        given(namespaceMemberRepository.findByUserId("user-1")).willReturn(List.of());
        given(userRoleBindingRepository.findByUserId("user-1")).willReturn(List.of());

        mockMvc.perform(patch("/api/v1/user/profile")
                        .with(authentication(testAuth(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("APPLIED"));
    }
    // ===== AC-E-001: Display name too short =====

    @Test
    void updateProfile_displayNameTooShort_shouldReturn400() throws Exception {
        var principal = testPrincipal();

        mockMvc.perform(patch("/api/v1/user/profile")
                        .with(authentication(testAuth(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"A\"}"))
                .andExpect(status().isBadRequest());
    }

    // ===== AC-E-002: Display name too long =====

    @Test
    void updateProfile_displayNameTooLong_shouldReturn400() throws Exception {
        var principal = testPrincipal();
        String longName = "a".repeat(33);

        mockMvc.perform(patch("/api/v1/user/profile")
                        .with(authentication(testAuth(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"" + longName + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // ===== AC-E-003: Invalid characters =====

    @Test
    void updateProfile_invalidCharacters_shouldReturn400() throws Exception {
        var principal = testPrincipal();

        mockMvc.perform(patch("/api/v1/user/profile")
                        .with(authentication(testAuth(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"test@user!\"}"))
                .andExpect(status().isBadRequest());
    }

    // ===== AC-E-006: Empty request body =====

    @Test
    void updateProfile_emptyRequest_shouldReturn400() throws Exception {
        var principal = testPrincipal();
        var user = new UserAccount("user-1", "OldName", "user@example.com", "https://example.com/avatar.png");

        given(userAccountRepository.findById("user-1")).willReturn(Optional.of(user));

        mockMvc.perform(patch("/api/v1/user/profile")
                        .with(authentication(testAuth(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ===== AC-P-006: GET profile with no pending changes =====

    @Test
    void getProfile_noPendingChanges_shouldReturnCurrentValues() throws Exception {
        var principal = testPrincipal();
        var user = new UserAccount("user-1", "CurrentName", "user@example.com", "https://example.com/avatar.png");

        given(userAccountRepository.findById("user-1")).willReturn(Optional.of(user));
        given(changeRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
                "user-1",
                List.of(ProfileChangeStatus.PENDING, ProfileChangeStatus.REJECTED)))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/user/profile")
                        .with(authentication(testAuth(principal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.displayName").value("CurrentName"))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.pendingChanges").isEmpty());
    }

    @Test
    void getProfile_pendingChanges_shouldReturnLatestPrivateValues() throws Exception {
        var principal = testPrincipal();
        var user = new UserAccount("user-1", "CurrentName", "user@example.com", "https://example.com/avatar.png");
        var request = new ProfileChangeRequest(
                "user-1",
                "{\"displayName\":\"NewName\",\"avatarUrl\":\"https://example.com/new-avatar.png\"}",
                "{\"displayName\":\"CurrentName\",\"avatarUrl\":\"https://example.com/avatar.png\"}",
                ProfileChangeStatus.PENDING,
                "PASS",
                null
        );

        given(userAccountRepository.findById("user-1")).willReturn(Optional.of(user));
        given(changeRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
                "user-1",
                List.of(ProfileChangeStatus.PENDING, ProfileChangeStatus.REJECTED)))
                .willReturn(Optional.of(request));

        mockMvc.perform(get("/api/v1/user/profile")
                        .with(authentication(testAuth(principal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("NewName"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/new-avatar.png"))
                .andExpect(jsonPath("$.data.pendingChanges.status").value("PENDING"))
                .andExpect(jsonPath("$.data.pendingChanges.changes.displayName").value("NewName"));
    }
    // ===== AC-S-003: XSS attempt =====

    @Test
    void updateProfile_xssAttempt_shouldReturn400() throws Exception {
        var principal = testPrincipal();

        mockMvc.perform(patch("/api/v1/user/profile")
                        .with(authentication(testAuth(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"<script>alert('xss')</script>\"}"))
                .andExpect(status().isBadRequest());
    }
}
