package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SkillDeleteResponse;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SkillDeleteAppService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-token-friendly hard-delete endpoint reserved for super administrators.
 */
@RestController
@RequestMapping("/api/v1/skills")
public class SkillDeleteController extends BaseApiController {

    private final SkillDeleteAppService skillDeleteAppService;

    public SkillDeleteController(SkillDeleteAppService skillDeleteAppService,
                                 ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.skillDeleteAppService = skillDeleteAppService;
    }

    @DeleteMapping("/{namespace}/{slug}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<SkillDeleteResponse> deleteSkill(@PathVariable String namespace,
                                                        @PathVariable String slug,
                                                        @AuthenticationPrincipal PlatformPrincipal principal,
                                                        HttpServletRequest request) {
        SkillDeleteAppService.DeleteResult result = skillDeleteAppService.deleteSkill(
                namespace,
                slug,
                principal.userId(),
                AuditRequestContext.from(request)
        );
        return ok("response.success.deleted", new SkillDeleteResponse(
                result.skillId(),
                result.namespace(),
                result.slug(),
                result.deleted()
        ));
    }
}
