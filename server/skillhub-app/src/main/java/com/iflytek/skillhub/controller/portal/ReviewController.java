package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.ReviewActionRequest;
import com.iflytek.skillhub.dto.ReviewSkillDetailResponse;
import com.iflytek.skillhub.dto.ReviewTaskRequest;
import com.iflytek.skillhub.dto.ReviewTaskResponse;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.GovernanceWorkflowAppService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for submitting, browsing, approving, rejecting, and withdrawing
 * review tasks.
 */
@RestController
@RequestMapping({"/api/v1/reviews", "/api/web/reviews"})
public class ReviewController extends BaseApiController {

    private final GovernanceWorkflowAppService governanceWorkflowAppService;

    public ReviewController(GovernanceWorkflowAppService governanceWorkflowAppService,
                            ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.governanceWorkflowAppService = governanceWorkflowAppService;
    }

    @PostMapping
    public ApiResponse<ReviewTaskResponse> submitReview(@RequestBody ReviewTaskRequest request,
                                                        @RequestAttribute("userId") String userId,
                                                        @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
                                                        HttpServletRequest httpRequest) {
        return ok(
                "response.success.created",
                governanceWorkflowAppService.submitReview(
                        request.skillVersionId(),
                        userId,
                        userNsRoles,
                        AuditRequestContext.from(httpRequest))
        );
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<ReviewTaskResponse> approveReview(@PathVariable Long id,
                                                         @RequestBody(required = false) ReviewActionRequest request,
                                                         @RequestAttribute("userId") String userId,
                                                         @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
                                                         HttpServletRequest httpRequest) {
        String comment = request != null ? request.comment() : null;
        return ok(
                "response.success.updated",
                governanceWorkflowAppService.approveReview(
                        id,
                        comment,
                        userId,
                        userNsRoles,
                        AuditRequestContext.from(httpRequest))
        );
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<ReviewTaskResponse> rejectReview(@PathVariable Long id,
                                                        @RequestBody(required = false) ReviewActionRequest request,
                                                        @RequestAttribute("userId") String userId,
                                                        @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
                                                        HttpServletRequest httpRequest) {
        String comment = request != null ? request.comment() : null;
        return ok(
                "response.success.updated",
                governanceWorkflowAppService.rejectReview(
                        id,
                        comment,
                        userId,
                        userNsRoles,
                        AuditRequestContext.from(httpRequest))
        );
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<Void> withdrawReview(@PathVariable Long id,
                                            @RequestAttribute("userId") String userId,
                                            HttpServletRequest httpRequest) {
        governanceWorkflowAppService.withdrawReviewTask(id, userId, AuditRequestContext.from(httpRequest));
        return ok("response.success.updated", null);
    }

    @GetMapping
    public ApiResponse<PageResponse<ReviewTaskResponse>> listReviews(@RequestParam String status,
                                                                     @RequestParam(required = false) Long namespaceId,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size,
                                                                     @RequestAttribute("userId") String userId,
                                                                     @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return ok(
                "response.success.read",
                governanceWorkflowAppService.listReviews(status, namespaceId, page, size, userId, userNsRoles)
        );
    }

    @GetMapping("/pending")
    public ApiResponse<PageResponse<ReviewTaskResponse>> listPendingReviews(@RequestParam Long namespaceId,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "20") int size,
                                                                            @RequestAttribute("userId") String userId,
                                                                            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return ok(
                "response.success.read",
                governanceWorkflowAppService.listPendingReviews(namespaceId, page, size, userId, userNsRoles)
        );
    }

    @GetMapping("/my-submissions")
    public ApiResponse<PageResponse<ReviewTaskResponse>> listMySubmissions(@RequestParam(defaultValue = "0") int page,
                                                                           @RequestParam(defaultValue = "20") int size,
                                                                           @RequestAttribute("userId") String userId) {
        return ok("response.success.read", governanceWorkflowAppService.listMyReviewSubmissions(page, size, userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReviewTaskResponse> getReviewDetail(@PathVariable Long id,
                                                           @RequestAttribute("userId") String userId,
                                                           @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return ok("response.success.read", governanceWorkflowAppService.getReviewDetail(id, userId, userNsRoles));
    }

    @GetMapping("/{id}/skill-detail")
    public ApiResponse<ReviewSkillDetailResponse> getReviewSkillDetail(@PathVariable Long id,
                                                                       @RequestAttribute("userId") String userId,
                                                                       @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return ok(
                "response.success.read",
                governanceWorkflowAppService.getReviewSkillDetail(id, userId, userNsRoles)
        );
    }

    /**
     * Reads a single file's content from the review-bound skill version.
     * Supports both text preview and single-file download use cases.
     * Path traversal is blocked by validating that path contains no ".." or absolute segments.
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<InputStreamResource> getReviewFile(
            @PathVariable Long id,
            @RequestParam("path") String path,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        // Validate path to prevent directory traversal
        if (path == null || path.isBlank() || path.contains("..") || path.startsWith("/")) {
            return ResponseEntity.badRequest().build();
        }

        java.io.InputStream content = governanceWorkflowAppService.getReviewFileContent(
                id, path, userId, userNsRoles
        );

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(content));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> downloadReviewVersion(@PathVariable Long id,
                                                                     HttpServletRequest request,
                                                                     @RequestAttribute("userId") String userId,
                                                                     @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        SkillDownloadService.DownloadResult result = governanceWorkflowAppService.downloadReviewPackage(
                id,
                userId,
                userNsRoles
        );
        return buildDownloadResponse(request, result);
    }

    private ResponseEntity<InputStreamResource> buildDownloadResponse(HttpServletRequest request, SkillDownloadService.DownloadResult result) {
        if (shouldRedirectToPresignedUrl(request, result.presignedUrl())) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, result.presignedUrl())
                    .build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
                .contentType(MediaType.parseMediaType(result.contentType()))
                .contentLength(result.contentLength())
                .body(new InputStreamResource(result.openContent()));
    }

    private boolean shouldRedirectToPresignedUrl(HttpServletRequest request, String presignedUrl) {
        if (presignedUrl == null || presignedUrl.isBlank()) {
            return false;
        }
        if (!isSecureRequest(request)) {
            return true;
        }
        try {
            return "https".equalsIgnoreCase(java.net.URI.create(presignedUrl).getScheme());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            return "https".equalsIgnoreCase(forwardedProto);
        }
        return request.isSecure();
    }
}
