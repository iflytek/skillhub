package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.domain.social.SkillStarService;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<Void> starSkill(
            @PathVariable Long skillId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        skillStarService.star(skillId, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{skillId}/star")
    public ResponseEntity<Void> unstarSkill(
            @PathVariable Long skillId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        skillStarService.unstar(skillId, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{skillId}/star")
    public ApiResponse<Boolean> checkStarred(
            @PathVariable Long skillId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        boolean starred = skillStarService.isStarred(skillId, principal.userId());
        return ok("response.success.skill.star.check", starred);
    }
}
