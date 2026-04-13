package com.iflytek.skillhub.compat;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.compat.dto.ClawHubDeleteResponse;
import com.iflytek.skillhub.compat.dto.ClawHubPublishResponse;
import com.iflytek.skillhub.compat.dto.ClawHubResolveResponse;
import com.iflytek.skillhub.compat.dto.ClawHubSearchResponse;
import com.iflytek.skillhub.compat.dto.ClawHubSkillListResponse;
import com.iflytek.skillhub.compat.dto.ClawHubSkillResponse;
import com.iflytek.skillhub.compat.dto.ClawHubStarResponse;
import com.iflytek.skillhub.compat.dto.ClawHubUnstarResponse;
import com.iflytek.skillhub.compat.dto.ClawHubWhoamiResponse;
import com.iflytek.skillhub.controller.support.MultipartPackageExtractor;
import com.iflytek.skillhub.controller.support.ZipPackageExtractor;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.social.SkillStarService;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.service.SkillSearchAppService;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Compatibility-focused application service that keeps ClawHub transport logic
 * out of the controller while preserving the existing wire contract.
 */
@Service
public class ClawHubCompatAppService {

    private static final String GLOBAL_NAMESPACE = "global";

    private final CanonicalSlugMapper mapper;
    private final SkillSearchAppService skillSearchAppService;
    private final SkillQueryService skillQueryService;
    private final SkillPublishService skillPublishService;
    private final ZipPackageExtractor zipPackageExtractor;
    private final MultipartPackageExtractor multipartPackageExtractor;
    private final AuditLogService auditLogService;
    private final CompatSkillLookupService compatSkillLookupService;
    private final SkillStarService skillStarService;

    public ClawHubCompatAppService(CanonicalSlugMapper mapper,
                                   SkillSearchAppService skillSearchAppService,
                                   SkillQueryService skillQueryService,
                                   SkillPublishService skillPublishService,
                                   ZipPackageExtractor zipPackageExtractor,
                                   MultipartPackageExtractor multipartPackageExtractor,
                                   AuditLogService auditLogService,
                                   CompatSkillLookupService compatSkillLookupService,
                                   SkillStarService skillStarService) {
        this.mapper = mapper;
        this.skillSearchAppService = skillSearchAppService;
        this.skillQueryService = skillQueryService;
        this.skillPublishService = skillPublishService;
        this.zipPackageExtractor = zipPackageExtractor;
        this.multipartPackageExtractor = multipartPackageExtractor;
        this.auditLogService = auditLogService;
        this.compatSkillLookupService = compatSkillLookupService;
        this.skillStarService = skillStarService;
    }

    public ClawHubSearchResponse search(String q,
                                        int page,
                                        int limit,
                                        String userId,
                                        Map<Long, NamespaceRole> userNsRoles) {
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

    public ClawHubResolveResponse resolveByQuery(String slug,
                                                 String version,
                                                 String hash,
                                                 String userId,
                                                 Map<Long, NamespaceRole> userNsRoles) {
        SkillCoordinate coord = resolveQueryCoordinate(slug);

        SkillQueryService.ResolvedVersionDTO resolved = skillQueryService.resolveVersion(
                coord.namespace(),
                coord.slug(),
                "latest".equals(version) ? null : version,
                "latest".equals(version) ? "latest" : null,
                hash,
                userId,
                userNsRoles != null ? userNsRoles : Map.of()
        );
        return toResolveResponse(resolved);
    }

    public ClawHubResolveResponse resolve(String canonicalSlug,
                                          String version,
                                          String userId,
                                          Map<Long, NamespaceRole> userNsRoles) {
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
        return toResolveResponse(resolved);
    }

    public String downloadLocationByPath(String canonicalSlug, String version) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        return "latest".equals(version)
                ? "/api/v1/skills/" + coord.namespace() + "/" + coord.slug() + "/download"
                : "/api/v1/skills/" + coord.namespace() + "/" + coord.slug() + "/versions/" + version + "/download";
    }

    public String downloadLocationByQuery(String slug, String version) {
        SkillCoordinate coord = resolveQueryCoordinate(slug);
        return "latest".equals(version)
                ? "/api/v1/skills/" + coord.namespace() + "/" + coord.slug() + "/download"
                : "/api/v1/skills/" + coord.namespace() + "/" + coord.slug() + "/versions/" + version + "/download";
    }

    private SkillCoordinate resolveQueryCoordinate(String slug) {
        if (slug != null && slug.contains("--")) {
            return mapper.fromCanonical(slug);
        }
        try {
            CompatSkillLookupService.CompatSkillContext context = compatSkillLookupService.findByLegacySlug(slug);
            return new SkillCoordinate(context.namespace().getSlug(), context.skill().getSlug());
        } catch (DomainNotFoundException ex) {
            return mapper.fromCanonical(slug);
        }
    }

