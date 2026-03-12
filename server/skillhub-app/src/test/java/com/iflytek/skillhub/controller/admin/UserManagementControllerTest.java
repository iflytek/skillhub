package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
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

    @Test
    void listUsers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listUsers_withUserAdminRole_returns200() throws Exception {
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
        PlatformPrincipal principal = new PlatformPrincipal(
            "user-42", "admin", "admin@example.com", "", "github", Set.of("USER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER_ADMIN"))
        );

        String requestBody = "{\"role\":\"MODERATOR\"}";

        mockMvc.perform(put("/api/v1/admin/users/user-123/role")
                .with(authentication(auth))
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value("user-123"))
            .andExpect(jsonPath("$.data.role").value("MODERATOR"));
    }

    @Test
    void updateUserStatus_withUserAdminRole_returns200() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
            "user-42", "admin", "admin@example.com", "", "github", Set.of("USER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER_ADMIN"))
        );

        String requestBody = "{\"status\":\"BANNED\"}";

        mockMvc.perform(put("/api/v1/admin/users/user-123/status")
                .with(authentication(auth))
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value("user-123"))
            .andExpect(jsonPath("$.data.status").value("BANNED"));
    }
}
