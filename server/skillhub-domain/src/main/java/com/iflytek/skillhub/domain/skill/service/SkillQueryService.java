package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.UncheckedIOException;
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

/**
 * Read-side domain service for skill detail, version browsing, and packaged
 * file inspection.
 *
 * <p>Unlike search, this service works from the authoritative skill model and
 * applies viewer-specific visibility rules before returning data.
 */
@Service
public class SkillQueryService {

    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillFileRepository skillFileRepository;
    private final SkillTagRepository skillTagRepository;
    private final ObjectStorageService objectStorageService;
    private final VisibilityChecker visibilityChecker;
    private final PromotionRequestRepository promotionRequestRepository;
    private final SkillSlugResolutionService skillSlugResolutionService;
    private final SkillLifecycleProjectionService skillLifecycleProjectionService;
    private final UserAccountRepository userAccountRepository;

    public SkillQueryService(
            NamespaceRepository namespaceRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            SkillFileRepository skillFileRepository,
            SkillTagRepository skillTagRepository,
            ObjectStorageService objectStorageService,
            VisibilityChecker visibilityChecker,
            PromotionRequestRepository promotionRequestRepository,
            SkillSlugResolutionService skillSlugResolutionService,
            SkillLifecycleProjectionService skillLifecycleProjectionService,
            UserAccountRepository userAccountRepository) {
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillFileRepository = skillFileRepository;
        this.skillTagRepository = skillTagRepository;
        this.objectStorageService = objectStorageService;
        this.visibilityChecker = visibilityChecker;
        this.promotionRequestRepository = promotionRequestRepository;
        this.skillSlugResolutionService = skillSlugResolutionService;
        this.skillLifecycleProjectionService = skillLifecycleProjectionService;
        this.userAccountRepository = userAccountRepository;
    }

    public record SkillDetailDTO(
            Long id,
            String slug,
            String displayName,
            String ownerDisplayName,
            String summary,
            String visibility,
            String status,
            Long downloadCount,
            Integer starCount,
            java.math.BigDecimal ratingAvg,
            Integer ratingCount,
            boolean hidden,
            Long namespaceId,
            java.time.Instant createdAt,
            java.time.Instant updatedAt,
            boolean canManageLifecycle,
            boolean canSubmitPromotion,
            boolean canInteract,
            boolean canReport,
            SkillLifecycleProjectionService.VersionProjection headlineVersion,
            SkillLifecycleProjectionService.VersionProjection publishedVersion,
            SkillLifecycleProjectionService.VersionProjection ownerPreviewVersion,
            String resolutionMode
    ) {}

