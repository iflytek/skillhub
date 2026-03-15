package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SkillQueryService {

    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillFileRepository skillFileRepository;
    private final SkillTagRepository skillTagRepository;
    private final ObjectStorageService objectStorageService;
    private final VisibilityChecker visibilityChecker;

    public SkillQueryService(
            NamespaceRepository namespaceRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            SkillFileRepository skillFileRepository,
            SkillTagRepository skillTagRepository,
            ObjectStorageService objectStorageService,
            VisibilityChecker visibilityChecker) {
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillFileRepository = skillFileRepository;
        this.skillTagRepository = skillTagRepository;
        this.objectStorageService = objectStorageService;
        this.visibilityChecker = visibilityChecker;
    }

    public record SkillDetailDTO(
            Long id,
            String slug,
            String displayName,
            String summary,
            String visibility,
            String status,
            Long downloadCount,
            Integer starCount,
            java.math.BigDecimal ratingAvg,
            Integer ratingCount,
            boolean hidden,
            String latestVersion,
            Long namespaceId,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt,
            SkillVersion latestVersionEntity,
            boolean canManageLifecycle,
            String viewingVersionStatus,
            boolean canInteract
    ) {}

    public record SkillVersionDetailDTO(
            Long id,
            String version,
            String status,
            String changelog,
            Integer fileCount,
            Long totalSize,
            java.time.LocalDateTime publishedAt,
            String parsedMetadataJson,
            String manifestJson
    ) {}

    public record ResolvedVersionDTO(
            Long skillId,
            String namespace,
            String slug,
            String version,
            Long versionId,
            String fingerprint,
            Boolean matched,
            String downloadUrl
    ) {}

    public SkillDetailDTO getSkillDetail(
            String namespaceSlug,
            String skillSlug,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {

        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", skillSlug));

        // Visibility check
        if (!visibilityChecker.canAccess(skill, currentUserId, userNsRoles)) {
            throw new DomainForbiddenException("error.skill.access.denied", skillSlug);
        }

        String latestVersion = null;
        SkillVersion latestVersionEntity = resolvePreviewVersion(skill, currentUserId);
        if (latestVersionEntity != null) {
            latestVersion = latestVersionEntity.getVersion();
        }

        return new SkillDetailDTO(
                skill.getId(),
                skill.getSlug(),
                skill.getDisplayName(),
                skill.getSummary(),
                skill.getVisibility().name(),
                skill.getStatus().name(),
                skill.getDownloadCount(),
                skill.getStarCount(),
                skill.getRatingAvg(),
                skill.getRatingCount(),
                skill.isHidden(),
                latestVersion,
                skill.getNamespaceId(),
                skill.getCreatedAt(),
                skill.getUpdatedAt(),
                latestVersionEntity,
                canManageRestrictedSkill(skill, currentUserId, userNsRoles),
                latestVersionEntity != null ? latestVersionEntity.getStatus().name() : null,
                latestVersionEntity == null || latestVersionEntity.getStatus() == SkillVersionStatus.PUBLISHED
        );
    }

    public Page<Skill> listSkillsByNamespace(
            String namespaceSlug,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles,
            Pageable pageable) {

        Namespace namespace = findNamespace(namespaceSlug);
        List<Skill> allSkills = skillRepository.findByNamespaceIdAndStatus(namespace.getId(), SkillStatus.ACTIVE);

        // Filter by visibility
        List<Skill> accessibleSkills = allSkills.stream()
                .filter(skill -> visibilityChecker.canAccess(skill, currentUserId, userNsRoles))
                .collect(Collectors.toList());

        // Manual pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), accessibleSkills.size());
        List<Skill> pageContent = accessibleSkills.subList(start, end);

        return new PageImpl<>(pageContent, pageable, accessibleSkills.size());
    }

    public SkillVersionDetailDTO getVersionDetail(
            String namespaceSlug,
            String skillSlug,
            String version,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {
        Skill skill = findSkill(namespaceSlug, skillSlug);
        assertPublishedAccessible(skill, currentUserId, userNsRoles);
        SkillVersion skillVersion = findVersion(skill, version);
        assertPreviewAccessible(skill, skillVersion, version, currentUserId);

        return new SkillVersionDetailDTO(
                skillVersion.getId(),
                skillVersion.getVersion(),
                skillVersion.getStatus().name(),
                skillVersion.getChangelog(),
                skillVersion.getFileCount(),
                skillVersion.getTotalSize(),
                skillVersion.getPublishedAt(),
                skillVersion.getParsedMetadataJson(),
                skillVersion.getManifestJson()
        );
    }

    public List<SkillFile> listFiles(
            String namespaceSlug,
            String skillSlug,
            String version,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {
        Skill skill = findSkill(namespaceSlug, skillSlug);
        assertPublishedAccessible(skill, currentUserId, userNsRoles);

        SkillVersion skillVersion = findVersion(skill, version);
        assertPreviewAccessible(skill, skillVersion, version, currentUserId);

        return skillFileRepository.findByVersionId(skillVersion.getId());
    }

    public List<SkillFile> listFilesByTag(
            String namespaceSlug,
            String skillSlug,
            String tagName,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {
        Skill skill = findSkill(namespaceSlug, skillSlug);
        assertPublishedAccessible(skill, currentUserId, userNsRoles);
        SkillVersion skillVersion = resolveVersionEntity(skill, null, tagName, null);
        return skillFileRepository.findByVersionId(skillVersion.getId());
    }

    public InputStream getFileContent(
            String namespaceSlug,
            String skillSlug,
            String version,
            String filePath,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {
        Skill skill = findSkill(namespaceSlug, skillSlug);
        assertPublishedAccessible(skill, currentUserId, userNsRoles);

        SkillVersion skillVersion = findVersion(skill, version);
        assertPreviewAccessible(skill, skillVersion, version, currentUserId);

        SkillFile file = findFile(skillVersion, filePath);

        return objectStorageService.getObject(file.getStorageKey());
    }

    public InputStream getFileContentByTag(
            String namespaceSlug,
            String skillSlug,
            String tagName,
            String filePath,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {
        Skill skill = findSkill(namespaceSlug, skillSlug);
        assertPublishedAccessible(skill, currentUserId, userNsRoles);
        SkillVersion skillVersion = resolveVersionEntity(skill, null, tagName, null);
        SkillFile file = findFile(skillVersion, filePath);
        return objectStorageService.getObject(file.getStorageKey());
    }

    public Page<SkillVersion> listVersions(String namespaceSlug,
                                           String skillSlug,
                                           String currentUserId,
                                           Map<Long, NamespaceRole> userNsRoles,
                                           Pageable pageable) {
        Skill skill = findSkill(namespaceSlug, skillSlug);
        assertPublishedAccessible(skill, currentUserId, userNsRoles);
        List<SkillVersion> visibleVersions;
        if (canManageRestrictedSkill(skill, currentUserId, userNsRoles)) {
            visibleVersions = skillVersionRepository.findBySkillId(skill.getId()).stream()
                    .filter(version -> version.getStatus() == SkillVersionStatus.PUBLISHED
                            || version.getStatus() == SkillVersionStatus.PENDING_REVIEW
                            || version.getStatus() == SkillVersionStatus.DRAFT
                            || version.getStatus() == SkillVersionStatus.REJECTED
                            || version.getStatus() == SkillVersionStatus.YANKED)
                    .sorted(Comparator
                            .comparingInt((SkillVersion version) -> lifecycleListPriority(version.getStatus()))
                            .thenComparing(SkillVersion::getPublishedAt,
                                    Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(SkillVersion::getCreatedAt,
                                    Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(SkillVersion::getId, Comparator.reverseOrder()))
                    .toList();
        } else {
            visibleVersions = skillVersionRepository.findBySkillIdAndStatus(
                    skill.getId(), SkillVersionStatus.PUBLISHED);
        }

        // Manual pagination
        int start = Math.min((int) pageable.getOffset(), visibleVersions.size());
        int end = Math.min(start + pageable.getPageSize(), visibleVersions.size());
        List<SkillVersion> pageContent = visibleVersions.subList(start, end);

        return new PageImpl<>(pageContent, pageable, visibleVersions.size());
    }

    public ResolvedVersionDTO resolveVersion(
            String namespaceSlug,
            String skillSlug,
            String version,
            String tag,
            String hash,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {
        if (version != null && !version.isBlank() && tag != null && !tag.isBlank()) {
            throw new DomainBadRequestException("error.skill.resolve.versionTag.conflict");
        }

        Skill skill = findSkill(namespaceSlug, skillSlug);
        assertPublishedAccessible(skill, currentUserId, userNsRoles);
        SkillVersion resolved = resolveVersionEntity(skill, version, tag, hash);
        String fingerprint = computeFingerprint(resolved);
        Boolean matched = hash == null || hash.isBlank() ? null : Objects.equals(hash, fingerprint);

        return new ResolvedVersionDTO(
                skill.getId(),
                namespaceSlug,
                skill.getSlug(),
                resolved.getVersion(),
                resolved.getId(),
                fingerprint,
                matched,
                String.format(
                        "/api/v1/skills/%s/%s/versions/%s/download",
                        encodePathSegment(namespaceSlug),
                        encodePathSegment(skill.getSlug()),
                        encodePathSegment(resolved.getVersion()))
        );
    }

    private Namespace findNamespace(String slug) {
        return namespaceRepository.findBySlug(slug)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", slug));
    }

    private Skill findSkill(String namespaceSlug, String skillSlug) {
        Namespace namespace = findNamespace(namespaceSlug);
        return skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", skillSlug));
    }

    private SkillVersion findVersion(Skill skill, String version) {
        return skillVersionRepository.findBySkillIdAndVersion(skill.getId(), version)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.notFound", version));
    }

    private SkillFile findFile(SkillVersion skillVersion, String filePath) {
        return skillFileRepository.findByVersionId(skillVersion.getId()).stream()
                .filter(f -> f.getFilePath().equals(filePath))
                .findFirst()
                .orElseThrow(() -> new DomainBadRequestException("error.skill.file.notFound", filePath));
    }

    private SkillVersion resolveVersionEntity(Skill skill, String version, String tag, String hash) {
        if (version != null && !version.isBlank()) {
            SkillVersion exactVersion = findVersion(skill, version);
            assertPublishedVersion(exactVersion, version);
            return exactVersion;
        }

        if (tag != null && !tag.isBlank()) {
            if ("latest".equalsIgnoreCase(tag)) {
                return resolveLatestVersion(skill);
            }
            SkillTag skillTag = skillTagRepository.findBySkillIdAndTagName(skill.getId(), tag)
                    .orElseThrow(() -> new DomainBadRequestException("error.skill.tag.notFound", tag));
            if (skillTag.getVersionId() == null) {
                throw new DomainBadRequestException("error.skill.tag.version.missing", tag);
            }
            SkillVersion taggedVersion = skillVersionRepository.findById(skillTag.getVersionId())
                    .orElseThrow(() -> new DomainBadRequestException("error.skill.tag.version.notFound", tag));
            assertPublishedVersion(taggedVersion, taggedVersion.getVersion());
            return taggedVersion;
        }

        List<SkillVersion> publishedVersions = skillVersionRepository.findBySkillIdAndStatus(
                skill.getId(), SkillVersionStatus.PUBLISHED);
        if (publishedVersions.isEmpty()) {
            throw new DomainBadRequestException("error.skill.version.latest.unavailable", skill.getSlug());
        }

        if (hash != null && !hash.isBlank()) {
            Optional<SkillVersion> matchedVersion = publishedVersions.stream()
                    .filter(candidate -> Objects.equals(hash, computeFingerprint(candidate)))
                    .findFirst();
            if (matchedVersion.isPresent()) {
                return matchedVersion.get();
            }
        }

        return resolveLatestVersion(skill);
    }

    private SkillVersion resolveLatestVersion(Skill skill) {
        if (skill.getLatestVersionId() == null) {
            throw new DomainBadRequestException("error.skill.version.latest.unavailable", skill.getSlug());
        }
        SkillVersion latestVersion = skillVersionRepository.findById(skill.getLatestVersionId())
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.latest.notFound"));
        assertPublishedVersion(latestVersion, latestVersion.getVersion());
        return latestVersion;
    }

    private SkillVersion resolvePreviewVersion(Skill skill, String currentUserId) {
        SkillVersion ownerPreview = resolveOwnerPendingPreview(skill, currentUserId);
        if (ownerPreview != null) {
            return ownerPreview;
        }
        if (skill.getLatestVersionId() == null) {
            return null;
        }
        return skillVersionRepository.findById(skill.getLatestVersionId()).orElse(null);
    }

    private SkillVersion resolveOwnerPendingPreview(Skill skill, String currentUserId) {
        if (!isOwner(skill, currentUserId)) {
            return null;
        }
        return skillVersionRepository.findBySkillIdAndStatus(skill.getId(), SkillVersionStatus.PENDING_REVIEW).stream()
                .max(Comparator
                        .comparing(SkillVersion::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SkillVersion::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private String computeFingerprint(SkillVersion version) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<SkillFile> files = skillFileRepository.findByVersionId(version.getId()).stream()
                    .sorted(Comparator.comparing(SkillFile::getFilePath))
                    .toList();
            for (SkillFile file : files) {
                String line = file.getFilePath() + ":" + file.getSha256() + "\n";
                digest.update(line.getBytes(StandardCharsets.UTF_8));
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute version fingerprint", e);
        }
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void assertPublishedAccessible(Skill skill, String currentUserId, Map<Long, NamespaceRole> userNsRoles) {
        if (skill.getStatus() != SkillStatus.ACTIVE && !canManageRestrictedSkill(skill, currentUserId, userNsRoles)) {
            throw new DomainForbiddenException("error.skill.access.denied", skill.getSlug());
        }
        if (skill.isHidden() && !canManageRestrictedSkill(skill, currentUserId, userNsRoles)) {
            throw new DomainForbiddenException("error.skill.access.denied", skill.getSlug());
        }
        if (!visibilityChecker.canAccess(skill, currentUserId, userNsRoles)) {
            throw new DomainForbiddenException("error.skill.access.denied", skill.getSlug());
        }
    }

    private boolean canManageRestrictedSkill(Skill skill, String currentUserId, Map<Long, NamespaceRole> userNsRoles) {
        if (currentUserId == null) {
            return false;
        }
        NamespaceRole role = userNsRoles.get(skill.getNamespaceId());
        return skill.getOwnerId().equals(currentUserId)
                || role == NamespaceRole.ADMIN
                || role == NamespaceRole.OWNER;
    }

    private boolean isOwner(Skill skill, String currentUserId) {
        return currentUserId != null && skill.getOwnerId().equals(currentUserId);
    }

    private int lifecycleListPriority(SkillVersionStatus status) {
        if (status == SkillVersionStatus.PUBLISHED) {
            return 0;
        }
        if (status == SkillVersionStatus.REJECTED) {
            return 1;
        }
        if (status == SkillVersionStatus.PENDING_REVIEW) {
            return 2;
        }
        if (status == SkillVersionStatus.DRAFT) {
            return 3;
        }
        if (status == SkillVersionStatus.YANKED) {
            return 4;
        }
        return 2;
    }

    private void assertPublishedVersion(SkillVersion version, String versionStr) {
        if (version.getStatus() != SkillVersionStatus.PUBLISHED) {
            throw new DomainBadRequestException("error.skill.version.notPublished", versionStr);
        }
    }

    private void assertPreviewAccessible(Skill skill, SkillVersion version, String versionStr, String currentUserId) {
        if (version.getStatus() == SkillVersionStatus.PUBLISHED) {
            return;
        }
        if (version.getStatus() == SkillVersionStatus.PENDING_REVIEW && isOwner(skill, currentUserId)) {
            return;
        }
        throw new DomainBadRequestException("error.skill.version.notPublished", versionStr);
    }
}