    public ClawHubSkillListResponse listSkills(int page,
                                               int limit,
                                               String sort,
                                               String userId,
                                               Map<Long, NamespaceRole> userNsRoles) {
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

        List<ClawHubSkillListResponse.SkillListItem> items = response.items().stream()
                .map(this::toSkillListItem)
                .toList();

        String nextCursor = null;
        long totalResults = response.total();
        long currentOffset = (long) page * limit;
        if (currentOffset + items.size() < totalResults) {
            nextCursor = String.valueOf(page + 1);
        }

        return new ClawHubSkillListResponse(items, nextCursor);
    }

    public ClawHubSkillResponse getSkill(String canonicalSlug, String userId) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        CompatSkillLookupService.CompatSkillContext context = compatSkillLookupService.resolveVisible(
                coord.namespace(),
                coord.slug(),
                userId
        );
        SkillVersion latestVersionEntity = context.latestVersion().orElse(null);

        ClawHubSkillResponse.SkillInfo skillInfo = null;
        ClawHubSkillResponse.VersionInfo versionInfo = null;

        if (context.skill().getId() != null) {
            long createdAt = context.skill().getCreatedAt() != null ? context.skill().getCreatedAt().toEpochMilli() : 0;
            long updatedAt = context.skill().getUpdatedAt() != null ? context.skill().getUpdatedAt().toEpochMilli() : 0;
            skillInfo = new ClawHubSkillResponse.SkillInfo(
                    mapper.toCanonical(coord.namespace(), coord.slug()),
                    context.skill().getDisplayName(),
                    context.skill().getSummary(),
                    Map.of(),
                    Map.of(),
                    createdAt,
                    updatedAt
            );

            if (latestVersionEntity != null) {
                long versionCreatedAt = latestVersionEntity.getPublishedAt() != null
                        ? latestVersionEntity.getPublishedAt().toEpochMilli()
                        : 0;
                versionInfo = new ClawHubSkillResponse.VersionInfo(
                        latestVersionEntity.getVersion(),
                        versionCreatedAt,
                        latestVersionEntity.getChangelog() == null ? "" : latestVersionEntity.getChangelog(),
                        null
                );
            }
        }

