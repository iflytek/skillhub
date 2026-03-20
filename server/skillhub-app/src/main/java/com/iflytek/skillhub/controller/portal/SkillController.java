package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.ResolveVersionResponse;
import com.iflytek.skillhub.dto.SkillDetailResponse;
import com.iflytek.skillhub.dto.SkillFileResponse;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillVersionDetailResponse;
import com.iflytek.skillhub.dto.SkillVersionResponse;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import com.iflytek.skillhub.ratelimit.RateLimit;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-oriented skill endpoints for detail pages, lifecycle inspection, file
 * browsing, version resolution, and download delivery.
 */
@RestController
@RequestMapping({"/api/v1/skills", "/api/web/skills"})
public class SkillController extends BaseApiController {

    private final SkillQueryService skillQueryService;
    private final SkillDownloadService skillDownloadService;
    private final SkillHubMetrics metrics;

    public SkillController(
            SkillQueryService skillQueryService,
            SkillDownloadService skillDownloadService,
            SkillHubMetrics metrics,
            ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.skillQueryService = skillQueryService;
        this.skillDownloadService = skillDownloadService;
        this.metrics = metrics;
    }

    /**
     * Returns the viewer-specific projection of a skill, including lifecycle
     * pointers and interaction permissions derived from the caller context.
     */
    @GetMapping("/{namespace}/{slug}")
    public ApiResponse<SkillDetailResponse> getSkillDetail(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        SkillQueryService.SkillDetailDTO detail = skillQueryService.getSkillDetail(
                namespace, slug, userId, userNsRoles != null ? userNsRoles : Map.of());

        SkillDetailResponse response = new SkillDetailResponse(
                detail.id(),
                detail.slug(),
                detail.displayName(),
                detail.ownerId(),
                detail.ownerDisplayName(),
                detail.summary(),
                detail.visibility(),
                detail.status(),
                detail.downloadCount(),
                detail.starCount(),
                detail.ratingAvg(),
                detail.ratingCount(),
                detail.hidden(),
                namespace,
                detail.canManageLifecycle(),
                detail.canSubmitPromotion(),
                detail.canInteract(),
                detail.canReport(),
                toLifecycleVersion(detail.headlineVersion()),
                toLifecycleVersion(detail.publishedVersion()),
                toLifecycleVersion(detail.ownerPreviewVersion()),
                detail.resolutionMode()
        );

        return ok("response.success.read", response);
    }

    /**
     * Lists versions visible to the caller rather than every persisted version
     * of the skill.
     */
    @GetMapping("/{namespace}/{slug}/versions")
    public ApiResponse<PageResponse<SkillVersionResponse>> listVersions(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        Page<SkillVersion> versions = skillQueryService.listVersions(
                namespace,
                slug,
                userId,
                userNsRoles != null ? userNsRoles : Map.of(),
                PageRequest.of(page, size));

        PageResponse<SkillVersionResponse> response = PageResponse.from(versions.map(v -> new SkillVersionResponse(
                v.getId(),
                v.getVersion(),
                v.getStatus().name(),
                v.getChangelog(),
                v.getFileCount(),
                v.getTotalSize(),
                v.getPublishedAt(),
                skillQueryService.isDownloadAvailable(v)
        )));

        return ok("response.success.read", response);
    }

    /**
     * Returns metadata for a concrete version that the current caller is
     * allowed to inspect.
     */
    @GetMapping("/{namespace}/{slug}/versions/{version}")
    public ApiResponse<SkillVersionDetailResponse> getVersionDetail(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        SkillQueryService.SkillVersionDetailDTO detail = skillQueryService.getVersionDetail(
                namespace,
                slug,
                version,
                userId,
                userNsRoles != null ? userNsRoles : Map.of()
        );

        SkillVersionDetailResponse response = new SkillVersionDetailResponse(
                detail.id(),
                detail.version(),
                detail.status(),
                detail.changelog(),
                detail.fileCount(),
                detail.totalSize(),
                detail.publishedAt(),
                detail.parsedMetadataJson(),
                detail.manifestJson()
        );
        return ok("response.success.read", response);
    }

    /**
     * Lists packaged files for a concrete version after visibility checks have
     * been applied.
     */
    @GetMapping("/{namespace}/{slug}/versions/{version}/files")
    public ApiResponse<List<SkillFileResponse>> listFiles(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        List<SkillFile> files = skillQueryService.listFiles(
                namespace,
                slug,
                version,
                userId,
                userNsRoles != null ? userNsRoles : Map.of()
        );

        List<SkillFileResponse> response = files.stream()
                .map(f -> new SkillFileResponse(
                        f.getId(),
                        f.getFilePath(),
                        f.getFileSize(),
                        f.getContentType(),
                        f.getSha256()
                ))
                .collect(Collectors.toList());

        return ok("response.success.read", response);
    }

