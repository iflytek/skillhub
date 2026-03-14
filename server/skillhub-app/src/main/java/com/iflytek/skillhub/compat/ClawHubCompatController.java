package com.iflytek.skillhub.compat;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.compat.dto.ClawHubPublishResponse;
import com.iflytek.skillhub.compat.dto.ClawHubSkillItem;
import com.iflytek.skillhub.compat.dto.ClawHubResolveResponse;
import com.iflytek.skillhub.compat.dto.ClawHubSearchResponse;
import com.iflytek.skillhub.compat.dto.ClawHubWhoamiResponse;
import com.iflytek.skillhub.controller.support.ZipPackageExtractor;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.service.SkillSearchAppService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/compat/v1")
public class ClawHubCompatController {

    private final CanonicalSlugMapper mapper;
    private final SkillSearchAppService skillSearchAppService;
    private final SkillQueryService skillQueryService;
    private final SkillPublishService skillPublishService;
    private final ZipPackageExtractor zipPackageExtractor;
    private final AuditLogService auditLogService;

    public ClawHubCompatController(CanonicalSlugMapper mapper,
                                   SkillSearchAppService skillSearchAppService,
                                   SkillQueryService skillQueryService,
                                   SkillPublishService skillPublishService,
                                   ZipPackageExtractor zipPackageExtractor,
                                   AuditLogService auditLogService) {
        this.mapper = mapper;
        this.skillSearchAppService = skillSearchAppService;
        this.skillQueryService = skillQueryService;
        this.skillPublishService = skillPublishService;
        this.zipPackageExtractor = zipPackageExtractor;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/search")
    public ClawHubSearchResponse search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        SkillSearchAppService.SearchResponse response = skillSearchAppService.search(
                q,
                null,
                q == null || q.isBlank() ? "newest" : "relevance",
                page,
                limit,
                userId,
                userNsRoles
        );

        List<ClawHubSkillItem> items = response.items().stream()
                .map(item -> new ClawHubSkillItem(
                        mapper.toCanonical(item.namespace(), item.slug()),
                        item.summary(),
                        item.latestVersion(),
                        item.starCount()))
                .toList();

        return new ClawHubSearchResponse(items);
    }

    @GetMapping("/resolve/{canonicalSlug}")
    public ClawHubResolveResponse resolve(
            @PathVariable String canonicalSlug,
            @RequestParam(defaultValue = "latest") String version,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        SkillQueryService.ResolvedVersionDTO resolved = skillQueryService.resolveVersion(
                coord.namespace(),
                coord.slug(),
                "latest".equals(version) ? null : version,
                "latest".equals(version) ? "latest" : null,
                null,
                userId,
                userNsRoles != null ? userNsRoles : Map.of()
        );
        return new ClawHubResolveResponse(
                canonicalSlug,
                resolved.version(),
                resolved.downloadUrl()
        );
    }

    @GetMapping("/download/{canonicalSlug}")
    public ResponseEntity<Void> download(@PathVariable String canonicalSlug,
                                         @RequestParam(defaultValue = "latest") String version) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        String location = "latest".equals(version)
                ? "/api/v1/skills/" + coord.namespace() + "/" + coord.slug() + "/download"
                : "/api/v1/skills/" + coord.namespace() + "/" + coord.slug() + "/versions/" + version + "/download";
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }

    @PostMapping("/publish")
    public ClawHubPublishResponse publish(@RequestParam("file") MultipartFile file,
                                          @RequestParam("namespace") String namespace,
                                          @AuthenticationPrincipal PlatformPrincipal principal,
                                          HttpServletRequest request) throws IOException {
        SkillPublishService.PublishResult result = skillPublishService.publishFromEntries(
                namespace,
                zipPackageExtractor.extract(file),
                principal.userId(),
                SkillVisibility.PUBLIC,
                principal.platformRoles()
        );
        auditLogService.record(
                principal.userId(),
                "COMPAT_PUBLISH",
                "SKILL_VERSION",
                result.version().getId(),
                MDC.get("requestId"),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                "{\"namespace\":\"" + namespace + "\"}"
        );
        return new ClawHubPublishResponse(
                mapper.toCanonical(namespace, result.slug()),
                result.version().getVersion(),
                result.version().getStatus().name()
        );
    }

    @GetMapping("/whoami")
    public ClawHubWhoamiResponse whoami(@AuthenticationPrincipal PlatformPrincipal principal) {
        return new ClawHubWhoamiResponse(
                principal.userId(),
                principal.displayName(),
                principal.email()
        );
    }
}