    public record SkillVersionDetailDTO(
            Long id,
            String version,
            String status,
            String changelog,
            Integer fileCount,
            Long totalSize,
            java.time.Instant publishedAt,
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

    public record ReviewSkillSnapshotDTO(
            Skill skill,
            String ownerDisplayName,
            SkillVersion activeVersion,
            SkillVersion publishedVersion,
            List<SkillVersion> versions,
            List<SkillFile> files,
            String documentationPath,
            String documentationContent
    ) {}

    public SkillDetailDTO getSkillDetail(
            String namespaceSlug,
            String skillSlug,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {

        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = resolveVisibleSkill(namespace.getId(), skillSlug, currentUserId);

        // Visibility check
        if (!visibilityChecker.canAccess(skill, currentUserId, userNsRoles)) {
            throw new DomainForbiddenException("error.skill.access.denied", skillSlug);
        }

        SkillLifecycleProjectionService.Projection projection =
                skillLifecycleProjectionService.projectForViewer(skill, currentUserId, userNsRoles);
        SkillLifecycleProjectionService.VersionProjection headlineVersion = projection.headlineVersion();
        SkillLifecycleProjectionService.VersionProjection publishedVersion = projection.publishedVersion();
        SkillLifecycleProjectionService.VersionProjection ownerPreviewVersion = projection.ownerPreviewVersion();
        String ownerDisplayName = userAccountRepository.findById(skill.getOwnerId())
                .map(UserAccount::getDisplayName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(null);

        return new SkillDetailDTO(
                skill.getId(),
                skill.getSlug(),
                skill.getDisplayName(),
                ownerDisplayName,
                skill.getSummary(),
                skill.getVisibility().name(),
                skill.getStatus().name(),
                skill.getDownloadCount(),
                skill.getStarCount(),
                skill.getRatingAvg(),
                skill.getRatingCount(),
                skill.isHidden(),
                skill.getNamespaceId(),
                skill.getCreatedAt(),
                skill.getUpdatedAt(),
                canManageRestrictedSkill(skill, currentUserId, userNsRoles),
                canSubmitPromotion(namespace, skill, publishedVersion, currentUserId, userNsRoles),
                headlineVersion == null || "PUBLISHED".equals(headlineVersion.status()),
                currentUserId == null || !Objects.equals(skill.getOwnerId(), currentUserId),
                headlineVersion,
                publishedVersion,
                ownerPreviewVersion,
                projection.resolutionMode().name()
        );
    }

    /**
     * Lists skills within a namespace after filtering out records the caller is
     * not allowed to discover.
     */
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

    /**
     * Returns metadata for a visible version, including the stored manifest and
     * parsed metadata payload.
     */
    public SkillVersionDetailDTO getVersionDetail(
            String namespaceSlug,
            String skillSlug,
            String version,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {
        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = resolveVisibleSkill(namespace.getId(), skillSlug, currentUserId);
        assertPublishedAccessible(namespace, skill, currentUserId, userNsRoles);
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
        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = resolveVisibleSkill(namespace.getId(), skillSlug, currentUserId);
        assertPublishedAccessible(namespace, skill, currentUserId, userNsRoles);

        SkillVersion skillVersion = findVersion(skill, version);
        assertPreviewAccessible(skill, skillVersion, version, currentUserId);

        return availableFiles(skillVersion.getId());
    }

    public List<SkillFile> listFilesByTag(
            String namespaceSlug,
            String skillSlug,
            String tagName,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {
        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = resolveVisibleSkill(namespace.getId(), skillSlug, currentUserId);
        assertPublishedAccessible(namespace, skill, currentUserId, userNsRoles);
        SkillVersion skillVersion = resolveVersionEntity(skill, null, tagName, null);
        return availableFiles(skillVersion.getId());
    }

    /**
     * Opens a single file stream from object storage after verifying that the
     * caller may inspect the requested version.
     */
    public InputStream getFileContent(
            String namespaceSlug,
            String skillSlug,
            String version,
            String filePath,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {
        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = resolveVisibleSkill(namespace.getId(), skillSlug, currentUserId);
        assertPublishedAccessible(namespace, skill, currentUserId, userNsRoles);

        SkillVersion skillVersion = findVersion(skill, version);
        assertPreviewAccessible(skill, skillVersion, version, currentUserId);

        SkillFile file = findFile(skillVersion, filePath);

        return readFileContent(file);
    }

    public InputStream getFileContentByTag(
            String namespaceSlug,
            String skillSlug,
            String tagName,
            String filePath,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {
        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = resolveVisibleSkill(namespace.getId(), skillSlug, currentUserId);
        assertPublishedAccessible(namespace, skill, currentUserId, userNsRoles);
        SkillVersion skillVersion = resolveVersionEntity(skill, null, tagName, null);
        SkillFile file = findFile(skillVersion, filePath);
        return readFileContent(file);
    }

    public Page<SkillVersion> listVersions(String namespaceSlug,
                                           String skillSlug,
                                           String currentUserId,
                                           Map<Long, NamespaceRole> userNsRoles,
                                           Pageable pageable) {
        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = resolveVisibleSkill(namespace.getId(), skillSlug, currentUserId);
        assertPublishedAccessible(namespace, skill, currentUserId, userNsRoles);
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

    public boolean isDownloadAvailable(SkillVersion version) {
        if (version == null) {
            return false;
        }
        if (version.getStatus() != SkillVersionStatus.PUBLISHED) {
            return false;
        }
        return version.isDownloadReady();
    }

    public ReviewSkillSnapshotDTO getReviewSkillSnapshot(Long skillVersionId) {
        SkillVersion activeVersion = skillVersionRepository.findById(skillVersionId)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.notFound", skillVersionId));
        Skill skill = skillRepository.findById(activeVersion.getSkillId())
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", activeVersion.getSkillId()));
        String ownerDisplayName = userAccountRepository.findById(skill.getOwnerId())
                .map(UserAccount::getDisplayName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(null);

        List<SkillVersion> versions = skillVersionRepository.findBySkillId(skill.getId()).stream()
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
        List<SkillFile> files = availableFiles(activeVersion.getId());
        String documentationPath = resolveDocumentationPath(files);
        String documentationContent = documentationPath == null
                ? null
                : readTextContent(findFile(activeVersion, documentationPath));
        SkillVersion publishedVersion = versions.stream()
                .filter(version -> version.getStatus() == SkillVersionStatus.PUBLISHED)
                .findFirst()
                .orElse(null);

        return new ReviewSkillSnapshotDTO(
                skill,
                ownerDisplayName,
                activeVersion,
                publishedVersion,
                versions,
                files,
                documentationPath,
                documentationContent
        );
    }

    /**
     * Resolves a version selector such as an exact version, tag, or implicit
     * latest reference into a concrete download target.
     */
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

        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = resolveVisibleSkill(namespace.getId(), skillSlug, currentUserId);
        assertPublishedAccessible(namespace, skill, currentUserId, userNsRoles);
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

    private Skill findSkill(String namespaceSlug, String skillSlug, String currentUserId) {
        Namespace namespace = findNamespace(namespaceSlug);
        return resolveVisibleSkill(namespace.getId(), skillSlug, currentUserId);
    }

    private Skill resolveVisibleSkill(Long namespaceId, String slug, String currentUserId) {
        return skillSlugResolutionService.resolve(
                namespaceId,
                slug,
                currentUserId,
                SkillSlugResolutionService.Preference.CURRENT_USER);
    }

    private SkillVersion findVersion(Skill skill, String version) {
        return skillVersionRepository.findBySkillIdAndVersion(skill.getId(), version)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.notFound", version));
    }

    private SkillFile findFile(SkillVersion skillVersion, String filePath) {
        return availableFiles(skillVersion.getId()).stream()
                .filter(f -> f.getFilePath().equals(filePath))
                .findFirst()
                .orElseThrow(() -> new DomainBadRequestException("error.skill.file.notFound", filePath));
    }

    private List<SkillFile> availableFiles(Long versionId) {
        return skillFileRepository.findByVersionId(versionId).stream()
                .filter(file -> objectStorageService.exists(file.getStorageKey()))
                .toList();
    }

    private String getBundleStorageKey(Long skillId, Long versionId) {
        return String.format("packages/%d/%d/bundle.zip", skillId, versionId);
    }

    private InputStream readFileContent(SkillFile file) {
        try {
            return objectStorageService.getObject(file.getStorageKey());
        } catch (UncheckedIOException e) {
            throw new DomainBadRequestException("error.skill.file.notFound", file.getFilePath());
        }
    }

    private String readTextContent(SkillFile file) {
        try (InputStream inputStream = readFileContent(file)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read skill file content", e);
        }
    }

    private String resolveDocumentationPath(List<SkillFile> files) {
        if (files.isEmpty()) {
            return null;
        }
        List<String> filePaths = files.stream().map(SkillFile::getFilePath).toList();
        if (filePaths.contains("README.md")) {
            return "README.md";
        }
        if (filePaths.contains("SKILL.md")) {
            return "SKILL.md";
        }
        return null;
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

    private void assertPublishedAccessible(
            Namespace namespace,
            Skill skill,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {
        if (namespace.getStatus() == NamespaceStatus.ARCHIVED && !isNamespaceMember(skill.getNamespaceId(), currentUserId, userNsRoles)) {
            throw new DomainForbiddenException("error.namespace.archived", namespace.getSlug());
        }
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

    private boolean canSubmitPromotion(
            Namespace namespace,
            Skill skill,
            SkillLifecycleProjectionService.VersionProjection publishedVersion,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {
        if (namespace.getType() == NamespaceType.GLOBAL) {
            return false;
        }
        if (namespace.getStatus() != NamespaceStatus.ACTIVE || skill.getStatus() != SkillStatus.ACTIVE) {
            return false;
        }
        if (publishedVersion == null || !"PUBLISHED".equals(publishedVersion.status())) {
            return false;
        }
        if (promotionRequestRepository.findBySourceSkillIdAndStatus(skill.getId(), ReviewTaskStatus.PENDING).isPresent()) {
            return false;
        }
        if (promotionRequestRepository.findBySourceSkillIdAndStatus(skill.getId(), ReviewTaskStatus.APPROVED).isPresent()) {
            return false;
        }
        return canManageRestrictedSkill(skill, currentUserId, userNsRoles);
    }

    private boolean isOwner(Skill skill, String currentUserId) {
        return currentUserId != null && skill.getOwnerId().equals(currentUserId);
    }

    private boolean isNamespaceMember(Long namespaceId, String currentUserId, Map<Long, NamespaceRole> userNsRoles) {
        return currentUserId != null && userNsRoles.containsKey(namespaceId);
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
