package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillReviewMeResponse;
import com.iflytek.skillhub.dto.SkillReviewRequest;
import com.iflytek.skillhub.dto.SkillReviewResponse;
import com.iflytek.skillhub.service.SkillReviewAppService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/skills", "/api/web/skills"})
public class SkillReviewController extends BaseApiController {

    private final SkillReviewAppService reviewAppService;

    public SkillReviewController(ApiResponseFactory responseFactory,
                                 SkillReviewAppService reviewAppService) {
        super(responseFactory);
        this.reviewAppService = reviewAppService;
    }

    @GetMapping("/{skillId}/reviews")
    public ApiResponse<PageResponse<SkillReviewResponse>> list(
            @PathVariable Long skillId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles) {
        return ok("response.success.read", reviewAppService.list(
                skillId,
                principal != null ? principal.userId() : null,
                namespaceRoles,
                roles(principal),
                page,
                size
        ));
    }

    @GetMapping("/{skillId}/reviews/me")
    public ApiResponse<SkillReviewMeResponse> getMine(
            @PathVariable Long skillId,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles) {
        return ok("response.success.read", reviewAppService.getMine(
                skillId, principal.userId(), namespaceRoles, roles(principal)));
    }

    @PutMapping("/{skillId}/reviews/me")
    public ApiResponse<SkillReviewMeResponse> upsert(
            @PathVariable Long skillId,
            @Valid @RequestBody SkillReviewRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles) {
        return ok("response.success.updated", reviewAppService.upsert(
                skillId,
                principal.userId(),
                request.score(),
                request.reviewText(),
                namespaceRoles,
                roles(principal)
        ));
    }

    @DeleteMapping("/{skillId}/reviews/me")
    public ApiResponse<SkillReviewMeResponse> clear(
            @PathVariable Long skillId,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles) {
        return ok("response.success.updated", reviewAppService.clear(
                skillId, principal.userId(), namespaceRoles, roles(principal)));
    }

    private Set<String> roles(PlatformPrincipal principal) {
        return principal != null && principal.platformRoles() != null
                ? principal.platformRoles()
                : Set.of();
    }
}
