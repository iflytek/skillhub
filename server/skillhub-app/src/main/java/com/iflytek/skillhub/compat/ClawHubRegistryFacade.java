package com.iflytek.skillhub.compat;

import com.iflytek.skillhub.compat.dto.ClawHubRegistryModeration;
import com.iflytek.skillhub.compat.dto.ClawHubRegistryOwner;
import com.iflytek.skillhub.compat.dto.ClawHubRegistrySearchItem;
import com.iflytek.skillhub.compat.dto.ClawHubRegistrySearchResponse;
import com.iflytek.skillhub.compat.dto.ClawHubRegistrySkill;
import com.iflytek.skillhub.compat.dto.ClawHubRegistrySkillResponse;
import com.iflytek.skillhub.compat.dto.ClawHubRegistrySkillVersion;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.service.SkillSearchAppService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ClawHubRegistryFacade {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final CanonicalSlugMapper canonicalSlugMapper;
    private final SkillSearchAppService skillSearchAppService;
    private final SkillQueryService skillQueryService;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final UserAccountRepository userAccountRepository;

    public ClawHubRegistryFacade(
            CanonicalSlugMapper canonicalSlugMapper,
            SkillSearchAppService skillSearchAppService,
            SkillQueryService skillQueryService,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            UserAccountRepository userAccountRepository) {
        this.canonicalSlugMapper = canonicalSlugMapper;
        this.skillSearchAppService = skillSearchAppService;
        this.skillQueryService = skillQueryService;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.userAccountRepository = userAccountRepository;
    }

    public ClawHubRegistrySearchResponse search(
            String keyword,
            int limit,
            String userId,
            Map<Long, NamespaceRole> userNsRoles) {
        int boundedLimit = clampLimit(limit);
        List<SkillSummaryResponse> items = skillSearchAppService.search(
                        keyword,
                        null,
                        "relevance",
                        0,
                        boundedLimit,
                        userId,
                        normalizeRoles(userNsRoles))
                .items();

        List<ClawHubRegistrySearchItem> results = buildSearchResults(items);
        return new ClawHubRegistrySearchResponse(results);
    }

    public ClawHubRegistrySkillResponse getSkill(
            String canonicalSlug,
            String userId,
            Map<Long, NamespaceRole> userNsRoles) {
        SkillCoordinate coordinate = canonicalSlugMapper.fromCanonical(canonicalSlug);
        SkillQueryService.SkillDetailDTO detail = skillQueryService.getSkillDetail(
                coordinate.namespace(),
                coordinate.slug(),
                userId,
                normalizeRoles(userNsRoles));

        Skill skill = skillRepository.findById(detail.id())
                .orElseThrow(() -> new IllegalStateException("Skill unexpectedly missing: " + canonicalSlug));

        ClawHubRegistrySkill payload = new ClawHubRegistrySkill(
                canonicalSlugMapper.toCanonical(coordinate.namespace(), detail.slug()),
                normalizeDisplayName(detail.displayName(), canonicalSlug),
                detail.summary(),
                List.of(),
                Map.of(),
                toEpochMillis(skill.getCreatedAt()),
                toEpochMillis(skill.getUpdatedAt())
        );

        ClawHubRegistrySkillVersion latestVersion = buildLatestVersion(skill, detail.latestVersion());
        ClawHubRegistryOwner owner = buildOwner(skill.getOwnerId());

        return new ClawHubRegistrySkillResponse(
                payload,
                latestVersion,
                owner,
                ClawHubRegistryModeration.clean()
        );
    }

    public String resolveDownloadUrl(
            String canonicalSlug,
            String version,
            String userId,
            Map<Long, NamespaceRole> userNsRoles) {
        SkillCoordinate coordinate = canonicalSlugMapper.fromCanonical(canonicalSlug);
        String normalizedVersion = normalizeVersion(version);
        return skillQueryService.resolveVersion(
                        coordinate.namespace(),
                        coordinate.slug(),
                        normalizedVersion,
                        null,
                        null,
                        userId,
                        normalizeRoles(userNsRoles))
                .downloadUrl();
    }

    private List<ClawHubRegistrySearchItem> buildSearchResults(List<SkillSummaryResponse> items) {
        return java.util.stream.IntStream.range(0, items.size())
                .mapToObj(index -> toSearchItem(items.get(index), index))
                .toList();
    }

    private ClawHubRegistrySearchItem toSearchItem(SkillSummaryResponse item, int index) {
        String canonicalSlug = canonicalSlugMapper.toCanonical(item.namespace(), item.slug());
        return new ClawHubRegistrySearchItem(
                canonicalSlug,
                normalizeDisplayName(item.displayName(), canonicalSlug),
                item.summary(),
                item.latestVersion(),
                scoreFor(index),
                toEpochMillis(item.updatedAt())
        );
    }

    private ClawHubRegistrySkillVersion buildLatestVersion(Skill skill, String version) {
        if (version == null || version.isBlank()) {
            return null;
        }

        Optional<SkillVersion> latestVersion = skillVersionRepository.findBySkillIdAndVersion(skill.getId(), version);
        if (latestVersion.isEmpty()) {
            return new ClawHubRegistrySkillVersion(version, 0L, "", null);
        }

        SkillVersion entity = latestVersion.get();
        LocalDateTime createdAt = entity.getPublishedAt() != null ? entity.getPublishedAt() : entity.getCreatedAt();
        return new ClawHubRegistrySkillVersion(
                entity.getVersion(),
                toEpochMillis(createdAt),
                entity.getChangelog() != null ? entity.getChangelog() : "",
                null
        );
    }

    private ClawHubRegistryOwner buildOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return ClawHubRegistryOwner.empty();
        }

        return userAccountRepository.findById(ownerId)
                .map(user -> new ClawHubRegistryOwner(
                        null,
                        user.getDisplayName(),
                        user.getAvatarUrl()))
                .orElseGet(ClawHubRegistryOwner::empty);
    }

    private Map<Long, NamespaceRole> normalizeRoles(Map<Long, NamespaceRole> userNsRoles) {
        return userNsRoles != null ? userNsRoles : Map.of();
    }

    private int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String normalizeDisplayName(String displayName, String fallback) {
        if (displayName == null || displayName.isBlank()) {
            return fallback;
        }
        return displayName;
    }

    private String normalizeVersion(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        return version;
    }

    private double scoreFor(int index) {
        return Math.max(0.001d, 1.0d - (index * 0.001d));
    }

    private long toEpochMillis(LocalDateTime timestamp) {
        if (timestamp == null) {
            return 0L;
        }
        return timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
