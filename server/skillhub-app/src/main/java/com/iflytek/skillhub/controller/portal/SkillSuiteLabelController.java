package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.dto.SkillLabelDto;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SkillSuiteLabelAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Transport-only endpoints for direct Suite-to-Label associations. */
@RestController
@Tag(name = "Skill Suite Labels")
@RequestMapping({
        "/api/v1/suites/{namespace}/{slug}/labels",
        "/api/web/suites/{namespace}/{slug}/labels"
})
public class SkillSuiteLabelController extends BaseApiController {

    private final SkillSuiteLabelAppService appService;

    public SkillSuiteLabelController(
            SkillSuiteLabelAppService appService,
            ApiResponseFactory responseFactory
    ) {
        super(responseFactory);
        this.appService = appService;
    }

    @GetMapping
    @Operation(operationId = "listSkillSuiteLabels", summary = "List direct labels on one visible Suite")
    public ApiResponse<List<SkillLabelDto>> listLabels(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.read", appService.listLabels(
                namespace, slug, userId, roles(namespaceRoles), platformRoles(principal)));
    }

    @PutMapping("/{labelSlug}")
    @Operation(operationId = "attachSkillSuiteLabel", summary = "Attach an existing Registry label to a Suite")
    public ApiResponse<SkillLabelDto> attachLabel(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String labelSlug,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest request
    ) {
        return ok("response.success.updated", appService.attachLabel(
                namespace, slug, labelSlug, userId, roles(namespaceRoles),
                platformRoles(principal), AuditRequestContext.from(request)));
    }

    @DeleteMapping("/{labelSlug}")
    @Operation(operationId = "detachSkillSuiteLabel", summary = "Detach a Registry label from a Suite")
    public ApiResponse<MessageResponse> detachLabel(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String labelSlug,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest request
    ) {
        return ok("response.success.deleted", appService.detachLabel(
                namespace, slug, labelSlug, userId, roles(namespaceRoles),
                platformRoles(principal), AuditRequestContext.from(request)));
    }

    private Map<Long, NamespaceRole> roles(Map<Long, NamespaceRole> roles) {
        return roles == null ? Map.of() : roles;
    }

    private Set<String> platformRoles(PlatformPrincipal principal) {
        return principal == null || principal.platformRoles() == null
                ? Set.of()
                : principal.platformRoles();
    }
}
