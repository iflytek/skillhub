package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceGovernanceService;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberService;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.dto.NamespaceCandidateUserResponse;
import com.iflytek.skillhub.service.NamespaceMemberCandidateService;
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
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
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
    private NamespaceMemberService namespaceMemberService;

    @MockBean
    private com.iflytek.skillhub.domain.namespace.NamespaceRepository namespaceRepository;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private NamespaceMemberCandidateService namespaceMemberCandidateService;

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
    void getNamespace_hidesArchivedNamespaceFromAnonymousUsers() throws Exception {
        Namespace namespace = namespace(1L, "team-a", NamespaceStatus.ARCHIVED, NamespaceType.TEAM);
        given(namespaceService.getNamespaceBySlugForRead("team-a", null, Map.of())).willThrow(
                new com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException(
                        "error.namespace.slug.notFound",
                        "team-a"
                )
        );

        mockMvc.perform(get("/api/v1/namespaces/team-a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
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

    @Test
    void updateNamespace_returnsUpdatedNamespace() throws Exception {
        Namespace existing = namespace(1L, "team-a", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        Namespace updated = new Namespace("team-a", "Team A+", "owner-1");
        setField(updated, "id", 1L);
        updated.setStatus(NamespaceStatus.ACTIVE);
        updated.setType(NamespaceType.TEAM);
        updated.setDescription("Updated description");
        given(namespaceService.getNamespaceBySlug("team-a")).willReturn(existing);
        given(namespaceService.updateNamespace(1L, "Team A+", "Updated description", null, "owner-1"))
                .willReturn(updated);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/namespaces/team-a")
                        .with(csrf())
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"team-a","displayName":"Team A+","description":"Updated description"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.slug").value("team-a"))
                .andExpect(jsonPath("$.data.displayName").value("Team A+"))
                .andExpect(jsonPath("$.data.description").value("Updated description"));
    }

    @Test
    void listMembers_forNonMember_returns403() throws Exception {
        Namespace namespace = namespace(1L, "team-a", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        given(namespaceService.getNamespaceBySlug("team-a")).willReturn(namespace);
        doThrow(new DomainForbiddenException("error.namespace.membership.required"))
                .when(namespaceService).assertMember(1L, "guest-1");

        mockMvc.perform(get("/api/v1/namespaces/team-a/members")
                        .with(auth("guest-1"))
                        .requestAttr("userId", "guest-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void searchMemberCandidates_returnsCandidates() throws Exception {
        Namespace namespace = namespace(1L, "team-a", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        given(namespaceService.getNamespaceBySlug("team-a")).willReturn(namespace);
        given(namespaceMemberCandidateService.searchCandidates("team-a", "ali", "owner-1", 10))
                .willReturn(List.of(new NamespaceCandidateUserResponse(
                        "user-2",
                        "alice",
                        "alice@example.com",
                        "ACTIVE"
                )));

        mockMvc.perform(get("/api/v1/namespaces/team-a/member-candidates")
                        .param("search", "ali")
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].userId").value("user-2"))
                .andExpect(jsonPath("$.data[0].displayName").value("alice"));
    }

    @Test
    void addMember_returnsCreatedMember() throws Exception {
        Namespace namespace = namespace(1L, "team-a", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        NamespaceMember member = new NamespaceMember(1L, "user-2", NamespaceRole.ADMIN);
        given(namespaceService.getNamespaceBySlug("team-a")).willReturn(namespace);
        given(namespaceMemberService.addMember(1L, "user-2", NamespaceRole.ADMIN, "owner-1"))
                .willReturn(member);

        mockMvc.perform(post("/api/v1/namespaces/team-a/members")
                        .with(csrf())
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user-2","role":"ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value("user-2"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void removeMember_returnsSuccessMessage() throws Exception {
        Namespace namespace = namespace(1L, "team-a", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        given(namespaceService.getNamespaceBySlug("team-a")).willReturn(namespace);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/namespaces/team-a/members/user-2")
                        .with(csrf())
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.message").value("Member removed successfully"));
    }

    @Test
    void updateMemberRole_returnsUpdatedMember() throws Exception {
        Namespace namespace = namespace(1L, "team-a", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        NamespaceMember member = new NamespaceMember(1L, "user-2", NamespaceRole.OWNER);
        given(namespaceService.getNamespaceBySlug("team-a")).willReturn(namespace);
        given(namespaceMemberService.updateMemberRole(1L, "user-2", NamespaceRole.OWNER, "owner-1"))
                .willReturn(member);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/namespaces/team-a/members/user-2/role")
                        .with(csrf())
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"OWNER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value("user-2"))
                .andExpect(jsonPath("$.data.role").value("OWNER"));
    }

    @Test
    void createNamespace_requiresPlatformAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/namespaces")
                        .with(csrf())
                        .with(auth("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"team-alpha","displayName":"Team Alpha"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void createNamespace_allowsSkillAdmin() throws Exception {
        Namespace namespace = namespace(2L, "team-admin", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        given(namespaceService.createNamespace("team-admin", "Team Admin", null, "admin-1"))
                .willReturn(namespace);

        mockMvc.perform(post("/api/v1/namespaces")
                        .with(csrf())
                        .with(auth("admin-1", Set.of("SKILL_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"team-admin","displayName":"Team Admin"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.slug").value("team-admin"));
    }

    private RequestPostProcessor auth(String userId) {
        return auth(userId, Set.of());
    }

    private RequestPostProcessor auth(String userId, Set<String> platformRoles) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId,
                userId,
                userId + "@example.com",
                "",
                "session",
                platformRoles
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
