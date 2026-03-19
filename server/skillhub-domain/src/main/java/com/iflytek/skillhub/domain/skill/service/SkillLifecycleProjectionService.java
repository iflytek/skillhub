package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
            String status
    ) {}

    public record Projection(
            VersionProjection headlineVersion,
            VersionProjection publishedVersion,
            VersionProjection ownerPreviewVersion,
            ResolutionMode resolutionMode
    ) {}

    private final SkillVersionRepository skillVersionRepository;

    public SkillLifecycleProjectionService(SkillVersionRepository skillVersionRepository) {
        this.skillVersionRepository = skillVersionRepository;
    }

    public Projection projectForViewer(Skill skill, String currentUserId, Map<Long, NamespaceRole> userNsRoles) {
        VersionProjection publishedVersion = toProjection(resolvePublishedVersion(skill));
        VersionProjection ownerPreviewVersion = toProjection(resolveOwnerPendingPreview(skill, currentUserId, userNsRoles));
        VersionProjection headlineVersion = publishedVersion != null ? publishedVersion : ownerPreviewVersion;
        ResolutionMode resolutionMode = headlineVersion == null
                ? ResolutionMode.NONE
                : publishedVersion != null ? ResolutionMode.PUBLISHED : ResolutionMode.OWNER_PREVIEW;
        return new Projection(headlineVersion, publishedVersion, ownerPreviewVersion, resolutionMode);
    }

    public Projection projectForOwnerSummary(Skill skill) {
        VersionProjection publishedVersion = toProjection(resolvePublishedVersion(skill));
        VersionProjection ownerPreviewVersion = toProjection(resolveNewestNonPublishedVersion(skill));
        VersionProjection headlineVersion = publishedVersion != null ? publishedVersion : ownerPreviewVersion;
        ResolutionMode resolutionMode = headlineVersion == null
                ? ResolutionMode.NONE
                : publishedVersion != null ? ResolutionMode.PUBLISHED : ResolutionMode.OWNER_PREVIEW;
        return new Projection(headlineVersion, publishedVersion, ownerPreviewVersion, resolutionMode);
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

    private SkillVersion resolveOwnerPendingPreview(Skill skill, String currentUserId, Map<Long, NamespaceRole> userNsRoles) {
        if (!canManage(skill, currentUserId, userNsRoles)) {
            return null;
        }
        return skillVersionRepository.findBySkillIdAndStatus(skill.getId(), SkillVersionStatus.PENDING_REVIEW).stream()
                .max(versionComparator())
                .orElse(null);
    }

    private SkillVersion resolveNewestNonPublishedVersion(Skill skill) {
        List<SkillVersion> versions = skillVersionRepository.findBySkillId(skill.getId());
        return versions.stream()
                .filter(version -> version.getStatus() != SkillVersionStatus.PUBLISHED
                        && version.getStatus() != SkillVersionStatus.YANKED)
                .max(versionComparator())
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
        return new VersionProjection(version.getId(), version.getVersion(), version.getStatus().name());
    }
}
