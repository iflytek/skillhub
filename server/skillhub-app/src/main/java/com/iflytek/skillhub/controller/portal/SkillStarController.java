package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.domain.social.SkillStarService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/skills", "/api/web/skills"})
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
            @RequestAttribute("userId") String userId) {
        if (userId == null) {
            throw new DomainForbiddenException("error.auth.required");
        }
        skillStarService.star(skillId, userId);
        return ok("response.success.updated", null);
    }

    @DeleteMapping("/{skillId}/star")
    public ApiResponse<Void> unstarSkill(
            @PathVariable Long skillId,
            @RequestAttribute("userId") String userId) {
        if (userId == null) {
            throw new DomainForbiddenException("error.auth.required");
        }
        skillStarService.unstar(skillId, userId);
        return ok("response.success.updated", null);
    }

    @GetMapping("/{skillId}/star")
    public ApiResponse<Boolean> checkStarred(
            @PathVariable Long skillId,
            @RequestAttribute(value = "userId", required = false) String userId) {
        if (userId == null) {
            return ok("response.success.read", false);
        }
        boolean starred = skillStarService.isStarred(skillId, userId);
        return ok("response.success.read", starred);
    }
}
