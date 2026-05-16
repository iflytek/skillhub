package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.bundle.SkillBundleReviewActionRequest;
import com.iflytek.skillhub.service.bundle.SkillBundleAppService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints for skill bundle review queue.
 */
@RestController
@RequestMapping("/api/v1/skill-bundle-reviews")
@PreAuthorize("hasAnyRole('SKILL_ADMIN','SUPER_ADMIN','NAMESPACE_ADMIN','NAMESPACE_OWNER')")
public class SkillBundleReviewController extends BaseApiController {

    private final SkillBundleAppService appService;

    public SkillBundleReviewController(SkillBundleAppService appService, ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.appService = appService;
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<Long> approve(@PathVariable Long id,
                                     @RequestBody(required = false) SkillBundleReviewActionRequest body,
                                     @RequestAttribute("userId") String userId) {
        String comment = body == null ? null : body.comment();
        return ok("response.success.updated", appService.approve(id, comment, userId).getId());
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Long> reject(@PathVariable Long id,
                                    @RequestBody(required = false) SkillBundleReviewActionRequest body,
                                    @RequestAttribute("userId") String userId) {
        String comment = body == null ? null : body.comment();
        return ok("response.success.updated", appService.reject(id, comment, userId).getId());
    }
}
