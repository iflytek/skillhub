package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SkillRatingRequest;
import com.iflytek.skillhub.dto.SkillRatingStatusResponse;
import com.iflytek.skillhub.domain.social.SkillRatingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

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
            @RequestAttribute("userId") String userId) {
        if (userId == null) {
            throw new DomainForbiddenException("error.auth.required");
        }
        skillRatingService.rate(skillId, userId, request.score());
        return ok("response.success.updated", null);
    }

    @GetMapping("/{skillId}/rating")
    public ApiResponse<SkillRatingStatusResponse> getUserRating(
            @PathVariable Long skillId,
            @RequestAttribute(value = "userId", required = false) String userId) {
        if (userId == null) {
            return ok("response.success.read", new SkillRatingStatusResponse((short) 0, false));
        }
        Optional<Short> rating = skillRatingService.getUserRating(skillId, userId);
        return ok(
                "response.success.read",
                new SkillRatingStatusResponse(
                        rating.orElse((short) 0),
                        rating.isPresent()
                )
        );
    }
}
