package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SkillRatingRequest;
import com.iflytek.skillhub.dto.SkillRatingStatusResponse;
import com.iflytek.skillhub.domain.social.SkillRatingService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

/**
 * Endpoints for reading and mutating the current user's rating on a skill.
 */
@RestController
@RequestMapping({"/api/v1/skills", "/api/web/skills"})
public class SkillRatingController extends BaseApiController {

    private final SkillRatingService skillRatingService;

    public SkillRatingController(ApiResponseFactory responseFactory,
                                 SkillRatingService skillRatingService) {
        super(responseFactory);
        this.skillRatingService = skillRatingService;
    }

    @PutMapping("/{skillId}/rating")
    public ApiResponse<Void> rateSkill(
            @PathVariable Long skillId,
            @Valid @RequestBody SkillRatingRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        skillRatingService.rate(skillId, principal.userId(), request.score());
        return ok("response.success.updated", null);
    }

    @GetMapping("/{skillId}/rating")
    public ApiResponse<SkillRatingStatusResponse> getUserRating(
            @PathVariable Long skillId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        if (principal == null) {
            return ok("response.success.read", new SkillRatingStatusResponse((short) 0, false));
        }
        Optional<Short> rating = skillRatingService.getUserRating(skillId, principal.userId());
        return ok(
                "response.success.read",
                new SkillRatingStatusResponse(
                        rating.orElse((short) 0),
                        rating.isPresent()
                )
        );
    }
}
