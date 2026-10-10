package com.iflytek.skillhub.controller.cli;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.cli.*;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.ratelimit.RateLimit;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.cli.CliSkillAppService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cli/v1/skills")
public class CliSkillController extends BaseApiController {
    private final CliSkillAppService cliSkillAppService;
    private final SkillPackageArchiveExtractor archiveExtractor;

    public CliSkillController(CliSkillAppService cliSkillAppService,
                              SkillPackageArchiveExtractor archiveExtractor,
                              ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.cliSkillAppService = cliSkillAppService;
        this.archiveExtractor = archiveExtractor;
    }

    @GetMapping("/search")
    @RateLimit(category = "search", authenticated = 60, anonymous = 20)
    public ApiResponse<CliSearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") int limit,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        var result = cliSkillAppService.search(q, limit, userId, userNsRoles);
        return ok("response.success.read", new CliSearchResponse(
                result.items().stream()
                        .map(item -> new CliSearchItemResponse(item.namespace(), item.slug(), item.latestVersion(), item.summary()))
                        .toList(),
                result.total(),
                result.limit()
        ));
    }

    @GetMapping("/{namespace}/{slug}/resolve")
    public ApiResponse<CliResolveResponse> resolve(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestParam(required = false) String version,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return ok("response.success.read", cliSkillAppService.resolve(namespace, slug, version, userId, userNsRoles));
    }

    @GetMapping("/{namespace}/{slug}/download")
    @RateLimit(category = "download", authenticated = 120, anonymous = 30)
    public ResponseEntity<InputStreamResource> downloadLatest(
            @PathVariable String namespace,
            @PathVariable String slug,
            HttpServletRequest request) {
        return cliSkillAppService.downloadLatest(namespace, slug, request);
    }

    @GetMapping("/{namespace}/{slug}/versions/{version}/download")
    @RateLimit(category = "download", authenticated = 120, anonymous = 30)
    public ResponseEntity<InputStreamResource> downloadVersion(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version,
            HttpServletRequest request) {
        return cliSkillAppService.downloadVersion(namespace, slug, version, request);
    }

    @DeleteMapping("/{namespace}/{slug}")
    public ApiResponse<CliDeleteResponse> deleteRemote(
            @PathVariable String namespace,
            @PathVariable String slug,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest request) {
        return ok("response.success.deleted", cliSkillAppService.deleteRemote(
                namespace, slug, principal.userId(), AuditRequestContext.from(request)));
    }

    @PostMapping(value = "/{namespace}/publish/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit(category = "publish", authenticated = 10, anonymous = 0)
    public ApiResponse<CliDryRunResponse> validatePublish(
            @PathVariable String namespace,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "visibility", required = false) String visibility,
            @AuthenticationPrincipal PlatformPrincipal principal) throws IOException {
        List<PackageEntry> entries;
        try {
            entries = archiveExtractor.extract(file);
        } catch (IllegalArgumentException e) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid", e.getMessage());
        }
        SkillVisibility resolvedVisibility;
        try {
            resolvedVisibility = SkillVisibility.valueOf((visibility != null ? visibility : "PUBLIC").toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DomainBadRequestException("error.skill.publish.visibility.invalid", visibility);
        }
        var result = cliSkillAppService.validatePublish(
                namespace, entries, principal.userId(), resolvedVisibility, principal.platformRoles());
        return ok("response.success.read", result);
    }

    @PostMapping(value = "/{namespace}/publish", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit(category = "publish", authenticated = 10, anonymous = 0)
    public ApiResponse<CliPublishResponse> publish(
            @PathVariable String namespace,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "visibility", required = false) String visibility,
            @AuthenticationPrincipal PlatformPrincipal principal) throws IOException {
        List<PackageEntry> entries;
        try {
            entries = archiveExtractor.extract(file);
        } catch (IllegalArgumentException e) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid", e.getMessage());
        }
        var result = cliSkillAppService.publish(
                namespace, entries, principal.userId(),
                SkillVisibility.valueOf((visibility != null ? visibility : "PUBLIC").toUpperCase()),
                principal.platformRoles());
        return ok("response.success.published", result);
    }
}
