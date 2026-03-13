package com.iflytek.skillhub.domain.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.SlugValidator;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.PrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
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
            ReviewTaskRepository reviewTaskRepository) {
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
    }

    @Transactional
    public PublishResult publishFromEntries(
            String namespaceSlug,
            List<PackageEntry> entries,
            String publisherId,
            SkillVisibility visibility) {

        // 1. Find namespace by slug
        Namespace namespace = namespaceRepository.findBySlug(namespaceSlug)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", namespaceSlug));

        // 2. Check publisher is member
        namespaceMemberRepository.findByNamespaceIdAndUserId(namespace.getId(), publisherId)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.publish.publisher.notMember", namespaceSlug));

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
            throw new DomainBadRequestException("error.skill.metadata.requiredField.missing", "version");
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

        // 6. Find or create Skill record
        Skill skill = skillRepository.findByNamespaceIdAndSlug(namespace.getId(), skillSlug)
                .orElseGet(() -> {
                    Skill newSkill = new Skill(namespace.getId(), skillSlug, publisherId, visibility);
                    newSkill.setCreatedBy(publisherId);
                    return skillRepository.save(newSkill);
                });

        // 7. Check version doesn't already exist
        if (skillVersionRepository.findBySkillIdAndVersion(skill.getId(), metadata.version()).isPresent()) {
            throw new DomainBadRequestException("error.skill.version.exists", metadata.version());
        }

        // 8. Create SkillVersion
        SkillVersion version = new SkillVersion(skill.getId(), metadata.version(), publisherId);
        version.setStatus(SkillVersionStatus.PENDING_REVIEW);

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

        ReviewTask reviewTask = new ReviewTask(version.getId(), namespace.getId(), publisherId);
        reviewTaskRepository.save(reviewTask);

        // 12. Update skill metadata without moving the published pointer
        skill.setDisplayName(metadata.name());
        skill.setSummary(metadata.description());
        skill.setUpdatedBy(publisherId);
        skillRepository.save(skill);

        // 13. Return identifiers for the pending review version
        return new PublishResult(skill.getId(), skill.getSlug(), version);
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
