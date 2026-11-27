package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MySkillVersionController extends BaseApiController {

    private final SkillQueryService skillQueryService;

    public MySkillVersionController(SkillQueryService skillQueryService, ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.skillQueryService = skillQueryService;
    }

    @GetMapping("/api/v1/me/skills/versions/{versionId}")
    @Operation(operationId = "getMySkillVersionStatus")
    public ApiResponse<VersionStatus> getMySkillVersionStatus(
            @PathVariable Long versionId, @AuthenticationPrincipal PlatformPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }
        var version = skillQueryService.getOwnedVersion(versionId, principal.userId());
        return ok("response.success.read", new VersionStatus(version.getId(), version.getVersion(), version.getStatus()));
    }

    public record VersionStatus(Long id, String version, SkillVersionStatus status) {}
}
