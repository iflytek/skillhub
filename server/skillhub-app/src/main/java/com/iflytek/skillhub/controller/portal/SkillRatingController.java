package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.domain.social.SkillRatingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillRatingController extends BaseApiController {

    private final SkillRatingService skillRatingService;

    public SkillRatingController(ApiResponseFactory responseFactory,
                                 SkillRatingService skillRatingService) {
        super(responseFactory);
        this.skillRatingService = skillRatingService;
    }

    @PutMapping("/{skillId}/rating")
    public ResponseEntity<Void> rateSkill(
            @PathVariable Long skillId,
            @RequestBody Map<String, Short> request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        Short score = request.get("score");
        skillRatingService.rate(skillId, principal.userId(), score);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{skillId}/rating")
    public ApiResponse<Map<String, Object>> getUserRating(
            @PathVariable Long skillId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        Optional<Short> rating = skillRatingService.getUserRating(skillId, principal.userId());
        Map<String, Object> data = Map.of(
                "score", rating.orElse((short) 0),
                "rated", rating.isPresent()
        );
        return ok("response.success.skill.rating.get", data);
    }
}
