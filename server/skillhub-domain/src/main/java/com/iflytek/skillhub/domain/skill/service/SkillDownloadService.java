package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.event.SkillDownloadedEvent;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.storage.ObjectMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Domain service that delivers packaged skills to callers.
 *
 * <p>It combines visibility checks, version resolution, object-storage access,
 * and download tracking into a single download-oriented API.
 */
@Service
public class SkillDownloadService {
    private static final Logger log = LoggerFactory.getLogger(SkillDownloadService.class);

    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillVersionStatsRepository skillVersionStatsRepository;
    private final SkillFileRepository skillFileRepository;
    private final SkillTagRepository skillTagRepository;
    private final ObjectStorageService objectStorageService;
    private final VisibilityChecker visibilityChecker;
    private final ApplicationEventPublisher eventPublisher;
    private final SkillSlugResolutionService skillSlugResolutionService;

    public SkillDownloadService(
            NamespaceRepository namespaceRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            SkillVersionStatsRepository skillVersionStatsRepository,
            SkillFileRepository skillFileRepository,
            SkillTagRepository skillTagRepository,
            ObjectStorageService objectStorageService,
            VisibilityChecker visibilityChecker,
            ApplicationEventPublisher eventPublisher,
            SkillSlugResolutionService skillSlugResolutionService) {
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillVersionStatsRepository = skillVersionStatsRepository;
        this.skillFileRepository = skillFileRepository;
        this.skillTagRepository = skillTagRepository;
        this.objectStorageService = objectStorageService;
        this.visibilityChecker = visibilityChecker;
        this.eventPublisher = eventPublisher;
        this.skillSlugResolutionService = skillSlugResolutionService;
    }

    public record DownloadResult(
            Supplier<InputStream> contentSupplier,
            String filename,
            long contentLength,
            String contentType,
            String presignedUrl,
            boolean fallbackBundle
    ) {
        public InputStream openContent() {
            return contentSupplier.get();
        }
    }

