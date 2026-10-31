package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.service.SkillPackagePreviewService;
import com.iflytek.skillhub.dto.SkillPackagePreviewResponse;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PublishResponse;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import com.iflytek.skillhub.service.SkillPublishAppService;
import com.iflytek.skillhub.service.AuditRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import com.iflytek.skillhub.ratelimit.RateLimit;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Upload endpoints for skill packages.
 *
 * <p>The controller is responsible for archive extraction and request shaping,
 * while the domain service owns all publication validation and state changes.
 */
@RestController
@RequestMapping({"/api/v1/skills", "/api/web/skills"})
public class SkillPublishController extends BaseApiController {

    private final SkillPublishAppService skillPublishService;
    private final SkillPackageArchiveExtractor skillPackageArchiveExtractor;
    private final SkillHubMetrics skillHubMetrics;
    private final SkillPackagePreviewService packagePreview;

    @PostMapping(value = "/package-preview", consumes = "multipart/form-data")
    @RateLimit(category = "publish", authenticated = 10, anonymous = 0)
    public ApiResponse<SkillPackagePreviewResponse> previewPackage(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal PlatformPrincipal principal) throws IOException {
        if (principal == null) {
            throw new DomainForbiddenException("error.auth.required");
        }
        try {
            return ok("response.success.read", packagePreview.preview(
                    skillPackageArchiveExtractor.extractWithWarnings(file).entries()));
        } catch (IllegalArgumentException exception) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid", exception.getMessage());
        }
    }

    public SkillPublishController(SkillPublishAppService skillPublishService,
                                  SkillPackageArchiveExtractor skillPackageArchiveExtractor,
                                  ApiResponseFactory responseFactory,
                                  SkillHubMetrics skillHubMetrics,
                                  SkillPackagePreviewService packagePreview) {
        super(responseFactory);
        this.skillPublishService = skillPublishService;
        this.skillPackageArchiveExtractor = skillPackageArchiveExtractor;
        this.skillHubMetrics = skillHubMetrics;
        this.packagePreview = packagePreview;
    }

    /**
     * Publishes an uploaded package into the target namespace after archive
     * extraction and visibility parsing.
     */
    @PostMapping("/{namespace}/publish")
    @RateLimit(category = "publish", authenticated = 10, anonymous = 0)
    public ApiResponse<PublishResponse> publish(
            @PathVariable String namespace,
            @RequestParam("file") MultipartFile file,
            @RequestParam("visibility") String visibility,
            @RequestParam(value = "confirmWarnings", defaultValue = "false") boolean confirmWarnings,
            @RequestParam(value = "avatarAction", defaultValue = "KEEP") SkillPublishAppService.AvatarAction avatarAction,
            @RequestParam(value = "avatarUploadId", required = false) String avatarUploadId,
            @RequestParam(value = "expectedSlug", required = false) String expectedSlug,
            @RequestParam(value = "displayName", required = false) String displayName,
            @RequestParam(value = "shortDescription", required = false) String shortDescription,
            @RequestParam(value = "overview", required = false) String overview,
            @RequestParam(value = "resetPresentation", defaultValue = "false") boolean resetPresentation,
            @AuthenticationPrincipal PlatformPrincipal principal, HttpServletRequest request) throws IOException {

        SkillVisibility skillVisibility = SkillVisibility.valueOf(visibility.toUpperCase());

        List<PackageEntry> entries;
        List<String> extractionWarnings;
        try {
            SkillPackageArchiveExtractor.ExtractionResult extractionResult =
                    skillPackageArchiveExtractor.extractWithWarnings(file);
            entries = extractionResult.entries();
            extractionWarnings = extractionResult.warnings();
        } catch (IllegalArgumentException e) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid", e.getMessage());
        }

        if (!confirmWarnings && !extractionWarnings.isEmpty()) {
            throw new DomainBadRequestException(
                    "error.skill.publish.precheck.confirmRequired",
                    String.join("\n", extractionWarnings));
        }

        SkillPublishService.PublishResult publishResult = skillPublishService.publish(
                namespace,
                entries,
                principal.userId(),
                skillVisibility,
                principal.platformRoles(),
                confirmWarnings, avatarAction, avatarUploadId, expectedSlug, displayName,
                shortDescription, overview, resetPresentation, AuditRequestContext.from(request)
        );

        PublishResponse response = new PublishResponse(
                publishResult.skillId(),
                namespace,
                publishResult.slug(),
                publishResult.version().getVersion(),
                publishResult.version().getStatus().name(),
                publishResult.version().getFileCount(),
                publishResult.version().getTotalSize()
        );
        skillHubMetrics.incrementSkillPublish(namespace, publishResult.version().getStatus().name());

        return ok("response.success.published", response);
    }
}
