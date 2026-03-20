package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.PromotionService;
import com.iflytek.skillhub.domain.review.ReviewPermissionChecker;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.dto.PromotionResponseDto;
import com.iflytek.skillhub.repository.GovernanceQueryRepository;
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
import java.util.Optional;
import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PromotionPortalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PromotionService promotionService;

    @MockBean
    private PromotionRequestRepository promotionRequestRepository;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private com.iflytek.skillhub.domain.namespace.NamespaceRepository namespaceRepository;

    @MockBean
    private GovernanceQueryRepository governanceQueryRepository;

    @MockBean
    private RbacService rbacService;

    @MockBean
    private ReviewPermissionChecker permissionChecker;

    @MockBean
    private AuditLogService auditLogService;

    @Test
    void submitPromotion_passesNamespaceRolesToService() throws Exception {
        PromotionRequest request = createPromotionRequest(1L, "user-1");
        stubNamespaceRoles("user-1", List.of(new NamespaceMember(5L, "user-1", NamespaceRole.ADMIN)));
        given(rbacService.getUserRoleCodes("user-1")).willReturn(Set.of());
        given(promotionService.submitPromotion(10L, 20L, 30L, "user-1", Map.of(5L, NamespaceRole.ADMIN), Set.of()))
                .willReturn(request);
        stubPromotionResponse(request);

        mockMvc.perform(post("/api/v1/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceSkillId\":10,\"sourceVersionId\":20,\"targetNamespaceId\":30}")
                        .with(csrf())
                        .with(auth("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1L));
    }

    @Test
    void listPendingPromotions_forbidsRegularUser() throws Exception {
        stubNamespaceRoles("user-1", List.of());
        given(rbacService.getUserRoleCodes("user-1")).willReturn(Set.of());
        given(permissionChecker.canListPendingPromotions(Set.of())).willReturn(false);

        mockMvc.perform(get("/api/v1/promotions/pending").with(auth("user-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(promotionRequestRepository, never()).findByStatus(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getPromotionDetail_allowsSubmitter() throws Exception {
        PromotionRequest request = createPromotionRequest(1L, "user-1");
        stubNamespaceRoles("user-1", List.of());
        given(promotionRequestRepository.findById(1L)).willReturn(Optional.of(request));
        given(rbacService.getUserRoleCodes("user-1")).willReturn(Set.of());
        given(promotionService.canViewPromotion(request, "user-1", Set.of())).willReturn(true);
        stubPromotionResponse(request);

        mockMvc.perform(get("/api/v1/promotions/1").with(auth("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.submittedBy").value("user-1"));
    }

    @Test
    void getPromotionDetail_forbidsUnrelatedUser() throws Exception {
        PromotionRequest request = createPromotionRequest(1L, "user-1");
        stubNamespaceRoles("user-9", List.of());
        given(promotionRequestRepository.findById(1L)).willReturn(Optional.of(request));
        given(rbacService.getUserRoleCodes("user-9")).willReturn(Set.of());
        given(promotionService.canViewPromotion(request, "user-9", Set.of())).willReturn(false);

        mockMvc.perform(get("/api/v1/promotions/1").with(auth("user-9")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private void stubPromotionResponse(PromotionRequest request) {
        given(governanceQueryRepository.getPromotionResponse(request)).willReturn(new PromotionResponseDto(
                request.getId(),
                request.getSourceSkillId(),
                "team-a",
                "skill-a",
                "1.0.0",
                "global",
                request.getTargetSkillId(),
                request.getStatus().name(),
                request.getSubmittedBy(),
                "Submitter",
                request.getReviewedBy(),
                null,
                request.getReviewComment(),
                request.getSubmittedAt(),
                request.getReviewedAt()
        ));
    }

    private void stubNamespaceRoles(String userId, List<NamespaceMember> members) {
        given(namespaceMemberRepository.findByUserId(userId)).willReturn(members);
    }

    private RequestPostProcessor auth(String userId) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId,
                userId,
                userId + "@example.com",
                "",
                "session",
                Set.of()
        );
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        return authentication(authenticationToken);
    }

    private PromotionRequest createPromotionRequest(Long id, String submittedBy) {
        PromotionRequest request = new PromotionRequest(10L, 20L, 30L, submittedBy);
        setField(request, "id", id);
        setField(request, "status", ReviewTaskStatus.PENDING);
        return request;
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
