package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.CliWhoamiResponse;
import com.iflytek.skillhub.dto.ResolveVersionResponse;
import com.iflytek.skillhub.dto.SkillCheckResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.iflytek.skillhub.exception.UnauthorizedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class CliController extends BaseApiController {

    private final SkillPackageValidator skillPackageValidator;
    private final SkillPackageArchiveExtractor skillPackageArchiveExtractor;
    private final SkillQueryService skillQueryService;

    public CliController(ApiResponseFactory responseFactory,
                         SkillPackageValidator skillPackageValidator,
                         SkillPackageArchiveExtractor skillPackageArchiveExtractor,
                         SkillQueryService skillQueryService) {
        super(responseFactory);
        this.skillPackageValidator = skillPackageValidator;
        this.skillPackageArchiveExtractor = skillPackageArchiveExtractor;
        this.skillQueryService = skillQueryService;
    }

    @GetMapping("/whoami")
    public ApiResponse<CliWhoamiResponse> whoami(@AuthenticationPrincipal PlatformPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }

        return ok("response.success.read", CliWhoamiResponse.from(principal));
    }

    @PostMapping("/check")
    public ApiResponse<SkillCheckResponse> check(@RequestParam("file") MultipartFile file) throws IOException {
        List<PackageEntry> entries;
        try {
            entries = skillPackageArchiveExtractor.extract(file);
        } catch (IllegalArgumentException e) {
            SkillCheckResponse response = new SkillCheckResponse(
                    false,
                    List.of(e.getMessage()),
                    0,
                    0L
            );
            return ok("response.success.validated", response);
        }
        ValidationResult result = skillPackageValidator.validate(entries);

        SkillCheckResponse response = new SkillCheckResponse(
                result.passed(),
                result.errors(),
                entries.size(),
                entries.stream().mapToLong(PackageEntry::size).sum()
        );

        return ok("response.success.validated", response);
    }

    @GetMapping("/resolve/{namespace}/{slug}")
    public ApiResponse<ResolveVersionResponse> resolve(@PathVariable String namespace,
                                                       @PathVariable String slug,
                                                       @RequestParam(required = false) String version,
                                                       @RequestParam(required = false) String tag,
                                                       @RequestParam(required = false) String hash,
                                                       @RequestAttribute(value = "userId", required = false) String userId,
                                                       @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        SkillQueryService.ResolvedVersionDTO resolved = skillQueryService.resolveVersion(
                namespace,
                slug,
                version,
                tag,
                hash,
                userId,
                userNsRoles != null ? userNsRoles : Map.of()
        );
        return ok("response.success.read", new ResolveVersionResponse(
                resolved.skillId(),
                resolved.namespace(),
                resolved.slug(),
                resolved.version(),
                resolved.versionId(),
                resolved.fingerprint(),
                resolved.matched(),
                resolved.downloadUrl()
        ));
    }
}
