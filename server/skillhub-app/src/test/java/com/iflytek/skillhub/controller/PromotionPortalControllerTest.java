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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    void listPromotions_defaultsToPendingWithStableSubmittedSort() throws Exception {
        PromotionRequest request = createPromotionRequest(1L, "user-1");
        stubNamespaceRoles("admin", List.of());
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        PageRequest pageable = PageRequest.of(
                0,
                20,
                Sort.by(
                        new Sort.Order(Sort.Direction.DESC, "submittedAt"),
                        new Sort.Order(Sort.Direction.DESC, "id")
                )
        );
        given(promotionRequestRepository.findByStatus(ReviewTaskStatus.PENDING, pageable))
                .willReturn(new PageImpl<>(List.of(request), pageable, 1));
        stubPromotionListResponse(List.of(request));

        mockMvc.perform(get("/api/v1/promotions").with(auth("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].id").value(1L))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(promotionRequestRepository).findByStatus(ReviewTaskStatus.PENDING, pageable);
    }

    @Test
    void listPromotions_sortsApprovedHistoryByReviewedAtDescendingByDefault() throws Exception {
        PromotionRequest request = createPromotionRequest(1L, "user-1");
        stubNamespaceRoles("admin", List.of());
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        PageRequest pageable = PageRequest.of(1, 5);
        given(promotionRequestRepository.findHistoryByStatusOrderByReviewedAtDesc(ReviewTaskStatus.APPROVED, pageable))
                .willReturn(new PageImpl<>(List.of(request), pageable, 1));
        stubPromotionListResponse(List.of(request));

        mockMvc.perform(get("/api/web/promotions")
                        .param("status", "APPROVED")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sortBy", "reviewedAt")
                        .with(auth("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(promotionRequestRepository).findHistoryByStatusOrderByReviewedAtDesc(ReviewTaskStatus.APPROVED, pageable);
    }

    @Test
    void listPromotions_sortsRejectedHistoryByReviewedAtAscending() throws Exception {
        PromotionRequest request = createPromotionRequest(1L, "user-1");
        stubNamespaceRoles("admin", List.of());
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SUPER_ADMIN"));
        PageRequest pageable = PageRequest.of(0, 10);
        given(promotionRequestRepository.findHistoryByStatusOrderByReviewedAtAsc(ReviewTaskStatus.REJECTED, pageable))
                .willReturn(new PageImpl<>(List.of(request), pageable, 1));
        stubPromotionListResponse(List.of(request));

        mockMvc.perform(get("/api/web/promotions")
                        .param("status", "REJECTED")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "reviewedAt")
                        .param("sortDirection", "ASC")
                        .with(auth("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(promotionRequestRepository).findHistoryByStatusOrderByReviewedAtAsc(ReviewTaskStatus.REJECTED, pageable);
    }

    @Test
    void listPromotions_rejectsInvalidStatus() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));

        mockMvc.perform(get("/api/v1/promotions")
                        .param("status", "DONE")
                        .header("Accept-Language", "en")
                        .locale(java.util.Locale.ENGLISH)
                        .with(auth("admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("DONE")));
    }

    @Test
    void listPromotions_rejectsBlankStatus() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));

        mockMvc.perform(get("/api/v1/promotions")
                        .param("status", "")
                        .header("Accept-Language", "en")
                        .locale(java.util.Locale.ENGLISH)
                        .with(auth("admin")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listPromotions_rejectsPendingSortFieldEvenWhenBlank() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));

        mockMvc.perform(get("/api/v1/promotions")
                        .param("status", "PENDING")
                        .param("sortBy", "")
                        .header("Accept-Language", "en")
                        .locale(java.util.Locale.ENGLISH)
                        .with(auth("admin")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listPromotions_rejectsPendingSortDirectionEvenWhenBlank() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));

        mockMvc.perform(get("/api/v1/promotions")
                        .param("status", "PENDING")
                        .param("sortDirection", "")
                        .header("Accept-Language", "en")
                        .locale(java.util.Locale.ENGLISH)
                        .with(auth("admin")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listPromotions_rejectsInvalidHistorySortField() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));

        mockMvc.perform(get("/api/web/promotions")
                        .param("status", "APPROVED")
                        .param("sortBy", "submittedAt")
                        .param("sortDirection", "DESC")
                        .header("Accept-Language", "en")
                        .locale(java.util.Locale.ENGLISH)
                        .with(auth("admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("submittedAt")));
    }

    @Test
    void listPromotions_rejectsInvalidHistorySortDirection() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));

        mockMvc.perform(get("/api/web/promotions")
                        .param("status", "APPROVED")
                        .param("sortBy", "reviewedAt")
                        .param("sortDirection", "SIDEWAYS")
                        .header("Accept-Language", "en")
                        .locale(java.util.Locale.ENGLISH)
                        .with(auth("admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("SIDEWAYS")));
    }

    @Test
    void listPromotions_rejectsBlankHistorySortDirection() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));

        mockMvc.perform(get("/api/web/promotions")
                        .param("status", "APPROVED")
                        .param("sortBy", "reviewedAt")
                        .param("sortDirection", "")
                        .header("Accept-Language", "en")
                        .locale(java.util.Locale.ENGLISH)
                        .with(auth("admin")))
                .andExpect(status().isBadRequest());
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
                "Skill A",
                "Skill A summary",
                "team-a",
                "skill-a",
                "1.0.0",
                3,
                2048L,
                7L,
                2,
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

    private void stubPromotionListResponse(List<PromotionRequest> requests) {
        given(governanceQueryRepository.getPromotionResponses(requests)).willReturn(
                requests.stream()
                        .map(request -> new PromotionResponseDto(
                                request.getId(),
                                request.getSourceSkillId(),
                                "Skill A",
                                "Skill A summary",
                                "team-a",
                                "skill-a",
                                "1.0.0",
                                3,
                                2048L,
                                7L,
                                2,
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
                        ))
                        .toList()
        );
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
