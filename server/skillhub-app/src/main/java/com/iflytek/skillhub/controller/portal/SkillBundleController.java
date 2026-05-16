package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.bundle.BuildSkillBundleDraftRequest;
import com.iflytek.skillhub.dto.bundle.SkillBundleDetailResponse;
import com.iflytek.skillhub.dto.bundle.SkillBundleVersionResponse;
import com.iflytek.skillhub.service.bundle.SkillBundleAppService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal endpoints for the skill bundle module: build draft, query detail, submit
 * for review, and download accounting.
 */
@RestController
@RequestMapping("/api/v1/skill-bundles")
public class SkillBundleController extends BaseApiController {

    private final SkillBundleAppService appService;

    public SkillBundleController(SkillBundleAppService appService, ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.appService = appService;
    }

    @PostMapping("/{namespace}/drafts/build")
    public ApiResponse<SkillBundleVersionResponse> buildDraft(@PathVariable String namespace,
                                                              @Valid @RequestBody BuildSkillBundleDraftRequest request,
                                                              @RequestAttribute("userId") String userId) {
        return ok("response.success.created", appService.buildDraft(namespace, request, userId));
    }

    @PostMapping("/{namespace}/{slug}/versions/{bundleVersionId}/submit-review")
    public ApiResponse<Long> submitReview(@PathVariable String namespace,
                                          @PathVariable String slug,
                                          @PathVariable Long bundleVersionId,
                                          @RequestAttribute("userId") String userId) {
        return ok("response.success.created",
                appService.submitForReview(bundleVersionId, userId).getId());
    }

    @GetMapping("/{namespace}/{slug}")
    public ApiResponse<SkillBundleDetailResponse> getDetail(@PathVariable String namespace,
                                                            @PathVariable String slug,
                                                            @RequestParam(required = false) String version) {
        return ok("response.success.read", appService.getDetail(namespace, slug, version));
    }

    @PostMapping("/{namespace}/{slug}/download")
    public ApiResponse<Void> recordDownload(@PathVariable String namespace,
                                            @PathVariable String slug) {
        appService.incrementDownload(namespace, slug);
        return ok("response.success.created", null);
    }
}
