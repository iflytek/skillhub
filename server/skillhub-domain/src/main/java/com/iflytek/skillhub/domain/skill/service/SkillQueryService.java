package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.patch.Patch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
    private final ReviewTaskRepository reviewTaskRepository;
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
            ReviewTaskRepository reviewTaskRepository,
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
        this.reviewTaskRepository = reviewTaskRepository;
        this.skillSlugResolutionService = skillSlugResolutionService;
        this.skillLifecycleProjectionService = skillLifecycleProjectionService;
        this.userAccountRepository = userAccountRepository;
    }

    public record SkillDetailDTO(
            Long id,
            String slug,
            String displayName,
            String ownerId,
            String ownerDisplayName,
            String summary,
            String visibility,
            String status,
            Long downloadCount,
            Integer starCount,
            Integer subscriptionCount,
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
            String ownerPreviewReviewComment,
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

    public record SkillVersionCompareDTO(
            String from,
            String to,
            SkillVersionCompareSummaryDTO summary,
            List<SkillVersionCompareFileDTO> files
    ) {}

    public record SkillVersionCompareSummaryDTO(
            int totalFiles,
            int addedFiles,
            int modifiedFiles,
            int removedFiles,
            int addedLines,
            int removedLines
    ) {}

    public record SkillVersionCompareFileDTO(
            String path,
            String changeType,
            Long oldSize,
            Long newSize,
            boolean binary,
            boolean truncated,
            List<SkillVersionCompareHunkDTO> hunks
    ) {}

    public record SkillVersionCompareHunkDTO(
            int oldStart,
            int oldLines,
            int newStart,
            int newLines,
            List<SkillVersionCompareLineDTO> lines
    ) {}

    public record SkillVersionCompareLineDTO(
            String type,
            String content,
            Integer oldLineNumber,
            Integer newLineNumber
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

        if (namespace.getStatus() == com.iflytek.skillhub.domain.namespace.NamespaceStatus.ARCHIVED
                && !isNamespaceMember(namespace.getId(), currentUserId, userNsRoles)) {
            throw new DomainForbiddenException("error.namespace.archived", namespaceSlug);
        }

        if (!visibilityChecker.canAccess(skill, currentUserId, userNsRoles)) {
            throw new DomainForbiddenException("error.skill.access.denied", skillSlug);
        }

        SkillLifecycleProjectionService.Projection projection =
                skillLifecycleProjectionService.projectForViewer(skill, currentUserId, userNsRoles);
        SkillLifecycleProjectionService.VersionProjection headlineVersion = projection.headlineVersion();
        SkillLifecycleProjectionService.VersionProjection publishedVersion = projection.publishedVersion();
        SkillLifecycleProjectionService.VersionProjection ownerPreviewVersion = projection.ownerPreviewVersion();
        String ownerPreviewReviewComment = resolveOwnerPreviewReviewComment(ownerPreviewVersion);
        String ownerDisplayName = userAccountRepository.findById(skill.getOwnerId())
                .map(UserAccount::getDisplayName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(null);

        return new SkillDetailDTO(
                skill.getId(),
                skill.getSlug(),
                skill.getDisplayName(),
                skill.getOwnerId(),
                ownerDisplayName,
                skill.getSummary(),
                skill.getVisibility().name(),
                skill.getStatus().name(),
                skill.getDownloadCount(),
                skill.getStarCount(),
                skill.getSubscriptionCount(),
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
                ownerPreviewReviewComment,
                projection.resolutionMode().name()
        );
    }

    public SkillDetailDTO getSkillDetail(
            String namespaceSlug,
            String skillSlug,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles,
            Set<String> platformRoles) {
        return getSkillDetail(namespaceSlug, skillSlug, currentUserId, userNsRoles);
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
        assertPreviewAccessible(skill, skillVersion, version, currentUserId, userNsRoles);

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

    public SkillVersionCompareDTO compareVersions(
            String namespaceSlug,
            String skillSlug,
            String fromVersion,
            String toVersion,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {
        if (Objects.equals(fromVersion, toVersion)) {
            throw new DomainBadRequestException("error.skill.version.compare.same");
        }

        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = resolveVisibleSkill(namespace.getId(), skillSlug, currentUserId);
        assertPublishedAccessible(namespace, skill, currentUserId, userNsRoles);

        SkillVersion from = findVersion(skill, fromVersion);
        SkillVersion to = findVersion(skill, toVersion);
        assertPreviewAccessible(skill, from, fromVersion, currentUserId, userNsRoles);
        assertPreviewAccessible(skill, to, toVersion, currentUserId, userNsRoles);

        Map<String, SkillFile> fromFiles = availableFiles(from.getId()).stream()
                .collect(Collectors.toMap(SkillFile::getFilePath, file -> file));
        Map<String, SkillFile> toFiles = availableFiles(to.getId()).stream()
                .collect(Collectors.toMap(SkillFile::getFilePath, file -> file));

        List<SkillVersionCompareFileDTO> files = new TreeSet<String>() {{
            addAll(fromFiles.keySet());
            addAll(toFiles.keySet());
        }}.stream()
                .map(path -> buildCompareFile(fromFiles.get(path), toFiles.get(path)))
                .filter(Objects::nonNull)
                .toList();

        int addedFiles = 0;
        int modifiedFiles = 0;
        int removedFiles = 0;
        int addedLines = 0;
        int removedLines = 0;
        for (SkillVersionCompareFileDTO file : files) {
            if ("ADDED".equals(file.changeType())) {
                addedFiles++;
            } else if ("REMOVED".equals(file.changeType())) {
                removedFiles++;
            } else {
                modifiedFiles++;
            }
            for (SkillVersionCompareHunkDTO hunk : file.hunks()) {
                for (SkillVersionCompareLineDTO line : hunk.lines()) {
                    if ("ADD".equals(line.type())) {
                        addedLines++;
                    } else if ("DELETE".equals(line.type())) {
                        removedLines++;
                    }
                }
            }
        }

        return new SkillVersionCompareDTO(
                fromVersion,
                toVersion,
                new SkillVersionCompareSummaryDTO(files.size(), addedFiles, modifiedFiles, removedFiles, addedLines, removedLines),
                files);
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
        assertPreviewAccessible(skill, skillVersion, version, currentUserId, userNsRoles);

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
        assertPreviewAccessible(skill, skillVersion, version, currentUserId, userNsRoles);

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
                            || version.getStatus() == SkillVersionStatus.UPLOADED
                            || version.getStatus() == SkillVersionStatus.DRAFT
                            || version.getStatus() == SkillVersionStatus.REJECTED
                            || version.getStatus() == SkillVersionStatus.YANKED
                            || version.getStatus() == SkillVersionStatus.SCANNING
                            || version.getStatus() == SkillVersionStatus.SCAN_FAILED)
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
                        || version.getStatus() == SkillVersionStatus.UPLOADED
                        || version.getStatus() == SkillVersionStatus.DRAFT
                        || version.getStatus() == SkillVersionStatus.REJECTED
                        || version.getStatus() == SkillVersionStatus.YANKED
                        || version.getStatus() == SkillVersionStatus.SCANNING
                        || version.getStatus() == SkillVersionStatus.SCAN_FAILED)
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

    private static final long COMPARE_MAX_FILE_BYTES = 1024 * 1024;
    private static final int COMPARE_MAX_LINES = 5000;
    private static final Set<String> BINARY_FILE_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".ico", ".woff", ".woff2", ".ttf", ".eot",
            ".zip", ".tar", ".gz", ".jar", ".war", ".class", ".so", ".dll", ".exe", ".pdf"
    );

    private SkillVersionCompareFileDTO buildCompareFile(SkillFile fromFile, SkillFile toFile) {
        if (fromFile != null && toFile != null && Objects.equals(fromFile.getSha256(), toFile.getSha256())) {
            return null;
        }

        String path = fromFile != null ? fromFile.getFilePath() : Objects.requireNonNull(toFile).getFilePath();
        String changeType = fromFile == null ? "ADDED" : toFile == null ? "REMOVED" : "MODIFIED";
        Long oldSize = fromFile != null ? fromFile.getFileSize() : null;
        Long newSize = toFile != null ? toFile.getFileSize() : null;
        boolean isBinary = isBinaryFile(path);
        if (isBinary) {
            return new SkillVersionCompareFileDTO(path, changeType, oldSize, newSize, true, false, List.of());
        }

        String oldContent = fromFile != null ? readTextContent(fromFile) : "";
        String newContent = toFile != null ? readTextContent(toFile) : "";
        List<String> oldLines = splitLines(oldContent);
        List<String> newLines = splitLines(newContent);
        boolean isTruncated = (oldSize != null && oldSize > COMPARE_MAX_FILE_BYTES)
                || (newSize != null && newSize > COMPARE_MAX_FILE_BYTES)
                || oldLines.size() > COMPARE_MAX_LINES
                || newLines.size() > COMPARE_MAX_LINES;
        if (isTruncated) {
            return new SkillVersionCompareFileDTO(path, changeType, oldSize, newSize, false, true, List.of());
        }

        Patch<String> patch = DiffUtils.diff(oldLines, newLines);
        List<SkillVersionCompareHunkDTO> hunks = patch.getDeltas().stream()
                .map(this::toCompareHunk)
                .toList();
        return new SkillVersionCompareFileDTO(path, changeType, oldSize, newSize, false, false, hunks);
    }

    private SkillVersionCompareHunkDTO toCompareHunk(AbstractDelta<String> delta) {
        Chunk<String> source = delta.getSource();
        Chunk<String> target = delta.getTarget();
        List<SkillVersionCompareLineDTO> lines = new java.util.ArrayList<>();

        int oldLine = source.getPosition() + 1;
        for (String line : source.getLines()) {
            lines.add(new SkillVersionCompareLineDTO("DELETE", line, oldLine++, null));
        }

        int newLine = target.getPosition() + 1;
        for (String line : target.getLines()) {
            lines.add(new SkillVersionCompareLineDTO("ADD", line, null, newLine++));
        }

        return new SkillVersionCompareHunkDTO(
                source.getPosition() + 1,
                source.size(),
                target.getPosition() + 1,
                target.size(),
                lines);
    }

    private List<String> splitLines(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(content.split("\\R", -1));
    }

    private boolean isBinaryFile(String path) {
        String lowerCasePath = path.toLowerCase(java.util.Locale.ROOT);
        return BINARY_FILE_EXTENSIONS.stream().anyMatch(lowerCasePath::endsWith);
    }

    private SkillVersion findVersion(Skill skill, String version) {
        return skillVersionRepository.findBySkillIdAndVersion(skill.getId(), version)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.notFound", version));
    }

    /**
     * Reads a single file from a skill version by its version ID.
     * Used by the review file reading endpoint where the caller has already
     * performed authorization checks.
     */
    public InputStream getFileContentByVersionId(Long versionId, String filePath) {
        SkillVersion version = skillVersionRepository.findById(versionId)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.notFound", versionId));
        SkillFile file = findFile(version, filePath);
        return readFileContent(file);
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

    private String resolveOwnerPreviewReviewComment(SkillLifecycleProjectionService.VersionProjection ownerPreviewVersion) {
        if (ownerPreviewVersion == null || !"REJECTED".equals(ownerPreviewVersion.status())) {
            return null;
        }
        return reviewTaskRepository.findBySkillVersionIdAndStatus(ownerPreviewVersion.id(), ReviewTaskStatus.REJECTED)
                .map(com.iflytek.skillhub.domain.review.ReviewTask::getReviewComment)
                .filter(comment -> comment != null && !comment.isBlank())
                .orElse(null);
    }

    private int lifecycleListPriority(SkillVersionStatus status) {
        if (status == SkillVersionStatus.PUBLISHED) {
            return 0;
        }
        if (status == SkillVersionStatus.SCANNING) {
            return 1;
        }
        if (status == SkillVersionStatus.SCAN_FAILED) {
            return 1;
        }
        if (status == SkillVersionStatus.UPLOADED) {
            return 2;
        }
        if (status == SkillVersionStatus.REJECTED) {
            return 3;
        }
        if (status == SkillVersionStatus.PENDING_REVIEW) {
            return 4;
        }
        if (status == SkillVersionStatus.DRAFT) {
            return 5;
        }
        if (status == SkillVersionStatus.YANKED) {
            return 5;
        }
        return 3;
    }

    private void assertPublishedVersion(SkillVersion version, String versionStr) {
        if (version.getStatus() != SkillVersionStatus.PUBLISHED) {
            throw new DomainBadRequestException("error.skill.version.notPublished", versionStr);
        }
    }

    /**
     * Checks whether the caller may preview a specific version's files and metadata.
     * Published versions are visible to everyone; all other statuses are restricted
     * to the skill owner or namespace admins so they can inspect rejected, draft,
     * or in-progress versions.
     */
    private void assertPreviewAccessible(Skill skill, SkillVersion version, String versionStr,
                                          String currentUserId, Map<Long, NamespaceRole> userNsRoles) {
        if (version.getStatus() == SkillVersionStatus.PUBLISHED) {
            return;
        }
        if (canManageRestrictedSkill(skill, currentUserId, userNsRoles)) {
            return;
        }
        throw new DomainBadRequestException("error.skill.version.notPublished", versionStr);
    }
}
