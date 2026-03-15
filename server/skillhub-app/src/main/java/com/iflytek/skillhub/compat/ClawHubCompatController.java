package com.iflytek.skillhub.compat;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.compat.dto.ClawHubDeleteResponse;
import com.iflytek.skillhub.compat.dto.ClawHubPublishResponse;
import com.iflytek.skillhub.compat.dto.ClawHubResolveResponse;
import com.iflytek.skillhub.compat.dto.ClawHubSearchResponse;
import com.iflytek.skillhub.compat.dto.ClawHubSkillResponse;
import com.iflytek.skillhub.compat.dto.ClawHubStarResponse;
import com.iflytek.skillhub.compat.dto.ClawHubUnstarResponse;
import com.iflytek.skillhub.compat.dto.ClawHubWhoamiResponse;
import com.iflytek.skillhub.controller.support.MultipartPackageExtractor;
import com.iflytek.skillhub.controller.support.ZipPackageExtractor;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.social.SkillStarService;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ClawHubCompatController {

    private final CanonicalSlugMapper mapper;
    private final SkillSearchAppService skillSearchAppService;
    private final SkillQueryService skillQueryService;
    private final SkillPublishService skillPublishService;
    private final ZipPackageExtractor zipPackageExtractor;
    private final MultipartPackageExtractor multipartPackageExtractor;
    private final AuditLogService auditLogService;
    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillStarService skillStarService;

    public ClawHubCompatController(CanonicalSlugMapper mapper,
                                   SkillSearchAppService skillSearchAppService,
                                   SkillQueryService skillQueryService,
                                   SkillPublishService skillPublishService,
                                   ZipPackageExtractor zipPackageExtractor,
                                   MultipartPackageExtractor multipartPackageExtractor,
                                   AuditLogService auditLogService,
                                   SkillRepository skillRepository,
                                   NamespaceRepository namespaceRepository,
                                   SkillVersionRepository skillVersionRepository,
                                   SkillStarService skillStarService) {
        this.mapper = mapper;
        this.skillSearchAppService = skillSearchAppService;
        this.skillQueryService = skillQueryService;
        this.skillPublishService = skillPublishService;
        this.zipPackageExtractor = zipPackageExtractor;
        this.multipartPackageExtractor = multipartPackageExtractor;
        this.auditLogService = auditLogService;
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillStarService = skillStarService;
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

        List<ClawHubSearchResponse.ClawHubSearchResult> results = response.items().stream()
                .map(this::toSearchResult)
                .toList();

        return new ClawHubSearchResponse(results);
    }

    private ClawHubSearchResponse.ClawHubSearchResult toSearchResult(SkillSummaryResponse item) {
        Long updatedAtEpoch = item.updatedAt() != null
                ? item.updatedAt().toInstant(ZoneOffset.UTC).toEpochMilli()
                : null;
        return new ClawHubSearchResponse.ClawHubSearchResult(
                mapper.toCanonical(item.namespace(), item.slug()),
                item.displayName(),
                item.summary(),
                item.latestVersion(),
                calculateScore(item),
                updatedAtEpoch
        );
    }

    private double calculateScore(SkillSummaryResponse item) {
        // Simple score calculation based on stars and downloads
        int starScore = item.starCount() != null ? item.starCount() * 10 : 0;
        long downloadScore = item.downloadCount() != null ? item.downloadCount() : 0;
        return (starScore + downloadScore) / 100.0;
    }

    @GetMapping("/resolve")
    public ClawHubResolveResponse resolveByQuery(
            @RequestParam String slug,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String hash,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        // For resolve endpoint with query params, slug is just the skill slug without namespace
        // We need to find the skill by slug (this is a simplification - in real world you'd need more context)
        Skill skill = skillRepository.findBySlug(slug).stream().findFirst()
                .orElseThrow(() -> new DomainNotFoundException("error.skill.notFound", slug));

        Namespace ns = namespaceRepository.findById(skill.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("error.namespace.notFound", skill.getNamespaceId()));

        SkillQueryService.ResolvedVersionDTO resolved = skillQueryService.resolveVersion(
                ns.getSlug(),
                skill.getSlug(),
                "latest".equals(version) ? null : version,
                "latest".equals(version) ? "latest" : null,
                hash,
                userId,
                userNsRoles != null ? userNsRoles : Map.of()
        );

        ClawHubResolveResponse.VersionInfo matchVersion = resolved.version() != null
                ? new ClawHubResolveResponse.VersionInfo(resolved.version())
                : null;
        ClawHubResolveResponse.VersionInfo latestVersion = resolved.version() != null
                ? new ClawHubResolveResponse.VersionInfo(resolved.version())
                : null;

        return new ClawHubResolveResponse(matchVersion, latestVersion);
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

        ClawHubResolveResponse.VersionInfo matchVersion = resolved.version() != null
                ? new ClawHubResolveResponse.VersionInfo(resolved.version())
                : null;
        ClawHubResolveResponse.VersionInfo latestVersion = resolved.version() != null
                ? new ClawHubResolveResponse.VersionInfo(resolved.version())
                : null;

        return new ClawHubResolveResponse(matchVersion, latestVersion);
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

    @GetMapping("/skills")
    public ClawHubSearchResponse listSkills(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int limit,
            @RequestParam(required = false) String sort,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        // Use search with empty query to list skills
        String sortBy = sort != null ? sort : "newest";
        SkillSearchAppService.SearchResponse response = skillSearchAppService.search(
                "",
                null,
                sortBy,
                page,
                limit,
                userId,
                userNsRoles
        );

        List<ClawHubSearchResponse.ClawHubSearchResult> results = response.items().stream()
                .map(this::toSearchResult)
                .toList();

        return new ClawHubSearchResponse(results);
    }

    @GetMapping("/skills/{canonicalSlug}")
    public ClawHubSkillResponse getSkill(
            @PathVariable String canonicalSlug,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);

        Namespace ns = namespaceRepository.findBySlug(coord.namespace())
                .orElseThrow(() -> new DomainNotFoundException("error.namespace.notFound", coord.namespace()));
        Skill skill = skillRepository.findByNamespaceIdAndSlug(ns.getId(), coord.slug())
                .orElseThrow(() -> new DomainNotFoundException("error.skill.notFound", coord.slug()));

        SkillVersion latestVersionEntity = null;
        if (skill.getLatestVersionId() != null) {
            latestVersionEntity = skillVersionRepository.findById(skill.getLatestVersionId()).orElse(null);
        }

        ClawHubSkillResponse.SkillInfo skillInfo = null;
        ClawHubSkillResponse.VersionInfo versionInfo = null;

        if (skill.getId() != null) {
            long createdAt = skill.getCreatedAt() != null
                    ? skill.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli()
                    : 0;
            long updatedAt = skill.getUpdatedAt() != null
                    ? skill.getUpdatedAt().toInstant(ZoneOffset.UTC).toEpochMilli()
                    : 0;
            skillInfo = new ClawHubSkillResponse.SkillInfo(
                    mapper.toCanonical(coord.namespace(), coord.slug()),
                    skill.getDisplayName(),
                    skill.getSummary(),
                    Map.of(), // tags
                    Map.of(), // stats
                    createdAt,
                    updatedAt
            );

            if (latestVersionEntity != null) {
                long versionCreatedAt = latestVersionEntity.getPublishedAt() != null
                        ? latestVersionEntity.getPublishedAt().toInstant(ZoneOffset.UTC).toEpochMilli()
                        : 0;
                versionInfo = new ClawHubSkillResponse.VersionInfo(
                        latestVersionEntity.getVersion(),
                        versionCreatedAt,
                        latestVersionEntity.getChangelog(),
                        null // license
                );
            }
        }

        // Owner info - we don't have this readily available, return null
        ClawHubSkillResponse.OwnerInfo ownerInfo = null;

        // Moderation info - not implemented yet
        ClawHubSkillResponse.ModerationInfo moderationInfo = new ClawHubSkillResponse.ModerationInfo(
                false, false, "clean", new String[0], null, null, null
        );

        return new ClawHubSkillResponse(skillInfo, versionInfo, ownerInfo, moderationInfo);
    }

    @DeleteMapping("/skills/{canonicalSlug}")
    public ClawHubDeleteResponse deleteSkill(
            @PathVariable String canonicalSlug,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        // Note: Full delete not implemented yet, just return ok for compatibility
        return new ClawHubDeleteResponse();
    }

    @PostMapping("/skills/{canonicalSlug}/undelete")
    public ClawHubDeleteResponse undeleteSkill(
            @PathVariable String canonicalSlug,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        // Note: Undelete not implemented yet, just return ok for compatibility
        return new ClawHubDeleteResponse();
    }

    @PostMapping("/stars/{canonicalSlug}")
    public ClawHubStarResponse starSkill(
            @PathVariable String canonicalSlug,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        Namespace ns = namespaceRepository.findBySlug(coord.namespace())
                .orElseThrow(() -> new DomainNotFoundException("error.namespace.notFound", coord.namespace()));
        Skill skill = skillRepository.findByNamespaceIdAndSlug(ns.getId(), coord.slug())
                .orElseThrow(() -> new DomainNotFoundException("error.skill.notFound", canonicalSlug));

        boolean alreadyStarred = skillStarService.isStarred(skill.getId(), principal.userId());
        skillStarService.star(skill.getId(), principal.userId());

        return new ClawHubStarResponse(true, alreadyStarred);
    }

    @DeleteMapping("/stars/{canonicalSlug}")
    public ClawHubUnstarResponse unstarSkill(
            @PathVariable String canonicalSlug,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        Namespace ns = namespaceRepository.findBySlug(coord.namespace())
                .orElseThrow(() -> new DomainNotFoundException("error.namespace.notFound", coord.namespace()));
        Skill skill = skillRepository.findByNamespaceIdAndSlug(ns.getId(), coord.slug())
                .orElseThrow(() -> new DomainNotFoundException("error.skill.notFound", canonicalSlug));

        boolean alreadyUnstarred = !skillStarService.isStarred(skill.getId(), principal.userId());
        skillStarService.unstar(skill.getId(), principal.userId());

        return new ClawHubUnstarResponse(true, alreadyUnstarred);
    }

    @PostMapping("/skills")
    public ClawHubPublishResponse publishSkill(@RequestParam("payload") String payloadJson,
                                               @RequestParam("files") MultipartFile[] files,
                                               @AuthenticationPrincipal PlatformPrincipal principal,
                                               HttpServletRequest request) throws IOException {
        MultipartPackageExtractor.ExtractedPackage extracted = multipartPackageExtractor.extract(files, payloadJson);
        String namespace = determineNamespace(principal, extracted.payload());
        SkillPublishService.PublishResult result = skillPublishService.publishFromEntries(
                namespace,
                extracted.entries(),
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
                "{\"namespace\":\"" + namespace + "\",\"slug\":\"" + extracted.payload().slug() + "\"}"
        );
        return new ClawHubPublishResponse(
                result.skillId().toString(),
                result.version().getId().toString()
        );
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
                result.skillId().toString(),
                result.version().getId().toString()
        );
    }

    private String determineNamespace(PlatformPrincipal principal, MultipartPackageExtractor.PublishPayload payload) {
        // Use "global" namespace by default for compatibility
        return "global";
    }

    @GetMapping("/whoami")
    public ClawHubWhoamiResponse whoami(@AuthenticationPrincipal PlatformPrincipal principal) {
        return new ClawHubWhoamiResponse(
                principal.userId(),
                principal.displayName(),
                principal.avatarUrl()
        );
    }
}
