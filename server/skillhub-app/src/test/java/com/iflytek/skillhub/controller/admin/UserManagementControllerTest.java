package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.dto.AdminUserMutationResponse;
import com.iflytek.skillhub.dto.AdminUserSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.service.AdminUserAppService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class UserManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private AdminUserAppService adminUserAppService;

    @Test
    void listUsers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void listUsers_withUserAdminRole_returns200() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
            "user-42", "admin", "admin@example.com", "", "github", Set.of("USER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER_ADMIN"))
        );

        when(adminUserAppService.listUsers(null, null, 0, 20))
                .thenReturn(new PageResponse<>(
                        List.of(new AdminUserSummaryResponse(
                                "user-1",
                                "alice",
                                "alice@example.com",
                                "ACTIVE",
                                List.of("AUDITOR"),
                                Instant.parse("2026-03-13T09:00:00Z"))),
                        1,
                        0,
                        20));

        mockMvc.perform(get("/api/v1/admin/users").with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value("user-1"))
            .andExpect(jsonPath("$.data.items[0].email").value("alice@example.com"))
            .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-03-13T09:00:00Z"))
            .andExpect(jsonPath("$.data.items[0].platformRoles[0]").value("AUDITOR"));
    }

    @Test
    void listUsers_withSuperAdminRole_returns200() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
            "user-99", "superadmin", "super@example.com", "", "github", Set.of("SUPER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );

        when(adminUserAppService.listUsers(null, null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/v1/admin/users").with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void listUsers_withSkillAdminRole_returns403() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-77", "skilladmin", "skilladmin@example.com", "", "github", Set.of("SKILL_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_SKILL_ADMIN"))
        );

        mockMvc.perform(get("/api/v1/admin/users").with(authentication(auth)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void updateUserRole_withUserAdminRole_returns200() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
            "user-42", "admin", "admin@example.com", "", "github", Set.of("USER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER_ADMIN"))
        );

        String requestBody = "{\"role\":\"MODERATOR\"}";

        when(adminUserAppService.updateUserRole("user-123", "MODERATOR", Set.of("USER_ADMIN")))
                .thenReturn(new AdminUserMutationResponse("user-123", "MODERATOR", "ACTIVE"));

        mockMvc.perform(put("/api/v1/admin/users/user-123/role")
                .with(authentication(auth))
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value("user-123"))
            .andExpect(jsonPath("$.data.role").value("MODERATOR"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void updateUserStatus_withUserAdminRole_returns200() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
            "user-42", "admin", "admin@example.com", "", "github", Set.of("USER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER_ADMIN"))
        );

        String requestBody = "{\"status\":\"DISABLED\"}";

        when(adminUserAppService.updateUserStatus("user-123", "DISABLED"))
                .thenReturn(new AdminUserMutationResponse("user-123", null, "DISABLED"));

        mockMvc.perform(put("/api/v1/admin/users/user-123/status")
                .with(authentication(auth))
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value("user-123"))
            .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test
    void approveUser_delegatesToActiveStatusMutation() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "admin", "admin@example.com", "", "github", Set.of("USER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER_ADMIN"))
        );

        when(adminUserAppService.updateUserStatus("user-123", "ACTIVE"))
                .thenReturn(new AdminUserMutationResponse("user-123", null, "ACTIVE"));

        mockMvc.perform(post("/api/v1/admin/users/user-123/approve")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value("user-123"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(adminUserAppService).updateUserStatus("user-123", "ACTIVE");
    }

    @Test
    void disableUser_delegatesToDisabledStatusMutation() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "admin", "admin@example.com", "", "github", Set.of("USER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER_ADMIN"))
        );

        when(adminUserAppService.updateUserStatus("user-123", "DISABLED"))
                .thenReturn(new AdminUserMutationResponse("user-123", null, "DISABLED"));

        mockMvc.perform(post("/api/v1/admin/users/user-123/disable")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value("user-123"))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        verify(adminUserAppService).updateUserStatus("user-123", "DISABLED");
    }

    @Test
    void enableUser_delegatesToActiveStatusMutation() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "admin", "admin@example.com", "", "github", Set.of("USER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER_ADMIN"))
        );

        when(adminUserAppService.updateUserStatus("user-123", "ACTIVE"))
                .thenReturn(new AdminUserMutationResponse("user-123", null, "ACTIVE"));

        mockMvc.perform(post("/api/v1/admin/users/user-123/enable")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value("user-123"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(adminUserAppService).updateUserStatus("user-123", "ACTIVE");
    }
}
