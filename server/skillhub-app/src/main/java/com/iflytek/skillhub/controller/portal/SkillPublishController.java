package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PublishResponse;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.SkillPublishAppService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Upload endpoints for skill packages.
 *
 * <p>The controller is responsible for request binding while the app service
 * orchestrates archive extraction and publication.
 */
@RestController
@RequestMapping({"/api/v1/skills", "/api/web/skills"})
public class SkillPublishController extends BaseApiController {

    private final SkillPublishAppService skillPublishAppService;

    public SkillPublishController(SkillPublishAppService skillPublishAppService,
                                  ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.skillPublishAppService = skillPublishAppService;
    }

    /**
     * Publishes an uploaded package into the target namespace after archive
     * extraction and visibility parsing.
     */
    @PostMapping("/{namespace}/publish")
    @RateLimit(category = "publish", authenticated = 10, anonymous = 0)
    public ApiResponse<PublishResponse> publish(
            @PathVariable String namespace,
            @RequestParam("file") MultipartFile file,
            @RequestParam("visibility") String visibility,
            @RequestParam(value = "confirmWarnings", defaultValue = "false") boolean confirmWarnings,
            @AuthenticationPrincipal PlatformPrincipal principal) throws IOException {

        SkillVisibility skillVisibility = SkillVisibility.valueOf(visibility.toUpperCase());
        PublishResponse response = skillPublishAppService.publish(
                namespace,
                file,
                skillVisibility,
                principal.userId(),
                principal.platformRoles(),
                confirmWarnings
        );
        return ok("response.success.published", response);
    }
}
