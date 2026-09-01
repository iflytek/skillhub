package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SkillReviewModerationRequest;
import com.iflytek.skillhub.dto.SkillReviewResponse;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SkillReviewAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/skill-reviews")
@PreAuthorize("hasAnyRole('SKILL_ADMIN', 'SUPER_ADMIN')")
public class AdminSkillReviewController extends BaseApiController {

    private final SkillReviewAppService reviewAppService;

    public AdminSkillReviewController(ApiResponseFactory responseFactory,
                                      SkillReviewAppService reviewAppService) {
        super(responseFactory);
        this.reviewAppService = reviewAppService;
    }

    @PostMapping("/{reviewId}/hide")
    public ApiResponse<SkillReviewResponse> hide(
            @PathVariable Long reviewId,
            @Valid @RequestBody(required = false) SkillReviewModerationRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", reviewAppService.hide(
                reviewId,
                principal.userId(),
                request != null ? request.reason() : null,
                AuditRequestContext.from(httpRequest)
        ));
    }

    @PostMapping("/{reviewId}/restore")
    public ApiResponse<SkillReviewResponse> restore(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", reviewAppService.restore(
                reviewId,
                principal.userId(),
                AuditRequestContext.from(httpRequest)
        ));
    }
}
