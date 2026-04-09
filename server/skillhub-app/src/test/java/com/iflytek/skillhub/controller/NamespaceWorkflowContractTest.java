package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceGovernanceService;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberService;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.NamespaceCandidateUserResponse;
import com.iflytek.skillhub.service.NamespaceMemberCandidateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NamespaceWorkflowContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NamespaceService namespaceService;

    @MockBean
    private NamespaceGovernanceService namespaceGovernanceService;

    @MockBean
    private NamespaceMemberService namespaceMemberService;

    @MockBean
    private NamespaceRepository namespaceRepository;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private NamespaceMemberCandidateService namespaceMemberCandidateService;

    @MockBean
    private UserAccountRepository userAccountRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @Test
    void namespaceWorkflowEndpoints_shareExpectedEnvelopeShapes() throws Exception {
        Namespace namespace = namespace(7L, "team-flow", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        Namespace frozen = namespace(7L, "team-flow", NamespaceStatus.FROZEN, NamespaceType.TEAM);
        Namespace archived = namespace(7L, "team-flow", NamespaceStatus.ARCHIVED, NamespaceType.TEAM);
        NamespaceMember adminMember = new NamespaceMember(7L, "user-admin", NamespaceRole.ADMIN);
        setMemberId(adminMember, 11L);

        given(namespaceService.createNamespace(eq("team-flow"), eq("Team Flow"), eq("workflow"), eq("owner-1")))
                .willReturn(namespace);
        given(namespaceService.getNamespaceBySlug("team-flow")).willReturn(namespace);
        given(namespaceGovernanceService.freezeNamespace(eq("team-flow"), eq("owner-1"), eq(null), eq(null), any(), any()))
                .willReturn(frozen);
        given(namespaceGovernanceService.archiveNamespace(eq("team-flow"), eq("owner-1"), eq("cleanup"), eq(null), any(), any()))
                .willReturn(archived);
        given(namespaceMemberCandidateService.searchCandidates("team-flow", "admin", "owner-1", 10))
                .willReturn(List.of(new NamespaceCandidateUserResponse("user-admin", "Admin", "admin@example.com", "ACTIVE")));
        given(namespaceMemberService.addMember(7L, "user-admin", NamespaceRole.ADMIN, "owner-1"))
                .willReturn(adminMember);
        given(namespaceMemberService.listMembers(eq(7L), any(org.springframework.data.domain.Pageable.class)))
                .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(adminMember)));
        given(namespaceMemberService.updateMemberRole(7L, "user-admin", NamespaceRole.ADMIN, "owner-1"))
                .willReturn(adminMember);
        given(userAccountRepository.findById("user-admin"))
                .willReturn(Optional.of(new UserAccount("user-admin", "Admin", "admin@example.com", null)));

        mockMvc.perform(post("/api/web/namespaces")
                        .with(csrf())
                        .with(auth("owner-1", Set.of("SKILL_ADMIN")))
                        .requestAttr("userId", "owner-1")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"team-flow\",\"displayName\":\"Team Flow\",\"description\":\"workflow\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.slug").value("team-flow"));

        mockMvc.perform(get("/api/web/namespaces/team-flow/member-candidates")
                        .param("search", "admin")
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].userId").value("user-admin"));

        mockMvc.perform(post("/api/web/namespaces/team-flow/members")
                        .with(csrf())
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"user-admin\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value("user-admin"));

        mockMvc.perform(get("/api/web/namespaces/team-flow/members")
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].userId").value("user-admin"));

        mockMvc.perform(put("/api/web/namespaces/team-flow/members/user-admin/role")
                        .with(csrf())
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        mockMvc.perform(post("/api/web/namespaces/team-flow/freeze")
                        .with(csrf())
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("FROZEN"));

        mockMvc.perform(post("/api/web/namespaces/team-flow/archive")
                        .with(csrf())
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"cleanup\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        mockMvc.perform(delete("/api/web/namespaces/team-flow/members/user-admin")
                        .with(csrf())
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.message").value("Member removed successfully"));
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
        Namespace namespace = new Namespace(slug, "Team Flow", "owner-1");
        setNamespaceId(namespace, id);
        namespace.setStatus(status);
        namespace.setType(type);
        return namespace;
    }

    private void setNamespaceId(Namespace namespace, Long id) {
        org.springframework.test.util.ReflectionTestUtils.setField(namespace, "id", id);
    }

    private void setMemberId(NamespaceMember member, Long id) {
        org.springframework.test.util.ReflectionTestUtils.setField(member, "id", id);
    }
}
