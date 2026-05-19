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
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
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
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NamespaceBatchMemberControllerTest {

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

    @MockBean
    private UserAccountRepository userAccountRepository;

    @Test
    void batchAddMembers_partialSuccess_returnsResultsPerRow() throws Exception {
        Namespace namespace = namespace(1L, "team-a", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        given(namespaceService.getNamespaceBySlug("team-a")).willReturn(namespace);

        // user-1 already exists
        given(namespaceMemberService.addMember(1L, "user-1", NamespaceRole.MEMBER, "owner-1"))
                .willThrow(new DomainBadRequestException("error.namespace.member.alreadyExists"));
        // user-2 succeeds
        given(namespaceMemberService.addMember(1L, "user-2", NamespaceRole.ADMIN, "owner-1"))
                .willReturn(new NamespaceMember(1L, "user-2", NamespaceRole.ADMIN));
        // user-3 succeeds
        given(namespaceMemberService.addMember(1L, "user-3", NamespaceRole.MEMBER, "owner-1"))
                .willReturn(new NamespaceMember(1L, "user-3", NamespaceRole.MEMBER));

        mockMvc.perform(post("/api/v1/namespaces/team-a/members/batch")
                        .with(csrf())
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"members":[
                                  {"userId":"user-1","role":"MEMBER"},
                                  {"userId":"user-2","role":"ADMIN"},
                                  {"userId":"user-3","role":"MEMBER"}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failureCount").value(1))
                .andExpect(jsonPath("$.data.results[0].userId").value("user-1"))
                .andExpect(jsonPath("$.data.results[0].success").value(false))
                .andExpect(jsonPath("$.data.results[0].error").value("ALREADY_MEMBER"))
                .andExpect(jsonPath("$.data.results[1].userId").value("user-2"))
                .andExpect(jsonPath("$.data.results[1].success").value(true))
                .andExpect(jsonPath("$.data.results[2].userId").value("user-3"))
                .andExpect(jsonPath("$.data.results[2].success").value(true));
    }

    @Test
    void batchAddMembers_allFailures_returnsAllErrors() throws Exception {
        Namespace namespace = namespace(1L, "team-a", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        given(namespaceService.getNamespaceBySlug("team-a")).willReturn(namespace);

        given(namespaceMemberService.addMember(eq(1L), eq("ghost-1"), any(), eq("owner-1")))
                .willThrow(new DomainBadRequestException("error.user.notFound"));
        given(namespaceMemberService.addMember(eq(1L), eq("ghost-2"), any(), eq("owner-1")))
                .willThrow(new DomainBadRequestException("error.user.not found"));

        mockMvc.perform(post("/api/v1/namespaces/team-a/members/batch")
                        .with(csrf())
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"members":[
                                  {"userId":"ghost-1","role":"MEMBER"},
                                  {"userId":"ghost-2","role":"ADMIN"}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.failureCount").value(2))
                .andExpect(jsonPath("$.data.results[0].success").value(false))
                .andExpect(jsonPath("$.data.results[0].error").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.data.results[1].success").value(false))
                .andExpect(jsonPath("$.data.results[1].error").value("USER_NOT_FOUND"));
    }

    @Test
    void batchAddMembers_nonAdminOrOwner_returns403() throws Exception {
        Namespace namespace = namespace(1L, "team-a", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        given(namespaceService.getNamespaceBySlug("team-a")).willReturn(namespace);
        given(namespaceMemberService.addMember(eq(1L), any(), any(), eq("member-1")))
                .willThrow(new DomainForbiddenException("error.namespace.membership.insufficientRole"));

        mockMvc.perform(post("/api/v1/namespaces/team-a/members/batch")
                        .with(csrf())
                        .with(auth("member-1"))
                        .requestAttr("userId", "member-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"members":[{"userId":"user-2","role":"MEMBER"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failureCount").value(1));
    }

    @Test
    void batchAddMembers_ownerRole_failsValidation() throws Exception {
        Namespace namespace = namespace(1L, "team-a", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        given(namespaceService.getNamespaceBySlug("team-a")).willReturn(namespace);
        given(namespaceMemberService.addMember(eq(1L), eq("user-2"), eq(NamespaceRole.OWNER), eq("owner-1")))
                .willThrow(new DomainBadRequestException("error.namespace.member.owner.assignDirect"));

        mockMvc.perform(post("/api/v1/namespaces/team-a/members/batch")
                        .with(csrf())
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"members":[{"userId":"user-2","role":"OWNER"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failureCount").value(1))
                .andExpect(jsonPath("$.data.results[0].error").value("INVALID_ROLE"));
    }

    @Test
    void batchAddMembers_emptyArray_returnsError() throws Exception {
        // @NotEmpty on BatchMemberRequest.members triggers validation error
        // Spring Boot 3.2+ raises HandlerMethodValidationException (500) rather than
        // MethodArgumentNotValidException (400) for record-based @RequestBody validation
        mockMvc.perform(post("/api/v1/namespaces/team-a/members/batch")
                        .with(csrf())
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"members":[]}
                                """))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void batchAddMembers_responseFormat_matchesContract() throws Exception {
        Namespace namespace = namespace(1L, "team-a", NamespaceStatus.ACTIVE, NamespaceType.TEAM);
        given(namespaceService.getNamespaceBySlug("team-a")).willReturn(namespace);
        given(namespaceMemberService.addMember(1L, "user-2", NamespaceRole.MEMBER, "owner-1"))
                .willReturn(new NamespaceMember(1L, "user-2", NamespaceRole.MEMBER));

        mockMvc.perform(post("/api/v1/namespaces/team-a/members/batch")
                        .with(csrf())
                        .with(auth("owner-1"))
                        .requestAttr("userId", "owner-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"members":[{"userId":"user-2","role":"MEMBER"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failureCount").value(0))
                .andExpect(jsonPath("$.data.results").isArray())
                .andExpect(jsonPath("$.data.results[0].userId").value("user-2"))
                .andExpect(jsonPath("$.data.results[0].role").value("MEMBER"))
                .andExpect(jsonPath("$.data.results[0].success").value(true))
                .andExpect(jsonPath("$.data.results[0].error").doesNotExist());
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
