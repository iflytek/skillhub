package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.GithubRepositoryCatalogResponse.GithubCatalogRepositoryDetail;
import com.iflytek.skillhub.dto.GithubRepositoryCatalogResponse.GithubCatalogRepositoryPage;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.repository.GithubRepositoryCatalogQueryRepository;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/web/github-repositories")
public class GithubRepositoryCatalogController extends BaseApiController {
    private final GithubRepositoryCatalogQueryRepository catalog;

    public GithubRepositoryCatalogController(GithubRepositoryCatalogQueryRepository catalog, ApiResponseFactory factory) {
        super(factory);
        this.catalog = catalog;
    }

    @GetMapping
    @Operation(operationId = "listGithubCatalogRepositories")
    @RateLimit(category = "search", authenticated = 60, anonymous = 20)
    public ApiResponse<GithubCatalogRepositoryPage> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles
    ) {
        return ok("response.success.read", catalog.list(memberIds(roles), Math.max(0, page), boundedSize(size)));
    }

    @GetMapping("/{repositoryId}")
    @Operation(operationId = "getGithubCatalogRepository")
    @RateLimit(category = "search", authenticated = 60, anonymous = 20)
    public ApiResponse<GithubCatalogRepositoryDetail> detail(
            @PathVariable long repositoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles
    ) {
        return ok("response.success.read",
                catalog.detail(repositoryId, memberIds(roles), Math.max(0, page), boundedSize(size))
                        .orElseThrow(() -> new DomainNotFoundException("error.githubSync.source.notFound")));
    }

    private static Set<Long> memberIds(Map<Long, NamespaceRole> roles) {
        return roles == null ? Set.of() : roles.keySet();
    }

    private static int boundedSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }
}
