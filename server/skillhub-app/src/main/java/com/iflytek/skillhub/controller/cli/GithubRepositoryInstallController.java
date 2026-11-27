package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.token.ApiTokenScopes;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.GithubRepositoryInstallPlanResponse;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.repository.GithubRepositoryCatalogQueryRepository;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GithubRepositoryInstallController extends BaseApiController {
    private final GithubRepositoryCatalogQueryRepository catalog;

    public GithubRepositoryInstallController(GithubRepositoryCatalogQueryRepository catalog, ApiResponseFactory factory) {
        super(factory);
        this.catalog = catalog;
    }

    @GetMapping({"/api/cli/v1/github-repositories/{repositoryId}/install-plan",
            "/api/web/github-repositories/{repositoryId}/install-plan"})
    @Operation(operationId = "getGithubRepositoryInstallPlan")
    @RateLimit(category = "search", authenticated = 30, anonymous = 10)
    public ApiResponse<GithubRepositoryInstallPlanResponse> installPlan(
            @PathVariable long repositoryId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        Set<String> capabilities = principal == null ? null : principal.tokenCapabilities();
        Set<Long> memberIds = roles == null || capabilities != null && !capabilities.contains(ApiTokenScopes.READ)
                ? Set.of() : roles.keySet();
        return ok("response.success.read", catalog.installPlan(repositoryId, memberIds)
                .orElseThrow(() -> new DomainNotFoundException("error.githubSync.source.notFound")));
    }
}
