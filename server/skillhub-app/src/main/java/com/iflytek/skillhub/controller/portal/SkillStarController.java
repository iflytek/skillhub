package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.domain.social.SkillStarService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillStarController extends BaseApiController {

    private final SkillStarService skillStarService;

    public SkillStarController(ApiResponseFactory responseFactory,
                               SkillStarService skillStarService) {
        super(responseFactory);
        this.skillStarService = skillStarService;
    }

    @PutMapping("/{skillId}/star")
    public ApiResponse<Void> starSkill(
            @PathVariable Long skillId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        skillStarService.star(skillId, principal.userId());
        return ok("response.success.updated", null);
    }

    @DeleteMapping("/{skillId}/star")
    public ApiResponse<Void> unstarSkill(
            @PathVariable Long skillId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        skillStarService.unstar(skillId, principal.userId());
        return ok("response.success.updated", null);
    }

    @GetMapping("/{skillId}/star")
    public ApiResponse<Boolean> checkStarred(
            @PathVariable Long skillId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        boolean starred = skillStarService.isStarred(skillId, principal.userId());
        return ok("response.success.read", starred);
    }
}