    @GetMapping("/{namespace}/{slug}/tags/{tagName}/files")
    public ApiResponse<List<SkillFileResponse>> listFilesByTag(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String tagName,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        List<SkillFile> files = skillQueryService.listFilesByTag(
                namespace,
                slug,
                tagName,
                userId,
                userNsRoles != null ? userNsRoles : Map.of()
        );

        List<SkillFileResponse> response = files.stream()
                .map(f -> new SkillFileResponse(
                        f.getId(),
                        f.getFilePath(),
                        f.getFileSize(),
                        f.getContentType(),
                        f.getSha256()
                ))
                .collect(Collectors.toList());

        return ok("response.success.read", response);
    }

    /**
     * Streams a single packaged file directly from object storage through the
     * application API.
     */
    @GetMapping("/{namespace}/{slug}/versions/{version}/file")
    public ResponseEntity<InputStreamResource> getFileContent(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version,
            @RequestParam("path") String path,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        InputStream content = skillQueryService.getFileContent(
                namespace,
                slug,
                version,
                path,
                userId,
                userNsRoles != null ? userNsRoles : Map.of()
        );

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(content));
    }

    @GetMapping("/{namespace}/{slug}/tags/{tagName}/file")
    public ResponseEntity<InputStreamResource> getFileContentByTag(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String tagName,
            @RequestParam("path") String path,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        InputStream content = skillQueryService.getFileContentByTag(
                namespace,
                slug,
                tagName,
                path,
                userId,
                userNsRoles != null ? userNsRoles : Map.of()
        );

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(content));
    }

    /**
     * Resolves a human-facing version selector to the exact version that would
     * be downloaded by the caller.
     */
    @GetMapping("/{namespace}/{slug}/resolve")
    public ApiResponse<ResolveVersionResponse> resolveVersion(
            @PathVariable String namespace,
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

        ResolveVersionResponse response = new ResolveVersionResponse(
                resolved.skillId(),
                resolved.namespace(),
                resolved.slug(),
                resolved.version(),
                resolved.versionId(),
                resolved.fingerprint(),
                resolved.matched(),
                resolved.downloadUrl()
        );

        return ok("response.success.read", response);
    }

    @GetMapping("/{namespace}/{slug}/download")
    @RateLimit(category = "download", authenticated = 120, anonymous = 30)
    public ResponseEntity<InputStreamResource> downloadLatest(
            @PathVariable String namespace,
            @PathVariable String slug,
            HttpServletRequest request,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        SkillDownloadService.DownloadResult result = skillDownloadService.downloadLatest(
                namespace, slug, userId, userNsRoles != null ? userNsRoles : Map.of());

        return buildDownloadResponse(request, result);
    }

    @GetMapping("/{namespace}/{slug}/versions/{version}/download")
    @RateLimit(category = "download", authenticated = 120, anonymous = 30)
    public ResponseEntity<InputStreamResource> downloadVersion(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version,
            HttpServletRequest request,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        SkillDownloadService.DownloadResult result = skillDownloadService.downloadVersion(
                namespace, slug, version, userId, userNsRoles != null ? userNsRoles : Map.of());

        return buildDownloadResponse(request, result);
    }

    @GetMapping("/{namespace}/{slug}/tags/{tagName}/download")
    @RateLimit(category = "download", authenticated = 120, anonymous = 30)
    public ResponseEntity<InputStreamResource> downloadByTag(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String tagName,
            HttpServletRequest request,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        SkillDownloadService.DownloadResult result = skillDownloadService.downloadByTag(
                namespace, slug, tagName, userId, userNsRoles != null ? userNsRoles : Map.of());

        return buildDownloadResponse(request, result);
    }

    private ResponseEntity<InputStreamResource> buildDownloadResponse(HttpServletRequest request, SkillDownloadService.DownloadResult result) {
        if (shouldRedirectToPresignedUrl(request, result.presignedUrl())) {
            metrics.recordDownloadDelivery("redirect", result.fallbackBundle());
            if (result.fallbackBundle()) {
                metrics.incrementBundleMissingFallback();
            }
            return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, result.presignedUrl())
                .build();
        }

        metrics.recordDownloadDelivery("stream", result.fallbackBundle());
        if (result.fallbackBundle()) {
            metrics.incrementBundleMissingFallback();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
                .contentType(MediaType.parseMediaType(result.contentType()))
                .contentLength(result.contentLength())
                .body(new InputStreamResource(result.openContent()));
    }

    private boolean shouldRedirectToPresignedUrl(HttpServletRequest request, String presignedUrl) {
        if (presignedUrl == null || presignedUrl.isBlank()) {
            return false;
        }
        if (!isSecureRequest(request)) {
            return true;
        }
        try {
            return "https".equalsIgnoreCase(URI.create(presignedUrl).getScheme());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            return "https".equalsIgnoreCase(forwardedProto);
        }
        return request.isSecure();
    }

    private SkillLifecycleVersionResponse toLifecycleVersion(SkillLifecycleProjectionService.VersionProjection projection) {
        if (projection == null) {
            return null;
        }
        return new SkillLifecycleVersionResponse(projection.id(), projection.version(), projection.status());
    }
}
