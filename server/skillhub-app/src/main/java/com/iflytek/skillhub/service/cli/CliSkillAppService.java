package com.iflytek.skillhub.service.cli;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.dto.cli.CliDeleteResponse;
import com.iflytek.skillhub.dto.cli.CliDryRunResponse;
import com.iflytek.skillhub.dto.cli.CliPublishResponse;
import com.iflytek.skillhub.dto.cli.CliResolveResponse;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SkillDeleteAppService;
import com.iflytek.skillhub.service.SkillSearchAppService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CliSkillAppService {

    private final SkillSearchAppService skillSearchAppService;
    private final SkillQueryService skillQueryService;
    private final SkillDownloadService skillDownloadService;
    private final SkillDeleteAppService skillDeleteAppService;
    private final SkillPublishService skillPublishService;

    public CliSkillAppService(
            SkillSearchAppService skillSearchAppService,
            SkillQueryService skillQueryService,
            SkillDownloadService skillDownloadService,
            SkillDeleteAppService skillDeleteAppService,
            SkillPublishService skillPublishService) {
        this.skillSearchAppService = skillSearchAppService;
        this.skillQueryService = skillQueryService;
        this.skillDownloadService = skillDownloadService;
        this.skillDeleteAppService = skillDeleteAppService;
        this.skillPublishService = skillPublishService;
    }

    public record CliSearchItem(String namespace, String slug, String latestVersion, String summary) {}
    public record CliSearchResult(List<CliSearchItem> items, long total, int limit) {}

    public CliSearchResult search(String q, int limit, String userId, Map<Long, NamespaceRole> userNsRoles) {
        SkillSearchAppService.SearchResponse response = skillSearchAppService.search(
                q, null, "newest", 0, limit, userId, userNsRoles
        );

        List<CliSearchItem> items = response.items().stream()
                .map(item -> new CliSearchItem(
                        item.namespace(),
                        item.slug(),
                        item.publishedVersion() != null ? item.publishedVersion().version() : null,
                        item.summary()
                ))
                .toList();

        return new CliSearchResult(items, response.total(), limit);
    }

    public CliResolveResponse resolve(String namespace, String slug, String version, String userId, Map<Long, NamespaceRole> userNsRoles) {
        SkillQueryService.ResolvedVersionDTO resolved = skillQueryService.resolveVersion(
                namespace, slug, version, null, null, userId, userNsRoles
        );

        return new CliResolveResponse(
                resolved.namespace(),
                resolved.slug(),
                resolved.version(),
                resolved.versionId(),
                resolved.fingerprint(),
                resolved.downloadUrl()
        );
    }

    public ResponseEntity<InputStreamResource> downloadLatest(String namespace, String slug, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        @SuppressWarnings("unchecked")
        Map<Long, NamespaceRole> userNsRoles = (Map<Long, NamespaceRole>) request.getAttribute("userNsRoles");

        SkillDownloadService.DownloadResult result = skillDownloadService.downloadLatest(
                namespace, slug, userId, userNsRoles != null ? userNsRoles : Map.of()
        );

        return buildDownloadResponse(result);
    }

    public ResponseEntity<InputStreamResource> downloadVersion(String namespace, String slug, String version, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        @SuppressWarnings("unchecked")
        Map<Long, NamespaceRole> userNsRoles = (Map<Long, NamespaceRole>) request.getAttribute("userNsRoles");

        SkillDownloadService.DownloadResult result = skillDownloadService.downloadVersion(
                namespace, slug, version, userId, userNsRoles != null ? userNsRoles : Map.of()
        );

        return buildDownloadResponse(result);
    }

    public CliDeleteResponse deleteRemote(String namespace, String slug, String actorUserId, AuditRequestContext auditContext) {
        SkillDeleteAppService.DeleteResult result = skillDeleteAppService.deleteSkill(
                namespace, slug, null, actorUserId, auditContext
        );

        return new CliDeleteResponse(
                result.deleted(),
                "remote",
                "delete",
                result.namespace(),
                result.slug()
        );
    }

    public CliDryRunResponse validatePublish(String namespace, List<PackageEntry> entries, String publisherId, SkillVisibility visibility, Set<String> platformRoles) {
        SkillPublishService.DryRunResult result = skillPublishService.validateOnly(
                namespace, entries, publisherId, visibility, platformRoles);
        return new CliDryRunResponse(
                result.valid(),
                result.errors(),
                result.warnings(),
                result.resolvedSlug(),
                result.resolvedVersion()
        );
    }

    public CliPublishResponse publish(String namespace, List<PackageEntry> entries, String publisherId, SkillVisibility visibility, Set<String> platformRoles) {
        SkillPublishService.PublishResult result = skillPublishService.publishFromEntries(
                namespace, entries, publisherId, visibility, platformRoles, false
        );

        return new CliPublishResponse(
                namespace,
                result.slug(),
                result.version().getVersion(),
                visibility.name()
        );
    }

    private ResponseEntity<InputStreamResource> buildDownloadResponse(SkillDownloadService.DownloadResult result) {
        if (result.presignedUrl() != null) {
            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, result.presignedUrl())
                    .build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
                .contentType(MediaType.parseMediaType(result.contentType()))
                .contentLength(result.contentLength())
                .body(new InputStreamResource(result.openContent()));
    }
}