        return new ClawHubSkillResponse(
                skillInfo,
                versionInfo,
                null,
                new ClawHubSkillResponse.ModerationInfo(false, false, "clean", new String[0], null, null, null)
        );
    }

    public ClawHubDeleteResponse deleteSkill() {
        return new ClawHubDeleteResponse();
    }

    public ClawHubDeleteResponse undeleteSkill() {
        return new ClawHubDeleteResponse();
    }

    public ClawHubStarResponse starSkill(String canonicalSlug, PlatformPrincipal principal) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        CompatSkillLookupService.CompatSkillContext context = compatSkillLookupService.resolveVisible(
                coord.namespace(),
                coord.slug(),
                principal.userId()
        );

        boolean alreadyStarred = skillStarService.isStarred(context.skill().getId(), principal.userId());
        skillStarService.star(context.skill().getId(), principal.userId());
        return new ClawHubStarResponse(true, alreadyStarred);
    }

    public ClawHubUnstarResponse unstarSkill(String canonicalSlug, PlatformPrincipal principal) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        CompatSkillLookupService.CompatSkillContext context = compatSkillLookupService.resolveVisible(
                coord.namespace(),
                coord.slug(),
                principal.userId()
        );

        boolean alreadyUnstarred = !skillStarService.isStarred(context.skill().getId(), principal.userId());
        skillStarService.unstar(context.skill().getId(), principal.userId());
        return new ClawHubUnstarResponse(true, alreadyUnstarred);
    }

    public ClawHubPublishResponse publishSkill(String payloadJson,
                                               MultipartFile[] files,
                                               boolean confirmWarnings,
                                               PlatformPrincipal principal,
                                               String clientIp,
                                               String userAgent) throws IOException {
        MultipartPackageExtractor.ExtractedPackage extracted = multipartPackageExtractor.extract(files, payloadJson);
        String namespace = determineNamespace(extracted.payload());
        SkillPublishService.PublishResult result = skillPublishService.publishFromEntries(
                namespace,
                extracted.entries(),
                principal.userId(),
                SkillVisibility.PUBLIC,
                principal.platformRoles(),
                confirmWarnings
        );
        recordCompatPublishAudit(principal.userId(), result.version().getId(), clientIp, userAgent,
                "{\"namespace\":\"" + namespace + "\",\"slug\":\"" + extracted.payload().slug() + "\"}");
        return new ClawHubPublishResponse(result.skillId().toString(), result.version().getId().toString());
    }

    public ClawHubPublishResponse publish(MultipartFile file,
                                          String namespace,
                                          boolean confirmWarnings,
                                          PlatformPrincipal principal,
                                          String clientIp,
                                          String userAgent) throws IOException {
        SkillPublishService.PublishResult result = skillPublishService.publishFromEntries(
                namespace,
                zipPackageExtractor.extract(file),
                principal.userId(),
                SkillVisibility.PUBLIC,
                principal.platformRoles(),
                confirmWarnings
        );
        recordCompatPublishAudit(principal.userId(), result.version().getId(), clientIp, userAgent,
                "{\"namespace\":\"" + namespace + "\"}");
        return new ClawHubPublishResponse(result.skillId().toString(), result.version().getId().toString());
    }

    public ClawHubWhoamiResponse whoami(PlatformPrincipal principal) {
        return new ClawHubWhoamiResponse(
                principal.userId(),
                principal.displayName(),
                principal.avatarUrl()
        );
    }

    private ClawHubSearchResponse.ClawHubSearchResult toSearchResult(SkillSummaryResponse item) {
        Long updatedAtEpoch = item.updatedAt() != null ? item.updatedAt().toEpochMilli() : null;
        return new ClawHubSearchResponse.ClawHubSearchResult(
                mapper.toCanonical(item.namespace(), item.slug()),
                item.displayName(),
                item.summary(),
                item.publishedVersion() != null ? item.publishedVersion().version() : null,
                calculateScore(item),
                updatedAtEpoch
        );
    }

    private double calculateScore(SkillSummaryResponse item) {
        int starScore = item.starCount() != null ? item.starCount() * 10 : 0;
        long downloadScore = item.downloadCount() != null ? item.downloadCount() : 0;
        return (starScore + downloadScore) / 100.0;
    }

    private ClawHubResolveResponse toResolveResponse(SkillQueryService.ResolvedVersionDTO resolved) {
        ClawHubResolveResponse.VersionInfo matchVersion = resolved.version() != null
                ? new ClawHubResolveResponse.VersionInfo(resolved.version())
                : null;
        ClawHubResolveResponse.VersionInfo latestVersion = resolved.version() != null
                ? new ClawHubResolveResponse.VersionInfo(resolved.version())
                : null;
        return new ClawHubResolveResponse(matchVersion, latestVersion);
    }

    private ClawHubSkillListResponse.SkillListItem toSkillListItem(SkillSummaryResponse item) {
        long createdAt = 0;
        long updatedAt = item.updatedAt() != null ? item.updatedAt().toEpochMilli() : 0;

        ClawHubSkillListResponse.SkillListItem.LatestVersion latestVersion = null;
        if (item.publishedVersion() != null) {
            latestVersion = new ClawHubSkillListResponse.SkillListItem.LatestVersion(
                    item.publishedVersion().version(),
                    updatedAt,
                    "",
                    null
            );
        }

        Map<String, Object> stats = new HashMap<>();
        if (item.downloadCount() != null) {
            stats.put("downloads", item.downloadCount());
        }
        if (item.starCount() != null) {
            stats.put("stars", item.starCount());
        }

        return new ClawHubSkillListResponse.SkillListItem(
                mapper.toCanonical(item.namespace(), item.slug()),
                item.displayName(),
                item.summary(),
                Map.of(),
                stats,
                createdAt,
                updatedAt,
                latestVersion
        );
    }

    private String determineNamespace(MultipartPackageExtractor.PublishPayload payload) {
        if (payload == null) {
            return GLOBAL_NAMESPACE;
        }

        if (StringUtils.hasText(payload.namespace())) {
            return normalizeNamespace(payload.namespace());
        }

        if (StringUtils.hasText(payload.slug()) && payload.slug().contains("--")) {
            return mapper.fromCanonical(payload.slug()).namespace();
        }

        return GLOBAL_NAMESPACE;
    }

    private String normalizeNamespace(String namespace) {
        String trimmed = namespace.trim();
        if (trimmed.startsWith("@")) {
            return trimmed.substring(1);
        }
        return trimmed;
    }

    private void recordCompatPublishAudit(String userId,
                                          Long versionId,
                                          String clientIp,
                                          String userAgent,
                                          String detailJson) {
        auditLogService.record(
                userId,
                "COMPAT_PUBLISH",
                "SKILL_VERSION",
                versionId,
                MDC.get("requestId"),
                clientIp,
                userAgent,
                detailJson
        );
    }

}
