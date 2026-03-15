package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.dto.AuditLogItemResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.service.AdminAuditLogAppService;
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
import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private AdminAuditLogAppService adminAuditLogAppService;

    @Test
    void listAuditLogs_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listAuditLogs_withAuditorRole_returns200() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
            "user-50", "auditor", "auditor@example.com", "", "github", Set.of("AUDITOR")
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_AUDITOR"))
        );

        when(adminAuditLogAppService.listAuditLogs(0, 20, null, null, null, null, null, null, null, null))
                .thenReturn(new PageResponse<>(
                        List.of(new AuditLogItemResponse(
                                1L,
                                "USER_STATUS_CHANGE",
                                "user-1",
                                "alice",
                                "{\"status\":\"DISABLED\"}",
                                "127.0.0.1",
                                "req-1",
                                "USER",
                                "42",
                                Instant.parse("2026-03-13T01:00:00Z"))),
                        1,
                        0,
                        20));

        mockMvc.perform(get("/api/v1/admin/audit-logs").with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].username").value("alice"))
            .andExpect(jsonPath("$.data.items[0].details").value("{\"status\":\"DISABLED\"}"))
            .andExpect(jsonPath("$.data.items[0].requestId").value("req-1"))
            .andExpect(jsonPath("$.data.items[0].resourceType").value("USER"))
            .andExpect(jsonPath("$.data.items[0].resourceId").value("42"));
    }

    @Test
    void listAuditLogs_withSuperAdminRole_returns200() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
            "user-99", "superadmin", "super@example.com", "", "github", Set.of("SUPER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );

        when(adminAuditLogAppService.listAuditLogs(0, 20, null, null, null, null, null, null, null, null))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/v1/admin/audit-logs").with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void listAuditLogs_withFilters_returns200() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
            "user-50", "auditor", "auditor@example.com", "", "github", Set.of("AUDITOR")
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_AUDITOR"))
        );

        when(adminAuditLogAppService.listAuditLogs(
                0,
                20,
                "user-1",
                "CREATE_SKILL",
                "req-2",
                "127.0.0.1",
                "SKILL",
                "99",
                Instant.parse("2026-03-13T00:00:00Z"),
                Instant.parse("2026-03-14T00:00:00Z")))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                .param("userId", "user-1")
                .param("action", "CREATE_SKILL")
                .param("requestId", "req-2")
                .param("ipAddress", "127.0.0.1")
                .param("resourceType", "SKILL")
                .param("resourceId", "99")
                .param("startTime", "2026-03-13T00:00:00Z")
                .param("endTime", "2026-03-14T00:00:00Z")
                .with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void listAuditLogs_withUserAdminRole_returns403() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-88", "useradmin", "useradmin@example.com", "", "github", Set.of("USER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER_ADMIN"))
        );

        mockMvc.perform(get("/api/v1/admin/audit-logs").with(authentication(auth)))
                .andExpect(status().isForbidden());
    }
}
