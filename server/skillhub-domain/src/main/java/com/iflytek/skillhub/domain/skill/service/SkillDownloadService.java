package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.event.SkillDownloadedEvent;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.storage.ObjectMetadata;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

@Service
public class SkillDownloadService {

    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillTagRepository skillTagRepository;
    private final ObjectStorageService objectStorageService;
    private final VisibilityChecker visibilityChecker;
    private final ApplicationEventPublisher eventPublisher;

    public SkillDownloadService(
            NamespaceRepository namespaceRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            SkillTagRepository skillTagRepository,
            ObjectStorageService objectStorageService,
            VisibilityChecker visibilityChecker,
            ApplicationEventPublisher eventPublisher) {
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillTagRepository = skillTagRepository;
        this.objectStorageService = objectStorageService;
        this.visibilityChecker = visibilityChecker;
        this.eventPublisher = eventPublisher;
    }

    public record DownloadResult(
            InputStream content,
            String filename,
            long contentLength,
            String contentType,
            String presignedUrl
    ) {}

    public DownloadResult downloadLatest(
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

        if (skill.getLatestVersionId() == null) {
            throw new DomainBadRequestException("error.skill.version.latest.unavailable", skillSlug);
        }

        SkillVersion version = skillVersionRepository.findById(skill.getLatestVersionId())
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.latest.notFound"));

        return downloadVersion(skill, version);
    }

    public DownloadResult downloadVersion(
            String namespaceSlug,
            String skillSlug,
            String versionStr,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {

        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", skillSlug));

        // Visibility check
        if (!visibilityChecker.canAccess(skill, currentUserId, userNsRoles)) {
            throw new DomainForbiddenException("error.skill.access.denied", skillSlug);
        }

        SkillVersion version = skillVersionRepository.findBySkillIdAndVersion(skill.getId(), versionStr)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.notFound", versionStr));

        return downloadVersion(skill, version);
    }

    public DownloadResult downloadByTag(
            String namespaceSlug,
            String skillSlug,
            String tagName,
            String currentUserId,
            Map<Long, NamespaceRole> userNsRoles) {

        Namespace namespace = findNamespace(namespaceSlug);
        Skill skill = skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", skillSlug));

        // Visibility check
        if (!visibilityChecker.canAccess(skill, currentUserId, userNsRoles)) {
            throw new DomainForbiddenException("error.skill.access.denied", skillSlug);
        }

        SkillTag tag = skillTagRepository.findBySkillIdAndTagName(skill.getId(), tagName)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.tag.notFound", tagName));

        if (tag.getVersionId() == null) {
            throw new DomainBadRequestException("error.skill.tag.version.missing", tagName);
        }

        SkillVersion version = skillVersionRepository.findById(tag.getVersionId())
                .orElseThrow(() -> new DomainBadRequestException("error.skill.tag.version.notFound", tagName));

        return downloadVersion(skill, version);
    }

    private DownloadResult downloadVersion(Skill skill, SkillVersion version) {
        assertPublishedAccessible(skill);
        assertPublishedVersion(version);

        String storageKey = String.format("packages/%d/%d/bundle.zip", skill.getId(), version.getId());

        if (!objectStorageService.exists(storageKey)) {
            throw new DomainBadRequestException("error.skill.bundle.notFound");
        }

        ObjectMetadata metadata = objectStorageService.getMetadata(storageKey);
        String presignedUrl = objectStorageService.generatePresignedUrl(storageKey, Duration.ofMinutes(10));
        InputStream content = objectStorageService.getObject(storageKey);

        // Publish download event
        eventPublisher.publishEvent(new SkillDownloadedEvent(skill.getId(), version.getId()));

        String filename = String.format("%s-%s.zip", skill.getSlug(), version.getVersion());

        return new DownloadResult(content, filename, metadata.size(), metadata.contentType(), presignedUrl);
    }

    private Namespace findNamespace(String slug) {
        return namespaceRepository.findBySlug(slug)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", slug));
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
