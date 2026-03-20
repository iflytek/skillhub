package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SkillDeleteResponse;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SkillDeleteAppService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/web/skills")
public class SkillLifecycleDeleteController extends BaseApiController {

    private final SkillDeleteAppService skillDeleteAppService;

    public SkillLifecycleDeleteController(SkillDeleteAppService skillDeleteAppService,
                                          ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.skillDeleteAppService = skillDeleteAppService;
    }

    @DeleteMapping("/{namespace}/{slug}")
    public ApiResponse<SkillDeleteResponse> deleteSkill(@PathVariable String namespace,
                                                        @PathVariable String slug,
                                                        @AuthenticationPrincipal PlatformPrincipal principal,
                                                        HttpServletRequest httpRequest) {
        SkillDeleteAppService.DeleteResult result = skillDeleteAppService.deleteSkillFromPortal(
                namespace,
                slug,
                principal,
                AuditRequestContext.from(httpRequest)
        );
        return ok("response.success.deleted", new SkillDeleteResponse(
                result.skillId(),
                result.namespace(),
                result.slug(),
                result.deleted()
        ));
    }
}
