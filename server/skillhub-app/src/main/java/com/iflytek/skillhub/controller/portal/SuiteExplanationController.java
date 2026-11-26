package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.SuiteExplanationService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.iflytek.skillhub.exception.UnauthorizedException;
import com.iflytek.skillhub.exception.BadRequestException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/web/suites")
public class SuiteExplanationController extends BaseApiController {
    private final SuiteExplanationService service;

    public SuiteExplanationController(ApiResponseFactory responses, SuiteExplanationService service) {
        super(responses);
        this.service = service;
    }

    @PostMapping("/{namespace}/{slug}/explanation")
    @Operation(operationId = "explainSkillSuite", summary = "Explain a pinned suite using readable member instructions")
    @RateLimit(category = "suite-explanation", authenticated = 10, anonymous = 1)
    public ApiResponse<SuiteExplanationService.Explanation> explain(
            @PathVariable String namespace, @PathVariable String slug, @RequestParam String version,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal, Locale locale) {
        if (userId == null) throw new UnauthorizedException("error.auth.required");
        if (version.isBlank()) throw new BadRequestException("error.suite.version.required");
        return ok("response.success.read", service.explain(namespace, slug, version, userId,
                roles == null ? Map.of() : roles, principal == null || principal.platformRoles() == null
                        ? Set.of() : principal.platformRoles(), locale));
    }
}
