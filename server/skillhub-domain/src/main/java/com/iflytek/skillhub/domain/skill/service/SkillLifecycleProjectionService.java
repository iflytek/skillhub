package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillInstallability;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Builds lightweight lifecycle projections that describe which skill version should be surfaced to
 * a given viewer.
 */
@Service
public class SkillLifecycleProjectionService {

    public enum ResolutionMode {
        PUBLISHED,
        OWNER_PREVIEW,
        NONE
    }

    public record VersionProjection(
            Long id,
            String version,
            String status,
            String parsedMetadataJson
    ) {
        public VersionProjection(Long id, String version, String status) {
            this(id, version, status, null);
        }
    }

    public record Projection(
            VersionProjection headlineVersion,
            VersionProjection publishedVersion,
            VersionProjection ownerPreviewVersion,
            ResolutionMode resolutionMode
    ) {}

    private static final Comparator<SkillVersion> RECENCY = Comparator
            .comparing(SkillVersion::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SkillVersion::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final SkillVersionRepository skillVersionRepository;

    public SkillLifecycleProjectionService(SkillVersionRepository skillVersionRepository) {
        this.skillVersionRepository = skillVersionRepository;
    }

    public Projection projectForViewer(Skill skill, String currentUserId, Map<Long, NamespaceRole> userNsRoles) {
        SkillVersion published = resolvePublishedVersion(skill);
        SkillVersion preview = canManage(skill, currentUserId, userNsRoles)
                ? resolveNewerNonPublishedVersion(skill, published)
                : null;
        return buildProjection(published, preview);
    }

    public Projection projectForOwnerSummary(Skill skill) {
        SkillVersion published = resolvePublishedVersion(skill);
        SkillVersion preview = resolveNewerNonPublishedVersion(skill, published);
        return buildProjection(published, preview);
    }

    private Projection buildProjection(SkillVersion published, SkillVersion preview) {
        VersionProjection publishedVersion = toProjection(published);
        VersionProjection ownerPreviewVersion = toProjection(preview);
        VersionProjection headlineVersion = publishedVersion != null ? publishedVersion : ownerPreviewVersion;
        ResolutionMode resolutionMode = headlineVersion == null ? ResolutionMode.NONE
                : publishedVersion != null ? ResolutionMode.PUBLISHED
                : ResolutionMode.OWNER_PREVIEW;
        return new Projection(headlineVersion, publishedVersion, ownerPreviewVersion, resolutionMode);
    }

    public Map<Long, Projection> projectPublishedSummaries(List<Skill> skills) {
        if (skills.isEmpty()) {
            return Map.of();
        }

        Map<Long, SkillVersion> latestVersionsById = skillVersionRepository.findByIdIn(
                        skills.stream()
                                .map(Skill::getLatestVersionId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList())
                .stream()
                .collect(Collectors.toMap(SkillVersion::getId, Function.identity()));

        Map<Long, SkillVersion> publishedBySkillId = new java.util.HashMap<>();
        for (Skill skill : skills) {
            SkillVersion latestVersion = latestVersionsById.get(skill.getLatestVersionId());
            if (SkillInstallability.isInstallableVersion(latestVersion)) {
                publishedBySkillId.put(skill.getId(), latestVersion);
            }
        }

        return skills.stream().collect(Collectors.toMap(Skill::getId, skill -> {
            VersionProjection publishedVersion = toProjection(publishedBySkillId.get(skill.getId()));
            ResolutionMode resolutionMode = publishedVersion == null ? ResolutionMode.NONE : ResolutionMode.PUBLISHED;
            return new Projection(publishedVersion, publishedVersion, null, resolutionMode);
        }));
    }

    private SkillVersion resolvePublishedVersion(Skill skill) {
        if (skill.getLatestVersionId() != null) {
            SkillVersion latest = skillVersionRepository.findById(skill.getLatestVersionId()).orElse(null);
            if (latest != null && latest.getStatus() == SkillVersionStatus.PUBLISHED) {
                return latest;
            }
        }
        return skillVersionRepository.findBySkillIdAndStatus(skill.getId(), SkillVersionStatus.PUBLISHED).stream()
                .max(versionComparator())
                .orElse(null);
    }

    /**
     * Returns the newest non-published version (PENDING_REVIEW, REJECTED, DRAFT, SCANNING,
     * SCAN_FAILED) that represents a NEW round of work layered on top of the current published
     * version. A non-published version that is older than the published version is treated as
     * settled history (e.g. an early rejected attempt later superseded by a published release)
     * and is intentionally not surfaced, so the owner does not see a stale preview/rejected badge
     * next to an already-published skill.
     */
    private SkillVersion resolveNewerNonPublishedVersion(Skill skill, SkillVersion publishedVersion) {
        return skillVersionRepository.findBySkillId(skill.getId()).stream()
                .filter(version -> version.getStatus() != SkillVersionStatus.PUBLISHED
                        && version.getStatus() != SkillVersionStatus.YANKED)
                .filter(version -> publishedVersion == null || RECENCY.compare(version, publishedVersion) > 0)
                .max(RECENCY)
                .orElse(null);
    }

    private boolean canManage(Skill skill, String currentUserId, Map<Long, NamespaceRole> userNsRoles) {
        if (currentUserId == null) {
            return false;
        }
        NamespaceRole role = userNsRoles.get(skill.getNamespaceId());
        return skill.getOwnerId().equals(currentUserId)
                || role == NamespaceRole.ADMIN
                || role == NamespaceRole.OWNER;
    }

    private Comparator<SkillVersion> versionComparator() {
        return Comparator
                .comparing(SkillVersion::getPublishedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SkillVersion::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SkillVersion::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private VersionProjection toProjection(SkillVersion version) {
        if (version == null) {
            return null;
        }
        return new VersionProjection(
                version.getId(),
                version.getVersion(),
                version.getStatus().name(),
                version.getParsedMetadataJson()
        );
    }
}
