package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.GithubRepositoryCatalogResponse.GithubCatalogRepositoryDetail;
import com.iflytek.skillhub.dto.GithubRepositoryCatalogResponse.GithubCatalogRepositoryPage;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.GithubRepositorySnapshotArchiveService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import com.iflytek.skillhub.repository.GithubRepositoryCatalogQueryRepository;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    private final GithubRepositorySnapshotArchiveService archives;

    public GithubRepositoryCatalogController(GithubRepositoryCatalogQueryRepository catalog,
                                             GithubRepositorySnapshotArchiveService archives, ApiResponseFactory factory) {
        super(factory);
        this.catalog = catalog;
        this.archives = archives;
    }

    @GetMapping
    @Operation(operationId = "listGithubCatalogRepositories")
    @RateLimit(category = "search", authenticated = 60, anonymous = 20)
    public ApiResponse<GithubCatalogRepositoryPage> list(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.read", catalog.list(normalizeQuery(q), memberIds(roles, principal), Math.max(0, page), boundedSize(size)));
    }

    @GetMapping("/{repositoryId}")
    @Operation(operationId = "getGithubCatalogRepository")
    @RateLimit(category = "search", authenticated = 60, anonymous = 20)
    public ApiResponse<GithubCatalogRepositoryDetail> detail(
            @PathVariable long repositoryId,
            @RequestParam(required = false) Long snapshotId,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
            @AuthenticationPrincipal PlatformPrincipal principal
    ) {
        return ok("response.success.read",
                catalog.detail(repositoryId, snapshotId, normalizeQuery(q), memberIds(roles, principal), Math.max(0, page), boundedSize(size))
                        .orElseThrow(() -> new DomainNotFoundException("error.githubSync.source.notFound")));
    }

    @GetMapping(value = "/{repositoryId}/snapshots/{snapshotId}/archive", produces = "application/zip")
    @Operation(operationId = "downloadGithubRepositorySnapshot")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Repository snapshot ZIP",
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/zip",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "binary")))
    @RateLimit(category = "download", authenticated = 10, anonymous = 3)
    public void archive(@PathVariable long repositoryId, @PathVariable long snapshotId,
                        @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> roles,
                        @AuthenticationPrincipal PlatformPrincipal principal,
                        HttpServletResponse response) throws IOException {
        archives.write(repositoryId, snapshotId, memberIds(roles, principal), archive -> {
            response.setContentType("application/zip");
            response.setContentLengthLong(Files.size(archive));
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("Content-Disposition",
                    "attachment; filename=github-" + repositoryId + "-snapshot-" + snapshotId + ".zip");
            Files.copy(archive, response.getOutputStream());
        });
    }

    private static Set<Long> memberIds(Map<Long, NamespaceRole> roles, PlatformPrincipal principal) {
        // The portal catalog has historically exposed team repositories only to sessions.
        return roles == null || principal != null && principal.tokenCapabilities() != null
                ? Set.of() : roles.keySet();
    }

    private static String normalizeQuery(String query) {
        String trimmed = query.strip();
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, Math.min(120, trimmed.codePointCount(0, trimmed.length()))));
    }

    private static int boundedSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }
}
