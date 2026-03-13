package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.dto.AdminUserSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.service.AdminUserManagementService;
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

import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

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
    private AdminUserManagementService adminUserManagementService;

    @Test
    void listUsers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listUsers_withUserAdminRole_returns200() throws Exception {
        given(adminUserManagementService.listUsers(null, null, 0, 20))
            .willReturn(new PageResponse<>(
                List.of(
                    new AdminUserSummaryResponse("user-1", "alice", "alice@example.com", List.of("USER"), "ACTIVE", LocalDateTime.parse("2026-03-12T12:00:00")),
                    new AdminUserSummaryResponse("user-2", "bob", "bob@example.com", List.of("USER_ADMIN"), "PENDING", LocalDateTime.parse("2026-03-12T13:00:00"))
                ),
                2,
                0,
                20
            ));

        PlatformPrincipal principal = new PlatformPrincipal(
            "user-42", "admin", "admin@example.com", "", "github", Set.of("USER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER_ADMIN"))
        );

        mockMvc.perform(get("/api/v1/admin/users").with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void listUsers_withSuperAdminRole_returns200() throws Exception {
        given(adminUserManagementService.listUsers(null, null, 0, 20))
            .willReturn(new PageResponse<>(
                List.of(new AdminUserSummaryResponse("user-99", "superadmin", "super@example.com", List.of("SUPER_ADMIN"), "ACTIVE", LocalDateTime.parse("2026-03-12T14:00:00"))),
                1,
                0,
                20
            ));

        PlatformPrincipal principal = new PlatformPrincipal(
            "user-99", "superadmin", "super@example.com", "", "github", Set.of("SUPER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );

        mockMvc.perform(get("/api/v1/admin/users").with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void updateUserRole_withUserAdminRole_returns200() throws Exception {
        given(adminUserManagementService.updateUserRole(org.mockito.ArgumentMatchers.eq("user-123"), org.mockito.ArgumentMatchers.eq("USER_ADMIN"), org.mockito.ArgumentMatchers.any()))
            .willReturn(new AdminUserSummaryResponse("user-123", "target", "target@example.com", List.of("USER_ADMIN"), "ACTIVE", LocalDateTime.parse("2026-03-12T15:00:00")));

        PlatformPrincipal principal = new PlatformPrincipal(
            "user-42", "admin", "admin@example.com", "", "github", Set.of("USER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER_ADMIN"))
        );

        String requestBody = "{\"role\":\"USER_ADMIN\"}";

        mockMvc.perform(put("/api/v1/admin/users/user-123/role")
                .with(authentication(auth))
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value("user-123"))
            .andExpect(jsonPath("$.data.role").value("USER_ADMIN"));
    }

    @Test
    void updateUserStatus_withUserAdminRole_returns200() throws Exception {
        given(adminUserManagementService.updateUserStatus("user-123", "DISABLED"))
            .willReturn(new AdminUserSummaryResponse("user-123", "target", "target@example.com", List.of("USER"), "DISABLED", LocalDateTime.parse("2026-03-12T16:00:00")));

        PlatformPrincipal principal = new PlatformPrincipal(
            "user-42", "admin", "admin@example.com", "", "github", Set.of("USER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER_ADMIN"))
        );

        String requestBody = "{\"status\":\"DISABLED\"}";

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
}