    /**
     * Downloads the latest published version available to the caller.
     */
    public DownloadResult downloadLatest(
            String namespaceSlug,
            String skillSlug,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {

        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = resolveVisibleSkill(namespace.getId(), skillSlug, currentUserId);
        assertCanDownload(namespace, skill, currentUserId, userNsRoles);

        if (skill.getLatestVersionId() == null) {
            throw new DomainBadRequestException("error.skill.version.latest.unavailable", skillSlug);
        }

        SkillVersion version = skillVersionRepository.findById(skill.getLatestVersionId())
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.latest.notFound"));

        return downloadVersion(skill, version);
    }

    /**
     * Downloads an explicit version when the caller has permission to access
     * the containing skill.
     */
    public DownloadResult downloadVersion(
            String namespaceSlug,
            String skillSlug,
            String versionStr,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {

        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = resolveVisibleSkill(namespace.getId(), skillSlug, currentUserId);
        assertCanDownload(namespace, skill, currentUserId, userNsRoles);

        SkillVersion version = skillVersionRepository.findBySkillIdAndVersion(skill.getId(), versionStr)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.notFound", versionStr));

        return downloadVersion(skill, version);
    }

    /**
     * Downloads the version pointed to by a mutable tag name.
     */
    public DownloadResult downloadByTag(
            String namespaceSlug,
            String skillSlug,
            String tagName,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {

        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = resolveVisibleSkill(namespace.getId(), skillSlug, currentUserId);
        assertCanDownload(namespace, skill, currentUserId, userNsRoles);

        SkillTag tag = skillTagRepository.findBySkillIdAndTagName(skill.getId(), tagName)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.tag.notFound", tagName));

        if (tag.getVersionId() == null) {
            throw new DomainBadRequestException("error.skill.tag.version.missing", tagName);
        }

        SkillVersion version = skillVersionRepository.findById(tag.getVersionId())
                .orElseThrow(() -> new DomainBadRequestException("error.skill.tag.version.notFound", tagName));

        return downloadVersion(skill, version);
    }

    /**
     * Downloads a review-bound package without enforcing public-version
     * eligibility and without recording public download metrics.
     */
    public DownloadResult downloadReviewVersion(Skill skill, SkillVersion version) {
        return buildDownloadResult(skill, version);
    }

    private DownloadResult downloadVersion(Skill skill, SkillVersion version) {
        assertPublishedAccessible(skill);
        assertPublishedVersion(version);
        DownloadResult result = buildDownloadResult(skill, version);

        skillRepository.incrementDownloadCount(skill.getId());
        skillVersionStatsRepository.incrementDownloadCount(version.getId(), skill.getId());
        eventPublisher.publishEvent(new SkillDownloadedEvent(skill.getId(), version.getId()));
        return result;
    }

    private DownloadResult buildDownloadResult(Skill skill, SkillVersion version) {

        String storageKey = String.format("packages/%d/%d/bundle.zip", skill.getId(), version.getId());

        DownloadResult result;
        if (objectStorageService.exists(storageKey)) {
            ObjectMetadata metadata = objectStorageService.getMetadata(storageKey);
            String filename = buildFilename(skill, version);
            String presignedUrl = objectStorageService.generatePresignedUrl(storageKey, Duration.ofMinutes(10), filename);
            result = new DownloadResult(
                    () -> objectStorageService.getObject(storageKey),
                    filename,
                    metadata.size(),
                    metadata.contentType(),
                    presignedUrl,
                    false
            );
        } else {
            log.warn(
                    "Bundle missing for published version, falling back to per-file zip [skillId={}, versionId={}, version={}]",
                    skill.getId(),
                    version.getId(),
                    version.getVersion()
            );
            result = buildBundleFromFiles(skill, version);
        }
        return result;
    }

    private DownloadResult buildBundleFromFiles(Skill skill, SkillVersion version) {
        List<SkillFile> files = skillFileRepository.findByVersionId(version.getId()).stream()
                .filter(file -> objectStorageService.exists(file.getStorageKey()))
                .sorted(Comparator.comparing(SkillFile::getFilePath))
                .toList();
        if (files.isEmpty()) {
            throw new DomainBadRequestException("error.skill.bundle.notFound");
        }

        byte[] bundle = createBundle(files);
        return new DownloadResult(
                () -> new ByteArrayInputStream(bundle),
                buildFilename(skill, version),
                bundle.length,
                "application/zip",
                null,
                true
        );
    }

    private byte[] createBundle(List<SkillFile> files) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (SkillFile file : files) {
                ZipEntry zipEntry = new ZipEntry(file.getFilePath());
                zipOutputStream.putNextEntry(zipEntry);
                try (InputStream inputStream = objectStorageService.getObject(file.getStorageKey())) {
                    inputStream.transferTo(zipOutputStream);
                }
                zipOutputStream.closeEntry();
            }
            zipOutputStream.finish();
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build fallback bundle zip", e);
        }
    }

    private String buildFilename(Skill skill, SkillVersion version) {
        String baseName = skill.getDisplayName();
        if (baseName == null || baseName.isBlank()) {
            baseName = skill.getSlug();
        }
        return String.format("%s-%s.zip", sanitizeFilename(baseName), version.getVersion());
    }

    private String sanitizeFilename(String value) {
        String sanitized = value
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-")
                .replaceAll("\\s+", " ")
                .trim();
        return sanitized.isBlank() ? "skill" : sanitized;
    }

    private Namespace findNamespace(String slug) {
        return namespaceRepository.findBySlug(slug)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", slug));
    }

    private void assertCanDownload(Namespace namespace,
                                   Skill skill,
                                   String currentUserId,
                                   Map<Long, NamespaceRole> userNsRoles) {
        if (currentUserId == null && !isAnonymousDownloadAllowed(namespace, skill)) {
            throw new DomainForbiddenException("error.skill.access.denied", skill.getSlug());
        }
        if (!visibilityChecker.canAccess(skill, currentUserId, userNsRoles)) {
            throw new DomainForbiddenException("error.skill.access.denied", skill.getSlug());
        }
    }

    private boolean isAnonymousDownloadAllowed(Namespace namespace, Skill skill) {
        return namespace.getType() == NamespaceType.GLOBAL
                && skill.getVisibility() == SkillVisibility.PUBLIC;
    }

    private Skill resolveVisibleSkill(Long namespaceId, String slug, String currentUserId) {
        return skillSlugResolutionService.resolve(
                namespaceId,
                slug,
                currentUserId,
                SkillSlugResolutionService.Preference.CURRENT_USER);
    }

    private void assertPublishedAccessible(Skill skill) {
        if (skill.getStatus() != SkillStatus.ACTIVE) {
            throw new DomainBadRequestException("error.skill.status.notActive");
        }
    }

    private void assertPublishedVersion(SkillVersion version) {
        if (version.getStatus() != SkillVersionStatus.PUBLISHED) {
            throw new DomainBadRequestException("error.skill.version.notPublished", version.getVersion());
        }
    }
}
