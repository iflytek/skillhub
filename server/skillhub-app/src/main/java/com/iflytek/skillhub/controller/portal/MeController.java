package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.exception.UnauthorizedException;
import com.iflytek.skillhub.service.MySkillAppService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/me", "/api/web/me"})
public class MeController extends BaseApiController {

    private final MySkillAppService mySkillAppService;

    public MeController(MySkillAppService mySkillAppService, ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.mySkillAppService = mySkillAppService;
    }

    @GetMapping("/skills")
    public ApiResponse<List<SkillSummaryResponse>> listMySkills(
            @AuthenticationPrincipal PlatformPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }

        return ok("response.success.read", mySkillAppService.listMySkills(principal.userId()));
    }

    @GetMapping("/stars")
    public ApiResponse<List<SkillSummaryResponse>> listMyStars(
            @AuthenticationPrincipal PlatformPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }

        return ok("response.success.read", mySkillAppService.listMyStars(principal.userId()));
    }
}
