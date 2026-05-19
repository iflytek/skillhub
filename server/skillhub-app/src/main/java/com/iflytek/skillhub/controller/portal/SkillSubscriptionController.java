package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.domain.social.SkillSubscriptionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/skills", "/api/web/skills"})
public class SkillSubscriptionController extends BaseApiController {

    private final SkillSubscriptionService skillSubscriptionService;

    public SkillSubscriptionController(ApiResponseFactory responseFactory,
                                       SkillSubscriptionService skillSubscriptionService) {
        super(responseFactory);
        this.skillSubscriptionService = skillSubscriptionService;
    }

    @PutMapping("/{skillId}/subscription")
    public ApiResponse<Void> subscribeSkill(
            @PathVariable Long skillId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        skillSubscriptionService.subscribe(skillId, principal.userId());
        return ok("response.success.updated", null);
    }

    @DeleteMapping("/{skillId}/subscription")
    public ApiResponse<Void> unsubscribeSkill(
            @PathVariable Long skillId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        skillSubscriptionService.unsubscribe(skillId, principal.userId());
        return ok("response.success.updated", null);
    }

    @GetMapping("/{skillId}/subscription")
    public ApiResponse<Boolean> checkSubscribed(
            @PathVariable Long skillId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        if (principal == null) {
            return ok("response.success.read", false);
        }
        boolean subscribed = skillSubscriptionService.isSubscribed(skillId, principal.userId());
        return ok("response.success.read", subscribed);
    }
}
