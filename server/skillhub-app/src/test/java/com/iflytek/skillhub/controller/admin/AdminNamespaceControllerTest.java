package com.iflytek.skillhub.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.AdminNamespaceListResponse;
import com.iflytek.skillhub.dto.AdminNamespaceListStatsResponse;
import com.iflytek.skillhub.dto.AdminNamespacePermissionsResponse;
import com.iflytek.skillhub.dto.AdminNamespaceStatsResponse;
import com.iflytek.skillhub.dto.AdminNamespaceSummaryResponse;
import com.iflytek.skillhub.dto.MemberResponse;
import com.iflytek.skillhub.service.AdminNamespaceAppService;
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
class AdminNamespaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminNamespaceAppService adminNamespaceAppService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @Test
    void listNamespaces_returnsAllNamespacesForSuperAdmin() throws Exception {
        when(adminNamespaceAppService.list("team", "ACTIVE", "TEAM", 0, 20, "admin"))
                .thenReturn(new AdminNamespaceListResponse(
                        List.of(namespaceSummary("team-a").toSummary()),
                        1,
                        0,
                        20,
                        new AdminNamespaceListStatsResponse(3, 2, 1, 0)));

        mockMvc.perform(get("/api/v1/admin/namespaces")
                        .queryParam("keyword", "team")
                        .queryParam("status", "ACTIVE")
                        .queryParam("type", "TEAM")
                        .with(authentication(superAdminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.stats.total").value(3))
                .andExpect(jsonPath("$.data.items[0].slug").value("team-a"))
                .andExpect(jsonPath("$.data.items[0].permissions.platformOverride").value(true));

        verify(adminNamespaceAppService).list("team", "ACTIVE", "TEAM", 0, 20, "admin");
    }

    @Test
    void listNamespaces_rejectsNonSuperAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/namespaces")
                        .with(authentication(userAdminAuth())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void addMember_delegatesPlatformAdminMutation() throws Exception {
        when(adminNamespaceAppService.addMember(eq("team-a"), any(), eq("admin")))
                .thenReturn(new MemberResponse(
                        10L,
                        1L,
                        "user-2",
                        "Alice",
                        "alice@example.com",
                        NamespaceRole.MEMBER,
                        Instant.parse("2026-08-12T00:00:00Z"),
                        Instant.parse("2026-08-12T00:00:00Z")));

        mockMvc.perform(post("/api/v1/admin/namespaces/team-a/members")
                        .with(authentication(superAdminAuth()))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId": "user-2",
                                  "role": "MEMBER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value("user-2"))
                .andExpect(jsonPath("$.data.role").value("MEMBER"));

        verify(adminNamespaceAppService).addMember(eq("team-a"), any(), eq("admin"));
    }

    @Test
    void freezeNamespace_passesAuditContextAndReason() throws Exception {
        when(adminNamespaceAppService.freeze(eq("team-a"), any(), eq("admin"), any()))
                .thenReturn(namespaceSummary("team-a").toDetail());

        mockMvc.perform(post("/api/v1/admin/namespaces/team-a/freeze")
                        .with(authentication(superAdminAuth()))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "reason": "temporary governance hold"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.slug").value("team-a"));

        verify(adminNamespaceAppService).freeze(eq("team-a"), any(), eq("admin"), any());
    }

    private AdminNamespaceSummary namespaceSummary(String slug) {
        return new AdminNamespaceSummary(
                1L,
                slug,
                "Team A",
                "ACTIVE",
                "Team namespace",
                "TEAM",
                null,
                "owner-1",
                Instant.parse("2026-08-12T00:00:00Z"),
                Instant.parse("2026-08-12T00:00:00Z"),
                new AdminNamespaceStatsResponse(2, 5),
                new AdminNamespacePermissionsResponse(
                        null,
                        true,
                        false,
                        true,
                        true,
                        true,
                        true,
                        true,
                        false,
                        true,
                        false));
    }

    private UsernamePasswordAuthenticationToken superAdminAuth() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "admin",
                "admin",
                "admin@example.com",
                "",
                "github",
                Set.of("SUPER_ADMIN"));
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken userAdminAuth() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-admin",
                "user admin",
                "user-admin@example.com",
                "",
                "github",
                Set.of("USER_ADMIN"));
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER_ADMIN")));
    }

    private record AdminNamespaceSummary(
            Long id,
            String slug,
            String displayName,
            String status,
            String description,
            String type,
            String avatarUrl,
            String createdBy,
            Instant createdAt,
            Instant updatedAt,
            AdminNamespaceStatsResponse stats,
            AdminNamespacePermissionsResponse permissions
    ) {
        AdminNamespaceSummaryResponse toSummary() {
            return new AdminNamespaceSummaryResponse(
                    id,
                    slug,
                    displayName,
                    status,
                    description,
                    type,
                    avatarUrl,
                    createdBy,
                    createdAt,
                    updatedAt,
                    stats,
                    permissions);
        }

        com.iflytek.skillhub.dto.AdminNamespaceDetailResponse toDetail() {
            return new com.iflytek.skillhub.dto.AdminNamespaceDetailResponse(
                    id,
                    slug,
                    displayName,
                    status,
                    description,
                    type,
                    avatarUrl,
                    createdBy,
                    createdAt,
                    updatedAt,
                    stats,
                    permissions);
        }
    }
}
