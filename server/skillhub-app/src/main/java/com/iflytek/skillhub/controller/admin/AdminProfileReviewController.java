package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.user.ProfileReviewService;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.ProfileReviewMutationResponse;
import com.iflytek.skillhub.dto.ProfileReviewRejectRequest;
import com.iflytek.skillhub.dto.ProfileReviewSummaryResponse;
import com.iflytek.skillhub.service.AdminProfileReviewAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Admin REST controller for reviewing user profile change requests.
 *
 * <p>Provides list, approve, and reject endpoints. All endpoints
 * require USER_ADMIN or SUPER_ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/admin/profile-reviews")
public class AdminProfileReviewController extends BaseApiController {

    private final AdminProfileReviewAppService appService;
    private final ProfileReviewService reviewService;

    public AdminProfileReviewController(ApiResponseFactory responseFactory,
                                        AdminProfileReviewAppService appService,
                                        ProfileReviewService reviewService) {
        super(responseFactory);
        this.appService = appService;
        this.reviewService = reviewService;
    }

    /** List profile change requests filtered by status (default: PENDING). */
    @GetMapping
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<PageResponse<ProfileReviewSummaryResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        return ok("response.success", appService.list(status, page, size, sortDirection));
    }

    /** Approve a PENDING profile change request — applies changes to user account. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<ProfileReviewMutationResponse> approve(
            @PathVariable Long id,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        var result = reviewService.approve(
                id,
                principal.userId(),
                httpRequest.getHeader("X-Request-Id"),
                resolveClientIp(httpRequest),
                httpRequest.getHeader("User-Agent"));
        return ok("response.success.updated",
                new ProfileReviewMutationResponse(result.getId(), result.getStatus().name()));
    }

    /** Reject a PENDING profile change request with a mandatory comment. */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<ProfileReviewMutationResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody ProfileReviewRejectRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        var result = reviewService.reject(
                id,
                principal.userId(),
                request.comment(),
                httpRequest.getHeader("X-Request-Id"),
                resolveClientIp(httpRequest),
                httpRequest.getHeader("User-Agent"));
        return ok("response.success.updated",
                new ProfileReviewMutationResponse(result.getId(), result.getStatus().name()));
    }

    private String resolveClientIp(HttpServletRequest request) {
        var ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
