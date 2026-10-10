package com.iflytek.skillhub.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.dto.ExternalRoleGrantCreateRequest;
import com.iflytek.skillhub.dto.SystemAuthSettingsResponse;
import com.iflytek.skillhub.dto.SystemAuthSettingsUpdateRequest;
import com.iflytek.skillhub.service.SystemAuthSettingsAppService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemAuthSettingsControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private SystemAuthSettingsAppService service;
    @MockBean private NamespaceMemberRepository namespaceMemberRepository;

    @Test
    void superAdminCanChangeSettings() throws Exception {
        when(service.updateLocalSettings(any(), eq("admin"), any()))
                .thenReturn(new SystemAuthSettingsResponse(false, true, 1L, Instant.now()));

        mockMvc.perform(put("/api/v1/admin/system-config/auth/local")
                        .with(authentication(auth("SUPER_ADMIN"))).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"passwordLoginEnabled":false,"selfRegistrationEnabled":true,"version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordLoginEnabled").value(false));
    }

    @Test
    void nonSuperAdminCannotChangeSettingsOrCreateGrant() throws Exception {
        mockMvc.perform(put("/api/v1/admin/system-config/auth/local")
                        .with(authentication(auth("USER_ADMIN"))).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"passwordLoginEnabled":false,"selfRegistrationEnabled":true,"version":0}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/system-config/role-grants")
                        .with(authentication(auth("USER_ADMIN"))).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"providerCode":"feishu","email":"admin@example.com","roleCode":"SUPER_ADMIN"}
                                """))
                .andExpect(status().isForbidden());
        verify(service, never()).updateLocalSettings(any(SystemAuthSettingsUpdateRequest.class), any(), any());
        verify(service, never()).createRule(any(ExternalRoleGrantCreateRequest.class), any(), any());
    }

    private UsernamePasswordAuthenticationToken auth(String role) {
        PlatformPrincipal principal = new PlatformPrincipal("admin", "admin", "a@example.com", "", "github", Set.of(role));
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
