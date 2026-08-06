package com.iflytek.skillhub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
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
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @Autowired
    private ObjectMapper objectMapper;

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

    @MockBean
    private UserAccountRepository userAccountRepository;

    @MockBean
    private UserRoleBindingRepository userRoleBindingRepository;

    @Test
    void listNamespaces_superAdminReturnsAllActiveNamespacesWithoutMembership() throws Exception {
        Namespace teamA = namespace(1L, "team-a", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        Namespace teamB = namespace(2L, "team-b", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        given(namespaceMemberRepository.findByUserId("super-1")).willReturn(List.of());
        given(namespaceRepository.findByStatus(eq(NamespaceStatus.ACTIVE), any()))
                .willReturn(new org.springframework.data.domain.PageImpl<>(
                        List.of(teamA, teamB),
                        org.springframework.data.domain.PageRequest.of(0, 20),
                        2
                ));

        mockMvc.perform(get("/api/v1/namespaces")
                        .with(auth("super-1", Set.of("SUPER_ADMIN")))
                        .requestAttr("userId", "super-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].slug").value("team-a"))
                .andExpect(jsonPath("$.data.items[1].slug").value("team-b"))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void listMyNamespaces_superAdminKeepsLegacyArrayContract() throws Exception {
        Namespace active = namespace(1L, "active", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        Namespace archived = namespace(3L, "archived", NamespaceStatus.ARCHIVED, NamespaceType.TEAM);
        given(namespaceMemberRepository.findByUserId("super-1")).willReturn(List.of());
        given(namespaceRepository.findAll(any()))
                .willReturn(new org.springframework.data.domain.PageImpl<>(
                        List.of(active, archived),
                        org.springframework.data.domain.PageRequest.of(0, 2),
                        2
                ));

        mockMvc.perform(get("/api/v1/me/namespaces")
                        .param("page", "0")
                        .param("size", "2")
                        .with(auth("super-1", Set.of("SUPER_ADMIN")))
                        .requestAttr("userId", "super-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].slug").value("active"))
                .andExpect(jsonPath("$.data[0].currentUserRole").doesNotExist())
                .andExpect(jsonPath("$.data[1].slug").value("archived"))
                .andExpect(jsonPath("$.data.items").doesNotExist());
    }

    @Test
    void listMyNamespacesPage_superAdminReturnsPagedNamespacesWithoutMembership() throws Exception {
        Namespace active = namespace(1L, "active", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        Namespace archived = namespace(3L, "archived", NamespaceStatus.ARCHIVED, NamespaceType.TEAM);
        given(namespaceMemberRepository.findByUserId("super-1")).willReturn(List.of());
        given(namespaceRepository.search(eq(null), eq(null), eq(null), eq(null), any()))
                .willReturn(new org.springframework.data.domain.PageImpl<>(
                        List.of(active, archived),
                        org.springframework.data.domain.PageRequest.of(0, 2),
                        3
                ));

        mockMvc.perform(get("/api/web/me/namespaces/page")
                        .param("page", "0")
                        .param("size", "2")
                        .with(auth("super-1", Set.of("SUPER_ADMIN")))
                        .requestAttr("userId", "super-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].slug").value("active"))
                .andExpect(jsonPath("$.data.items[0].currentUserRole").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].slug").value("archived"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2));
    }

    @Test
    void listMyNamespacesPage_bindsAndAppliesOptionalFilters() throws Exception {
        Namespace active = namespace(1L, "team-ai", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        given(namespaceMemberRepository.findByUserId("owner-1"))
                .willReturn(List.of(new NamespaceMember(1L, "owner-1", NamespaceRole.OWNER)));
        given(namespaceRepository.searchByIdIn(
                eq(List.of(1L)),
                eq(NamespaceStatus.ACTIVE),
                eq(NamespaceType.TEAM),
                eq("team"),
                eq("team-ai"),
                any()
        )).willReturn(new org.springframework.data.domain.PageImpl<>(
                List.of(active),
                org.springframework.data.domain.PageRequest.of(0, 20),
                1
        ));

        mockMvc.perform(get("/api/v1/me/namespaces/page")
                        .param("page", "0")
                        .param("size", "20")
                        .param("status", "ACTIVE")
                        .param("type", "TEAM")
                        .param("q", "team")
                        .param("slug", "team-ai")
                        .param("roles", "OWNER", "ADMIN")
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].slug").value("team-ai"))
                .andExpect(jsonPath("$.data.items[0].currentUserRole").value("OWNER"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void listMyNamespacesPage_refreshesGrantedSuperAdminRoleForExistingSession() throws Exception {
        Namespace active = namespace(1L, "active", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        Namespace archived = namespace(2L, "archived", NamespaceStatus.ARCHIVED, NamespaceType.TEAM);
        PlatformPrincipal stalePrincipal = principal("target-1", Set.of("USER"));
        MockHttpSession session = sessionWithPrincipal(stalePrincipal);

        given(userRoleBindingRepository.findByUserId("target-1"))
                .willReturn(List.of(new UserRoleBinding("target-1", role("SUPER_ADMIN"))));
        given(namespaceMemberRepository.findByUserId("target-1")).willReturn(List.of());
        given(namespaceRepository.search(eq(null), eq(null), eq(null), eq(null), any()))
                .willReturn(new org.springframework.data.domain.PageImpl<>(
                        List.of(active, archived),
                        org.springframework.data.domain.PageRequest.of(0, 20),
                        2
                ));

        mockMvc.perform(get("/api/v1/me/namespaces/page")
                        .param("page", "0")
                        .param("size", "20")
                        .session(session)
                        .with(auth(stalePrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].slug").value("active"))
                .andExpect(jsonPath("$.data.items[1].slug").value("archived"))
                .andExpect(jsonPath("$.data.total").value(2));

        PlatformPrincipal refreshedPrincipal = (PlatformPrincipal) session.getAttribute("platformPrincipal");
        assertThat(refreshedPrincipal.platformRoles()).containsExactlyInAnyOrder("SUPER_ADMIN");
        assertThat(sessionAuthorities(session)).containsExactly("ROLE_SUPER_ADMIN");
    }

    @Test
    void listMyNamespacesPage_refreshesRevokedSuperAdminRoleForExistingSession() throws Exception {
        PlatformPrincipal stalePrincipal = principal("target-2", Set.of("SUPER_ADMIN"));
        MockHttpSession session = sessionWithPrincipal(stalePrincipal);

        given(userRoleBindingRepository.findByUserId("target-2")).willReturn(List.of());
        given(namespaceMemberRepository.findByUserId("target-2")).willReturn(List.of());

        mockMvc.perform(get("/api/v1/me/namespaces/page")
                        .param("page", "0")
                        .param("size", "20")
                        .session(session)
                        .with(auth(stalePrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0));

        verify(namespaceRepository, never()).search(eq(null), eq(null), eq(null), eq(null), any());
        PlatformPrincipal refreshedPrincipal = (PlatformPrincipal) session.getAttribute("platformPrincipal");
        assertThat(refreshedPrincipal.platformRoles()).containsExactly("USER");
        assertThat(sessionAuthorities(session)).containsExactly("ROLE_USER");
    }

    @Test
    void openApi_myNamespacesPageExposesFlatPagingParameters() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode document = objectMapper.readTree(body);

        assertMyNamespacesPageParameters(document, "/api/v1/me/namespaces/page");
        assertMyNamespacesPageParameters(document, "/api/web/me/namespaces/page");
    }

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
                .andExpect(jsonPath("$.data[0].currentUserRole").value("OWNER"))
                .andExpect(jsonPath("$.data[0].canDelete").value(false));
    }

    @Test
    void getNamespace_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/namespaces/team-a"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getNamespace_superAdminReadsNamespaceWithoutMembership() throws Exception {
        Namespace namespace = namespace(1L, "team-a", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        given(namespaceMemberRepository.findByUserId("super-1")).willReturn(List.of());
        given(namespaceService.getNamespaceBySlug("team-a")).willReturn(namespace);

        mockMvc.perform(get("/api/v1/namespaces/team-a")
                        .with(auth("super-1", Set.of("SUPER_ADMIN")))
                        .requestAttr("userId", "super-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.slug").value("team-a"));
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
    void deleteNamespace_returnsSuccessMessage() throws Exception {
        Namespace existing = namespace(1L, "team-a", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        given(namespaceService.getNamespaceBySlug("team-a")).willReturn(existing);

        mockMvc.perform(delete("/api/v1/namespaces/team-a")
                        .with(csrf())
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.message").value("Namespace deleted successfully"));
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
    void listMembers_globalNamespaceRejectsRegularUsers() throws Exception {
        Namespace namespace = namespace(1L, "global", NamespaceStatus.ACTIVE, NamespaceType.GLOBAL);
        given(namespaceService.getNamespaceBySlug("global")).willReturn(namespace);
        given(namespaceMemberService.listMembers(eq(1L), any()))
                .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/namespaces/global/members")
                        .with(auth("regular-1"))
                        .requestAttr("userId", "regular-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void listMembers_globalNamespaceAllowsUserAdminWithoutMembership() throws Exception {
        Namespace namespace = namespace(1L, "global", NamespaceStatus.ACTIVE, NamespaceType.GLOBAL);
        NamespaceMember member = new NamespaceMember(1L, "user-2", NamespaceRole.MEMBER);
        UserAccount user = new UserAccount("user-2", "Alice", "alice@example.com", null);
        given(namespaceService.getNamespaceBySlug("global")).willReturn(namespace);
        doThrow(new DomainForbiddenException("error.namespace.membership.required"))
                .when(namespaceService).assertMember(1L, "user-admin-1");
        given(namespaceMemberService.listMembers(eq(1L), any()))
                .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(member), org.springframework.data.domain.PageRequest.of(0, 20), 1));
        given(userAccountRepository.findByIdIn(List.of("user-2"))).willReturn(List.of(user));

        mockMvc.perform(get("/api/v1/namespaces/global/members")
                        .with(auth("user-admin-1", Set.of("USER_ADMIN")))
                        .requestAttr("userId", "user-admin-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].userId").value("user-2"))
                .andExpect(jsonPath("$.data.items[0].email").value("alice@example.com"));
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
        UserAccount user = new UserAccount("user-2", "Alice", "alice@example.com", null);
        given(namespaceService.getNamespaceBySlug("team-a")).willReturn(namespace);
        given(namespaceMemberService.addMember(1L, "user-2", NamespaceRole.ADMIN, "owner-1"))
                .willReturn(member);
        given(userAccountRepository.findById("user-2"))
                .willReturn(java.util.Optional.of(user));

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
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.displayName").value("Alice"))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"));
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
        UserAccount user = new UserAccount("user-2", "Alice", "alice@example.com", null);
        given(namespaceService.getNamespaceBySlug("team-a")).willReturn(namespace);
        given(namespaceMemberService.updateMemberRole(1L, "user-2", NamespaceRole.OWNER, "owner-1"))
                .willReturn(member);
        given(userAccountRepository.findById("user-2"))
                .willReturn(java.util.Optional.of(user));

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
                .andExpect(jsonPath("$.data.role").value("OWNER"))
                .andExpect(jsonPath("$.data.displayName").value("Alice"))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"));
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
        PlatformPrincipal principal = principal(userId, platformRoles);
        return auth(principal);
    }

    private RequestPostProcessor auth(PlatformPrincipal principal) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                authorities(principal.platformRoles())
        );
        return authentication(authenticationToken);
    }

    private PlatformPrincipal principal(String userId, Set<String> platformRoles) {
        return new PlatformPrincipal(
                userId,
                userId,
                userId + "@example.com",
                "",
                "session",
                platformRoles
        );
    }

    private MockHttpSession sessionWithPrincipal(PlatformPrincipal principal) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("platformPrincipal", principal);
        return session;
    }

    private List<SimpleGrantedAuthority> authorities(Set<String> platformRoles) {
        Set<String> roles = platformRoles == null || platformRoles.isEmpty() ? Set.of("USER") : platformRoles;
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    private List<String> sessionAuthorities(MockHttpSession session) {
        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        return context.getAuthentication().getAuthorities().stream()
                .map(Object::toString)
                .toList();
    }

    private void assertMyNamespacesPageParameters(JsonNode document, String path) {
        JsonNode parameters = document.at("/paths/" + escapeJsonPointer(path) + "/get/parameters");
        assertThat(parameters.isArray()).isTrue();
        List<String> parameterNames = parameterNames(parameters);
        assertThat(parameterNames)
                .contains("page", "size", "sort", "status", "type", "q", "slug", "roles")
                .doesNotContain("pageable");
        assertThat(parameter(parameters, "page").path("schema").path("type").asText()).isEqualTo("integer");
        assertThat(parameter(parameters, "size").path("schema").path("type").asText()).isEqualTo("integer");
    }

    private List<String> parameterNames(JsonNode parameters) {
        return java.util.stream.StreamSupport.stream(parameters.spliterator(), false)
                .map(parameter -> parameter.path("name").asText())
                .toList();
    }

    private JsonNode parameter(JsonNode parameters, String name) {
        return java.util.stream.StreamSupport.stream(parameters.spliterator(), false)
                .filter(parameter -> name.equals(parameter.path("name").asText()))
                .findFirst()
                .orElseThrow();
    }

    private String escapeJsonPointer(String path) {
        return path.replace("~", "~0").replace("/", "~1");
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

    private Role role(String code) {
        Role role = new Role();
        ReflectionTestUtils.setField(role, "code", code);
        ReflectionTestUtils.setField(role, "name", code);
        return role;
    }
}
