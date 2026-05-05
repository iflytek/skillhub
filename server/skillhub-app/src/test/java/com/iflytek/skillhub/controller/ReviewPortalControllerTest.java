package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.controller.portal.ReviewController;
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
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.dto.ReviewTaskResponse;
import com.iflytek.skillhub.dto.ReviewSkillDetailResponse;
import com.iflytek.skillhub.dto.SkillDetailResponse;
import com.iflytek.skillhub.dto.SkillFileResponse;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillVersionResponse;
import com.iflytek.skillhub.repository.GovernanceQueryRepository;
import com.iflytek.skillhub.service.ReviewSkillDetailAppService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
                                "owner-1",
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
                                List.<com.iflytek.skillhub.dto.SkillLabelDto>of(),
                                false,
                                false,
                                false,
                                false,
                                new SkillLifecycleVersionResponse(100L, "1.2.0", "PENDING_REVIEW"),
                                new SkillLifecycleVersionResponse(99L, "1.1.0", "PUBLISHED"),
                                new SkillLifecycleVersionResponse(100L, "1.2.0", "PENDING_REVIEW"),
                                null,
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
    void listReviews_appliesRequestedTimeSortDirection() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        PageRequest pageable = PageRequest.of(
                1,
                5,
                Sort.by(
                        new Sort.Order(Sort.Direction.ASC, "reviewedAt"),
                        new Sort.Order(Sort.Direction.ASC, "id")
                )
        );
        given(reviewTaskRepository.findByStatus(ReviewTaskStatus.APPROVED, pageable))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        mockMvc.perform(get("/api/v1/reviews")
                        .param("status", "APPROVED")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sortDirection", "ASC")
                        .with(auth("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(reviewTaskRepository).findByStatus(ReviewTaskStatus.APPROVED, pageable);
    }

    @Test
    void listReviews_preservesRepositoryTotalForDefaultPageSize() throws Exception {
        assertReviewTotalPreservedForDefaultPageSize(ReviewTaskStatus.PENDING);
    }

    @Test
    void listApprovedReviews_preservesRepositoryTotalForDefaultPageSize() throws Exception {
        assertReviewTotalPreservedForDefaultPageSize(ReviewTaskStatus.APPROVED);
    }

    @Test
    void listRejectedReviews_preservesRepositoryTotalForDefaultPageSize() throws Exception {
        assertReviewTotalPreservedForDefaultPageSize(ReviewTaskStatus.REJECTED);
    }

    @Test
    void listReviews_forbidsGlobalQueueForNonPlatformReviewer() throws Exception {
        stubNamespaceRoles("namespace-admin", List.of());
        given(rbacService.getUserRoleCodes("namespace-admin")).willReturn(Set.of("NAMESPACE_ADMIN"));

        mockMvc.perform(get("/api/v1/reviews")
                        .param("status", "PENDING")
                        .with(auth("namespace-admin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(reviewTaskRepository, never()).findByStatus(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
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

    @Test
    void downloadReviewVersion_redirectsToPresignedUrl() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(reviewSkillDetailAppService.downloadReviewPackage(1L, "admin", Map.of()))
                .willReturn(new SkillDownloadService.DownloadResult(
                        () -> new java.io.ByteArrayInputStream("zip".getBytes()),
                        "skill-a-1.2.0.zip",
                        3L,
                        "application/zip",
                        "https://download.example/presigned",
                        false
                ));

        mockMvc.perform(get("/api/v1/reviews/1/download")
                        .header("X-Forwarded-Proto", "https")
                        .with(auth("admin")))
                .andExpect(status().isFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Location", "https://download.example/presigned"));
    }

    @Test
    void downloadReviewVersion_redirectsForInsecureRequest() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(reviewSkillDetailAppService.downloadReviewPackage(1L, "admin", Map.of()))
                .willReturn(new SkillDownloadService.DownloadResult(
                        () -> new java.io.ByteArrayInputStream("zip".getBytes()),
                        "skill-a-1.2.0.zip",
                        3L,
                        "application/zip",
                        "https://download.example/presigned",
                        false
                ));

        mockMvc.perform(get("/api/v1/reviews/1/download")
                        .with(auth("admin")))
                .andExpect(status().isFound());
    }

    @Test
    void downloadReviewVersion_handlesInvalidPresignedUrlForSecureRequest() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(reviewSkillDetailAppService.downloadReviewPackage(1L, "admin", Map.of()))
                .willReturn(new SkillDownloadService.DownloadResult(
                        () -> new java.io.ByteArrayInputStream("zip".getBytes()),
                        "skill-a-1.2.0.zip",
                        3L,
                        "application/zip",
                        "bad url with space",
                        false
                ));

        mockMvc.perform(get("/api/v1/reviews/1/download")
                        .header("X-Forwarded-Proto", "https")
                        .with(auth("admin")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", org.hamcrest.Matchers.containsString("skill-a-1.2.0.zip")));
    }

    @Test
    void rejectReview_returnsUnifiedEnvelope() throws Exception {
        ReviewTask task = createReviewTask(1L, 20L, "user-1");
        stubNamespaceRoles("admin", List.of());
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.rejectReview(1L, "admin", "needs work", Map.of(), Set.of("SKILL_ADMIN")))
                .willReturn(task);
        stubReviewResponse(task);

        mockMvc.perform(post("/api/v1/reviews/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"needs work\"}")
                        .with(csrf())
                        .with(auth("admin"))
                        .requestAttr("userId", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1L));
    }

    @Test
    void rejectReview_withoutBody_usesNullComment() throws Exception {
        ReviewTask task = createReviewTask(1L, 20L, "user-1");
        stubNamespaceRoles("admin", List.of());
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.rejectReview(1L, "admin", null, Map.of(), Set.of("SKILL_ADMIN")))
                .willReturn(task);
        stubReviewResponse(task);

        mockMvc.perform(post("/api/v1/reviews/1/reject")
                        .with(csrf())
                        .with(auth("admin"))
                        .requestAttr("userId", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void withdrawReview_returnsSuccess() throws Exception {
        ReviewTask task = createReviewTask(1L, 20L, "user-1");
        given(reviewTaskRepository.findById(1L)).willReturn(Optional.of(task));
        com.iflytek.skillhub.domain.skill.SkillVersion version = new com.iflytek.skillhub.domain.skill.SkillVersion(1L, "1.0.0", "user-1");
        setField(version, "id", 100L);
        given(reviewService.withdrawReview(task.getSkillVersionId(), "user-1")).willReturn(version);

        mockMvc.perform(post("/api/v1/reviews/1/withdraw")
                        .with(csrf())
                        .with(auth("user-1"))
                        .requestAttr("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void listPendingReviews_returnsPagedResults() throws Exception {
        stubNamespaceRoles("admin", List.of());
        Namespace namespace = createNamespace(20L, "team-a");
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.canReviewNamespace(any(), eq("admin"), eq(namespace.getType()), eq(Map.of()), eq(Set.of("SKILL_ADMIN"))))
                .willReturn(true);
        ReviewTask task = createReviewTask(1L, 20L, "user-1");
        given(reviewTaskRepository.findByNamespaceIdAndStatus(20L, ReviewTaskStatus.PENDING, org.springframework.data.domain.PageRequest.of(0, 20)))
                .willReturn(new PageImpl<>(List.of(task)));
        given(governanceQueryRepository.getReviewTaskResponses(List.of(task)))
                .willReturn(List.of(toReviewResponse(task)));

        mockMvc.perform(get("/api/v1/reviews/pending")
                        .param("namespaceId", "20")
                        .with(auth("admin"))
                        .requestAttr("userId", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].id").value(1L));
    }

    @Test
    void listMySubmissions_returnsPagedResults() throws Exception {
        ReviewTask task = createReviewTask(1L, 20L, "user-1");
        given(reviewTaskRepository.findBySubmittedByAndStatus("user-1", ReviewTaskStatus.PENDING, org.springframework.data.domain.PageRequest.of(0, 20)))
                .willReturn(new PageImpl<>(List.of(task)));
        given(governanceQueryRepository.getReviewTaskResponses(List.of(task)))
                .willReturn(List.of(toReviewResponse(task)));

        mockMvc.perform(get("/api/v1/reviews/my-submissions")
                        .with(auth("user-1"))
                        .requestAttr("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].id").value(1L));
    }

    @Test
    void getReviewFile_rejectsInvalidPath() throws Exception {
        stubNamespaceRoles("admin", List.of());

        mockMvc.perform(get("/api/v1/reviews/1/file")
                        .param("path", "../etc/passwd")
                        .with(auth("admin"))
                        .requestAttr("userId", "admin"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReviewFile_returnsContentForValidPath() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(reviewSkillDetailAppService.getReviewFileContent(1L, "README.md", "admin", Map.of()))
                .willReturn(new java.io.ByteArrayInputStream("content".getBytes()));

        mockMvc.perform(get("/api/v1/reviews/1/file")
                        .param("path", "README.md")
                        .with(auth("admin"))
                        .requestAttr("userId", "admin"))
                .andExpect(status().isOk());
    }

    private void stubReviewResponse(ReviewTask task) {
        given(governanceQueryRepository.getReviewTaskResponse(task)).willReturn(toReviewResponse(task));
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

    private ReviewTask createReviewTask(Long id, Long namespaceId, String submittedBy, ReviewTaskStatus status) {
        ReviewTask task = createReviewTask(id, namespaceId, submittedBy);
        setField(task, "status", status);
        return task;
    }

    private Namespace createNamespace(Long id, String slug) {
        Namespace namespace = new Namespace(slug, "Team", "owner-1");
        setField(namespace, "id", id);
        return namespace;
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

    private void assertReviewTotalPreservedForDefaultPageSize(ReviewTaskStatus status) throws Exception {
        stubNamespaceRoles("admin", List.of());
        Namespace namespace = createNamespace(20L, "team-a");
        List<ReviewTask> tasks = IntStream.rangeClosed(1, 20)
                .mapToObj(index -> createReviewTask((long) index, 20L, "submitter-" + index, status))
                .toList();
        List<ReviewTaskResponse> responses = tasks.stream()
                .map(this::toReviewResponse)
                .toList();
        PageRequest pageable = PageRequest.of(
                0,
                20,
                Sort.by(
                        new Sort.Order(Sort.Direction.DESC, status == ReviewTaskStatus.PENDING ? "submittedAt" : "reviewedAt"),
                        new Sort.Order(Sort.Direction.DESC, "id")
                )
        );

        given(reviewTaskRepository.findByStatus(status, pageable))
                .willReturn(new PageImpl<>(tasks, pageable, 42));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        tasks.forEach(task -> given(reviewService.canViewReview(
                task,
                "admin",
                namespace.getType(),
                Map.of(),
                Set.of("SKILL_ADMIN"))).willReturn(true));
        given(governanceQueryRepository.getReviewTaskResponses(tasks)).willReturn(responses);

        mockMvc.perform(get("/api/v1/reviews")
                        .param("status", status.name())
                        .with(auth("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(42))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.items.length()").value(20));
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

    @Test
    void isSecureRequest_returnsTrueForHttpsForwardedProto() {
        ReviewController controller = new ReviewController(null, null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Proto", "https");
        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(controller, "isSecureRequest", request);
        assertTrue(result);
    }
}
