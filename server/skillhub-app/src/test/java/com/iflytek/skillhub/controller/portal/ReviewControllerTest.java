package com.iflytek.skillhub.controller.portal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.ReviewService;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.dto.ReviewTaskResponse;
import com.iflytek.skillhub.repository.GovernanceQueryRepository;
import com.iflytek.skillhub.service.ReviewSkillDetailAppService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewService reviewService;

    @MockBean
    private ReviewTaskRepository reviewTaskRepository;

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
    private AuditLogService auditLogService;

    @MockBean
    private ReviewSkillDetailAppService reviewSkillDetailAppService;

    @Test
    void approveReview_returnsUnifiedEnvelope() throws Exception {
        ReviewTask task = createReviewTask(1L, 20L, "user-1");
        stubNamespaceRoles("admin", List.of());
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.approveReview(1L, "admin", "looks good", Map.of(), Set.of("SKILL_ADMIN")))
                .willReturn(task);
        stubReviewResponse(task);

        mockMvc.perform(post("/api/v1/reviews/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"looks good\"}")
                        .with(csrf())
                        .with(auth("admin"))
                        .requestAttr("userId", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1L));
    }

    @Test
    void approveReview_withoutBody_usesNullComment() throws Exception {
        ReviewTask task = createReviewTask(1L, 20L, "user-1");
        stubNamespaceRoles("admin", List.of());
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.approveReview(1L, "admin", null, Map.of(), Set.of("SKILL_ADMIN")))
                .willReturn(task);
        stubReviewResponse(task);

        mockMvc.perform(post("/api/v1/reviews/1/approve")
                        .with(csrf())
                        .with(auth("admin"))
                        .requestAttr("userId", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void stubReviewResponse(ReviewTask task) {
        given(governanceQueryRepository.getReviewTaskResponse(task)).willReturn(toReviewResponse(task));
    }

    private void stubNamespaceRoles(String userId, List<com.iflytek.skillhub.domain.namespace.NamespaceMember> members) {
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

    private ReviewTask createReviewTask(Long id, Long namespaceId, String submittedBy) {
        ReviewTask task = new ReviewTask(100L, namespaceId, submittedBy);
        setField(task, "id", id);
        setField(task, "status", ReviewTaskStatus.PENDING);
        return task;
    }

    private ReviewTaskResponse toReviewResponse(ReviewTask task) {
        return new ReviewTaskResponse(
                task.getId(),
                task.getSkillVersionId(),
                "team-a",
                "skill-a",
                "1.0.0",
                task.getStatus().name(),
                task.getSubmittedBy(),
                "Submitter",
                task.getReviewedBy(),
                null,
                task.getReviewComment(),
                task.getSubmittedAt(),
                task.getReviewedAt()
        );
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
