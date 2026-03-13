package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.domain.audit.AuditLog;
import com.iflytek.skillhub.domain.audit.AuditLogQueryService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.BDDMockito.given;
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
    private AuditLogQueryService auditLogQueryService;

    @Test
    void listAuditLogs_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listAuditLogs_withAuditorRole_returns200() throws Exception {
        AuditLog log1 = new AuditLog("user-1", "CREATE_SKILL", "SKILL", 123L, null, "192.168.1.1", "", null);
        AuditLog log2 = new AuditLog("user-2", "UPDATE_NAMESPACE", "NAMESPACE", 456L, null, "192.168.1.2", "", null);
        org.springframework.test.util.ReflectionTestUtils.setField(log1, "id", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(log2, "id", 2L);
        org.springframework.test.util.ReflectionTestUtils.setField(log1, "createdAt", Instant.now());
        org.springframework.test.util.ReflectionTestUtils.setField(log2, "createdAt", Instant.now());
        given(auditLogQueryService.list(0, 20, null, null))
            .willReturn(new PageImpl<>(List.of(log1, log2), PageRequest.of(0, 20), 2));

        PlatformPrincipal principal = new PlatformPrincipal(
            "user-50", "auditor", "auditor@example.com", "", "github", Set.of("AUDITOR")
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_AUDITOR"))
        );

        mockMvc.perform(get("/api/v1/admin/audit-logs").with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void listAuditLogs_withSuperAdminRole_returns200() throws Exception {
        given(auditLogQueryService.list(0, 20, null, null))
            .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        PlatformPrincipal principal = new PlatformPrincipal(
            "user-99", "superadmin", "super@example.com", "", "github", Set.of("SUPER_ADMIN")
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );

        mockMvc.perform(get("/api/v1/admin/audit-logs").with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void listAuditLogs_withFilters_returns200() throws Exception {
        given(auditLogQueryService.list(0, 20, "user-1", "CREATE_SKILL"))
            .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        PlatformPrincipal principal = new PlatformPrincipal(
            "user-50", "auditor", "auditor@example.com", "", "github", Set.of("AUDITOR")
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_AUDITOR"))
        );

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                .param("userId", "user-1")
                .param("action", "CREATE_SKILL")
                .with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray());
    }
}
