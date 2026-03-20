package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.ReviewPermissionChecker;
import com.iflytek.skillhub.domain.review.ReviewService;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.ReviewSkillDetailResponse;
import com.iflytek.skillhub.dto.SkillDetailResponse;
import com.iflytek.skillhub.dto.SkillFileResponse;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillVersionResponse;
import com.iflytek.skillhub.service.ReviewSkillDetailAppService;
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
class ReviewPortalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewService reviewService;

    @MockBean
    private ReviewTaskRepository reviewTaskRepository;

    @MockBean
    private SkillRepository skillRepository;

    @MockBean
    private SkillVersionRepository skillVersionRepository;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private com.iflytek.skillhub.domain.namespace.NamespaceRepository namespaceRepository;

    @MockBean
    private UserAccountRepository userAccountRepository;

    @MockBean
    private RbacService rbacService;

    @MockBean
    private ReviewPermissionChecker permissionChecker;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private ReviewSkillDetailAppService reviewSkillDetailAppService;

    @Test
    void submitReview_passesNamespaceRolesToService() throws Exception {
        ReviewTask task = createReviewTask(1L, 20L, "user-1");
        stubNamespaceRoles("user-1", List.of(new NamespaceMember(20L, "user-1", NamespaceRole.MEMBER)));
        given(rbacService.getUserRoleCodes("user-1")).willReturn(Set.of());
        given(reviewService.submitReview(100L, "user-1", Map.of(20L, NamespaceRole.MEMBER), Set.of())).willReturn(task);
        stubReviewResponse(task);

        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skillVersionId\":100}")
                        .with(csrf())
                        .with(auth("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1L));
    }

    @Test
    void listPendingReviews_forbidsNamespaceMember() throws Exception {
        Namespace namespace = createNamespace(20L, "team-a");
        stubNamespaceRoles("user-1", List.of(new NamespaceMember(20L, "user-1", NamespaceRole.MEMBER)));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("user-1")).willReturn(Set.of());
        given(permissionChecker.canManageNamespaceReviews(
                20L,
                namespace.getType(),
                Map.of(20L, NamespaceRole.MEMBER),
                Set.of())).willReturn(false);

        mockMvc.perform(get("/api/v1/reviews/pending")
                        .param("namespaceId", "20")
                        .with(auth("user-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(reviewTaskRepository, never()).findByNamespaceIdAndStatus(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getReviewDetail_allowsSubmitter() throws Exception {
        ReviewTask task = createReviewTask(1L, 20L, "user-1");
        Namespace namespace = createNamespace(20L, "team-a");
        stubNamespaceRoles("user-1", List.of());
        given(reviewTaskRepository.findById(1L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("user-1")).willReturn(Set.of());
        given(reviewService.canViewReview(task, "user-1", namespace.getType(), Map.of(), Set.of())).willReturn(true);
        stubReviewResponse(task);

        mockMvc.perform(get("/api/v1/reviews/1").with(auth("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.submittedBy").value("user-1"));
    }

    @Test
    void getReviewDetail_forbidsUnrelatedUser() throws Exception {
        ReviewTask task = createReviewTask(1L, 20L, "user-1");
        Namespace namespace = createNamespace(20L, "team-a");
        stubNamespaceRoles("user-9", List.of());
        given(reviewTaskRepository.findById(1L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("user-9")).willReturn(Set.of());
        given(reviewService.canViewReview(task, "user-9", namespace.getType(), Map.of(), Set.of())).willReturn(false);

        mockMvc.perform(get("/api/v1/reviews/1").with(auth("user-9")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void getReviewSkillDetail_returnsReviewBoundPayload() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(reviewSkillDetailAppService.getReviewSkillDetail(1L, "admin", Map.of()))
                .willReturn(new ReviewSkillDetailResponse(
                        new SkillDetailResponse(
                                30L,
                                "skill-a",
                                "Skill A",
                                "Owner",
                                "Summary",
                                "PUBLIC",
                                "ACTIVE",
                                8L,
                                2,
                                null,
                                0,
                                false,
                                "team-a",
                                false,
                                false,
                                false,
                                false,
                                new SkillLifecycleVersionResponse(100L, "1.2.0", "PENDING_REVIEW"),
                                new SkillLifecycleVersionResponse(99L, "1.1.0", "PUBLISHED"),
                                new SkillLifecycleVersionResponse(100L, "1.2.0", "PENDING_REVIEW"),
                                "REVIEW_TASK"
                        ),
                        List.of(new SkillVersionResponse(100L, "1.2.0", "PENDING_REVIEW", null, 1, 10L, null, true)),
                        List.of(new SkillFileResponse(1L, "README.md", 123L, "text/markdown", "sha")),
                        "README.md",
                        "# demo",
                        "/api/v1/reviews/1/download",
                        "1.2.0"
                ));

        mockMvc.perform(get("/api/v1/reviews/1/skill-detail").with(auth("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeVersion").value("1.2.0"))
                .andExpect(jsonPath("$.data.downloadUrl").value("/api/v1/reviews/1/download"))
                .andExpect(jsonPath("$.data.files[0].filePath").value("README.md"));
    }

    @Test
    void getReviewSkillDetail_forbidsUnauthorizedUser() throws Exception {
        stubNamespaceRoles("user-9", List.of());
        given(reviewSkillDetailAppService.getReviewSkillDetail(1L, "user-9", Map.of()))
                .willThrow(new com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException("review.no_permission"));

        mockMvc.perform(get("/api/v1/reviews/1/skill-detail").with(auth("user-9")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void downloadReviewVersion_streamsZipForAuthorizedReviewer() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(reviewSkillDetailAppService.downloadReviewPackage(1L, "admin", Map.of()))
                .willReturn(new SkillDownloadService.DownloadResult(
                        () -> new java.io.ByteArrayInputStream("zip".getBytes()),
                        "skill-a-1.2.0.zip",
                        3L,
                        "application/zip",
                        null,
                        true
                ));

        mockMvc.perform(get("/api/v1/reviews/1/download").with(auth("admin")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", org.hamcrest.Matchers.containsString("skill-a-1.2.0.zip")));
    }

    private void stubReviewResponse(ReviewTask task) {
        SkillVersion version = new SkillVersion(30L, "1.0.0", task.getSubmittedBy());
        setField(version, "id", task.getSkillVersionId());
        Skill skill = new Skill(task.getNamespaceId(), "skill-a", task.getSubmittedBy(), SkillVisibility.PUBLIC);
        setField(skill, "id", 30L);
        UserAccount submitter = new UserAccount(task.getSubmittedBy(), "Submitter", "submitter@example.com", "");

        given(skillVersionRepository.findById(task.getSkillVersionId())).willReturn(Optional.of(version));
        given(skillRepository.findById(30L)).willReturn(Optional.of(skill));
        given(namespaceRepository.findById(task.getNamespaceId())).willReturn(Optional.of(createNamespace(task.getNamespaceId(), "team-a")));
        given(userAccountRepository.findById(task.getSubmittedBy())).willReturn(Optional.of(submitter));
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

    private ReviewTask createReviewTask(Long id, Long namespaceId, String submittedBy) {
        ReviewTask task = new ReviewTask(100L, namespaceId, submittedBy);
        setField(task, "id", id);
        setField(task, "status", ReviewTaskStatus.PENDING);
        return task;
    }

    private Namespace createNamespace(Long id, String slug) {
        Namespace namespace = new Namespace(slug, "Team", "owner-1");
        setField(namespace, "id", id);
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
