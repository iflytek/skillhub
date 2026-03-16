package com.iflytek.skillhub.domain.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.SlugValidator;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.PrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.yaml.snakeyaml.Yaml;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class SkillPublishService {

    public record PublishResult(
            Long skillId,
            String slug,
            SkillVersion version
    ) {}

    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillFileRepository skillFileRepository;
    private final ObjectStorageService objectStorageService;
    private final SkillPackageValidator skillPackageValidator;
    private final SkillMetadataParser skillMetadataParser;
    private final PrePublishValidator prePublishValidator;
    private final ObjectMapper objectMapper;
    private final ReviewTaskRepository reviewTaskRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SkillPublishService(
            NamespaceRepository namespaceRepository,
            NamespaceMemberRepository namespaceMemberRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            SkillFileRepository skillFileRepository,
            ObjectStorageService objectStorageService,
            SkillPackageValidator skillPackageValidator,
            SkillMetadataParser skillMetadataParser,
            PrePublishValidator prePublishValidator,
            ObjectMapper objectMapper,
            ReviewTaskRepository reviewTaskRepository,
            ApplicationEventPublisher eventPublisher) {
        this.namespaceRepository = namespaceRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillFileRepository = skillFileRepository;
        this.objectStorageService = objectStorageService;
        this.skillPackageValidator = skillPackageValidator;
        this.skillMetadataParser = skillMetadataParser;
        this.prePublishValidator = prePublishValidator;
        this.objectMapper = objectMapper;
        this.reviewTaskRepository = reviewTaskRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PublishResult publishFromEntries(
            String namespaceSlug,
            List<PackageEntry> entries,
            String publisherId,
            SkillVisibility visibility,
            java.util.Set<String> platformRoles) {
        return publishFromEntriesInternal(namespaceSlug, entries, publisherId, visibility, platformRoles, false, false);
    }

    @Transactional
    public PublishResult rereleasePublishedVersion(
            Long skillId,
            String sourceVersion,
            String targetVersion,
            String publisherId,
            Map<Long, NamespaceRole> userNamespaceRoles) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", skillId));
        assertCanManageLifecycle(skill, publisherId, userNamespaceRoles);

        SkillVersion publishedVersion = skillVersionRepository.findBySkillIdAndVersion(skillId, sourceVersion)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.notFound", sourceVersion));
        if (publishedVersion.getStatus() != SkillVersionStatus.PUBLISHED) {
            throw new DomainBadRequestException("error.skill.version.notPublished", sourceVersion);
        }
        if (skillVersionRepository.findBySkillIdAndVersion(skillId, targetVersion).isPresent()) {
            throw new DomainBadRequestException("error.skill.version.exists", targetVersion);
        }

        List<PackageEntry> entries = rebuildEntriesForRerelease(skillId, publishedVersion.getId(), targetVersion);

        return publishFromEntriesInternal(
                resolveNamespaceSlug(skill.getNamespaceId()),
                entries,
                publisherId,
                skill.getVisibility(),
                Set.of(),
                true,
                true
        );
    }

    private PublishResult publishFromEntriesInternal(
            String namespaceSlug,
            List<PackageEntry> entries,
            String publisherId,
            SkillVisibility visibility,
            Set<String> platformRoles,
            boolean forceAutoPublish,
            boolean bypassMembershipCheck) {

        // 1. Find namespace by slug
        Namespace namespace = namespaceRepository.findBySlug(namespaceSlug)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", namespaceSlug));
        assertNamespaceWritable(namespace);

        boolean isSuperAdmin = platformRoles.contains("SUPER_ADMIN");

        // 2. Check publisher is member unless SUPER_ADMIN short-circuits permission checks
        if (!isSuperAdmin && !bypassMembershipCheck) {
            namespaceMemberRepository.findByNamespaceIdAndUserId(namespace.getId(), publisherId)
                    .orElseThrow(() -> new DomainBadRequestException("error.skill.publish.publisher.notMember", namespaceSlug));
        }

        // 3. Validate package
        ValidationResult packageValidation = skillPackageValidator.validate(entries);
        if (!packageValidation.passed()) {
            throw new DomainBadRequestException(
                    "error.skill.publish.package.invalid",
                    String.join(", ", packageValidation.errors()));
        }

        // 4. Parse SKILL.md
        PackageEntry skillMd = entries.stream()
                .filter(e -> e.path().equals("SKILL.md"))
                .findFirst()
                .orElseThrow(() -> new DomainBadRequestException("error.skill.publish.skillMd.notFound"));

        String skillMdContent = new String(skillMd.content());
        SkillMetadata metadata = skillMetadataParser.parse(skillMdContent);
        if (metadata.version() == null || metadata.version().isBlank()) {
            String autoVersion = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss"));
            metadata = new SkillMetadata(metadata.name(), metadata.description(), autoVersion, metadata.body(), metadata.frontmatter());
        }
        String skillSlug = SlugValidator.slugify(metadata.name());

        // 5. Run PrePublishValidator
        PrePublishValidator.SkillPackageContext context = new PrePublishValidator.SkillPackageContext(
                entries, metadata, publisherId, namespace.getId());
        ValidationResult prePublishValidation = prePublishValidator.validate(context);
        if (!prePublishValidation.passed()) {
            throw new DomainBadRequestException(
                    "error.skill.publish.precheck.failed",
                    String.join(", ", prePublishValidation.errors()));
        }

        // 6. Find or create Skill record (with owner isolation)
        List<Skill> existingSkills = skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug);

        // Check if any other owner's skill has published versions
        for (Skill existing : existingSkills) {
            if (!existing.getOwnerId().equals(publisherId)) {
                boolean hasPublished = !skillVersionRepository
                        .findBySkillIdAndStatus(existing.getId(), SkillVersionStatus.PUBLISHED)
                        .isEmpty();
                if (hasPublished) {
                    throw new DomainBadRequestException("error.skill.publish.nameConflict", skillSlug);
                }
            }
        }

        // Find or create skill for current user
        Skill skill = skillRepository.findByNamespaceIdAndSlugAndOwnerId(namespace.getId(), skillSlug, publisherId)
                .orElseGet(() -> {
                    Skill newSkill = new Skill(namespace.getId(), skillSlug, publisherId, visibility);
                    newSkill.setCreatedBy(publisherId);
                    return skillRepository.save(newSkill);
                });

        if (skill.getStatus() == SkillStatus.ARCHIVED) {
            throw new DomainBadRequestException("error.skill.publish.archived", skillSlug);
        }

        // 6c. Auto-withdraw pending review versions
        List<SkillVersion> pendingVersions = skillVersionRepository
                .findBySkillIdAndStatus(skill.getId(), SkillVersionStatus.PENDING_REVIEW);
        for (SkillVersion pending : pendingVersions) {
            pending.setStatus(SkillVersionStatus.DRAFT);
            skillVersionRepository.save(pending);
        }

        // 7. Check version doesn't already exist
        if (skillVersionRepository.findBySkillIdAndVersion(skill.getId(), metadata.version()).isPresent()) {
            throw new DomainBadRequestException("error.skill.version.exists", metadata.version());
        }

        // 8. Create SkillVersion
        SkillVersion version = new SkillVersion(skill.getId(), metadata.version(), publisherId);
        boolean autoPublish = forceAutoPublish || isSuperAdmin;
        if (autoPublish) {
            version.setStatus(SkillVersionStatus.PUBLISHED);
            version.setPublishedAt(LocalDateTime.now());
        } else {
            version.setStatus(SkillVersionStatus.PENDING_REVIEW);
        }

        // Store metadata as JSON
        try {
            String metadataJson = objectMapper.writeValueAsString(metadata);
            version.setParsedMetadataJson(metadataJson);
            version.setManifestJson(objectMapper.writeValueAsString(buildManifest(entries)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize metadata", e);
        }

        version = skillVersionRepository.save(version);

        // 9. Upload each file to storage and compute SHA-256
        List<SkillFile> skillFiles = new ArrayList<>();
        long totalSize = 0;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            HexFormat hexFormat = HexFormat.of();

            for (PackageEntry entry : entries) {
                String storageKey = String.format("skills/%d/%d/%s", skill.getId(), version.getId(), entry.path());

                // Upload to storage
                objectStorageService.putObject(
                        storageKey,
                        new ByteArrayInputStream(entry.content()),
                        entry.size(),
                        entry.contentType()
                );

                // Compute SHA-256
                byte[] hash = digest.digest(entry.content());
                String sha256 = hexFormat.formatHex(hash);

                // Create SkillFile record
                SkillFile skillFile = new SkillFile(
                        version.getId(),
                        entry.path(),
                        entry.size(),
                        entry.contentType(),
                        sha256,
                        storageKey
                );
                skillFiles.add(skillFile);
                totalSize += entry.size();

                digest.reset();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to process files", e);
        }

        // 10. Save SkillFile records
        skillFileRepository.saveAll(skillFiles);

        // 10.5 Build and upload bundle zip for download endpoints
        byte[] bundleZip = buildBundle(entries);
        String bundleKey = String.format("packages/%d/%d/bundle.zip", skill.getId(), version.getId());
        objectStorageService.putObject(
                bundleKey,
                new ByteArrayInputStream(bundleZip),
                bundleZip.length,
                "application/zip"
        );

        // 11. Update version stats
        version.setFileCount(skillFiles.size());
        version.setTotalSize(totalSize);
        skillVersionRepository.save(version);

        if (!autoPublish) {
            ReviewTask reviewTask = new ReviewTask(version.getId(), namespace.getId(), publisherId);
            reviewTaskRepository.save(reviewTask);
        }

        // 12. Update skill metadata and move the published pointer for auto-publish flows
        skill.setDisplayName(metadata.name());
        skill.setSummary(metadata.description());
        if (autoPublish) {
            skill.setLatestVersionId(version.getId());
        }
        skill.setUpdatedBy(publisherId);
        skillRepository.save(skill);

        if (autoPublish) {
            eventPublisher.publishEvent(new SkillPublishedEvent(skill.getId(), version.getId(), publisherId));
        }

        // 13. Return identifiers for the created version
        return new PublishResult(skill.getId(), skill.getSlug(), version);
    }

    private String resolveNamespaceSlug(Long namespaceId) {
        return namespaceRepository.findById(namespaceId)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.notFound", namespaceId))
                .getSlug();
    }

    private void assertNamespaceWritable(Namespace namespace) {
        if (namespace.getStatus() == NamespaceStatus.FROZEN) {
            throw new DomainBadRequestException("error.namespace.frozen", namespace.getSlug());
        }
        if (namespace.getStatus() == NamespaceStatus.ARCHIVED) {
            throw new DomainBadRequestException("error.namespace.archived", namespace.getSlug());
        }
    }

    private void assertCanManageLifecycle(Skill skill,
                                          String actorUserId,
                                          Map<Long, NamespaceRole> userNamespaceRoles) {
        NamespaceRole namespaceRole = userNamespaceRoles.get(skill.getNamespaceId());
        boolean canManage = skill.getOwnerId().equals(actorUserId)
                || namespaceRole == NamespaceRole.ADMIN
                || namespaceRole == NamespaceRole.OWNER;
        if (!canManage) {
            throw new DomainForbiddenException("error.skill.lifecycle.noPermission");
        }
    }

    private List<PackageEntry> rebuildEntriesForRerelease(Long skillId, Long versionId, String targetVersion) {
        List<SkillFile> files = skillFileRepository.findByVersionId(versionId).stream()
                .sorted(Comparator.comparing(SkillFile::getFilePath))
                .toList();
        List<PackageEntry> entries = new ArrayList<>(files.size());
        for (SkillFile file : files) {
            byte[] content = readAllBytes(objectStorageService.getObject(file.getStorageKey()));
            if ("SKILL.md".equals(file.getFilePath())) {
                content = rewriteSkillMdVersion(content, targetVersion);
            }
            entries.add(new PackageEntry(
                    file.getFilePath(),
                    content,
                    content.length,
                    file.getContentType() != null ? file.getContentType() : "application/octet-stream"
            ));
        }
        return entries;
    }

    private byte[] readAllBytes(InputStream inputStream) {
        try (InputStream in = inputStream) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read stored skill file", e);
        }
    }

    private byte[] rewriteSkillMdVersion(byte[] content, String targetVersion) {
        String skillMdContent = new String(content);
        SkillMetadata metadata = skillMetadataParser.parse(skillMdContent);
        Map<String, Object> frontmatter = new LinkedHashMap<>(metadata.frontmatter());
        frontmatter.put("version", targetVersion);
        String rewritten = "---\n"
                + new Yaml().dump(frontmatter).trim()
                + "\n---\n"
                + metadata.body();
        return rewritten.getBytes();
    }

    private List<Map<String, Object>> buildManifest(List<PackageEntry> entries) {
        return entries.stream()
                .map(entry -> Map.<String, Object>of(
                        "path", entry.path(),
                        "size", entry.size(),
                        "contentType", entry.contentType()))
                .toList();
    }

    private byte[] buildBundle(List<PackageEntry> entries) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (PackageEntry entry : entries) {
                ZipEntry zipEntry = new ZipEntry(entry.path());
                zipOutputStream.putNextEntry(zipEntry);
                zipOutputStream.write(entry.content());
                zipOutputStream.closeEntry();
            }
            zipOutputStream.finish();
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build bundle zip", e);
        }
    }
}
