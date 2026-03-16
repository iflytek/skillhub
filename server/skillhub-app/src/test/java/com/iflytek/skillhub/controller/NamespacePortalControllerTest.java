package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceGovernanceService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NamespacePortalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceService namespaceService;

    @MockBean
    private NamespaceGovernanceService namespaceGovernanceService;

    @MockBean
    private com.iflytek.skillhub.domain.namespace.NamespaceRepository namespaceRepository;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @Test
    void listMyNamespaces_returnsFrozenAndArchivedNamespacesWithCurrentRole() throws Exception {
        Namespace namespace = namespace(1L, "team-a", NamespaceStatus.ARCHIVED, NamespaceType.TEAM);
        given(namespaceRepository.findByIdIn(List.of(1L))).willReturn(List.of(namespace));
        given(namespaceMemberRepository.findByUserId("owner-1"))
                .willReturn(List.of(new com.iflytek.skillhub.domain.namespace.NamespaceMember(1L, "owner-1", NamespaceRole.OWNER)));

        mockMvc.perform(get("/api/v1/me/namespaces")
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].slug").value("team-a"))
                .andExpect(jsonPath("$.data[0].status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data[0].currentUserRole").value("OWNER"));
    }

    @Test
    void archiveNamespace_returnsUpdatedNamespace() throws Exception {
        Namespace archived = namespace(1L, "team-a", NamespaceStatus.ARCHIVED, NamespaceType.TEAM);
        given(namespaceGovernanceService.archiveNamespace(eq("team-a"), eq("owner-1"), eq("cleanup"), nullable(String.class), any(), any()))
                .willReturn(archived);

        mockMvc.perform(post("/api/v1/namespaces/team-a/archive")
                        .with(csrf())
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"cleanup\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.slug").value("team-a"))
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
    }

    private RequestPostProcessor auth(String userId) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId,
                userId,
                userId + "@example.com",
                "",
                "session",
                java.util.Set.of()
        );
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        return authentication(authenticationToken);
    }

    private Namespace namespace(Long id, String slug, NamespaceStatus status, NamespaceType type) {
        Namespace namespace = new Namespace(slug, "Team A", "owner-1");
        setField(namespace, "id", id);
        namespace.setStatus(status);
        namespace.setType(type);
        return namespace;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
