package com.iflytek.skillhub.bootstrap;

import com.iflytek.skillhub.bootstrap.BuiltinSkillManifestLoader.ManifestItem;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.SlugValidator;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Best-effort startup synchronizer for remotely hosted built-in skill packages.
 */
@Component
public class BuiltinSkillInitializer {

    static final String GLOBAL_NAMESPACE = "global";
    static final String SYSTEM_PUBLISHER_ID = "builtin-skill-publisher";

    private static final Logger log = LoggerFactory.getLogger(BuiltinSkillInitializer.class);
    private static final Set<String> SYSTEM_PUBLISHER_ROLES = Set.of("SUPER_ADMIN");
    private static final boolean CONFIRM_BUILTIN_PUBLISH_WARNINGS = true;

    private final BuiltinSkillProperties properties;
    private final BuiltinSkillManifestLoader manifestLoader;
    private final BuiltinSkillRemotePackageDownloader downloader;
    private final BuiltinSkillPackageExtractor extractor;
    private final SkillMetadataParser metadataParser;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final UserAccountRepository userAccountRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillFileRepository skillFileRepository;
    private final SkillPublishService skillPublishService;

    public BuiltinSkillInitializer(
            BuiltinSkillProperties properties,
            BuiltinSkillManifestLoader manifestLoader,
            BuiltinSkillRemotePackageDownloader downloader,
            BuiltinSkillPackageExtractor extractor,
            SkillMetadataParser metadataParser,
            NamespaceRepository namespaceRepository,
            NamespaceMemberRepository namespaceMemberRepository,
            UserAccountRepository userAccountRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            SkillFileRepository skillFileRepository,
            SkillPublishService skillPublishService) {
        this.properties = properties;
        this.manifestLoader = manifestLoader;
        this.downloader = downloader;
        this.extractor = extractor;
        this.metadataParser = metadataParser;
        this.namespaceRepository = namespaceRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.userAccountRepository = userAccountRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillFileRepository = skillFileRepository;
        this.skillPublishService = skillPublishService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Async("skillhubEventExecutor")
    public void synchronizeAfterApplicationReady() {
        synchronize();
    }

    void synchronize() {
        if (!properties.isEnabled()) {
            log.info("Built-in skill startup synchronization is disabled");
            return;
        }

        Optional<Namespace> namespace = namespaceRepository.findBySlug(GLOBAL_NAMESPACE);
        if (namespace.isEmpty()) {
            log.warn("Global namespace '{}' does not exist, skipping built-in skill synchronization",
                    GLOBAL_NAMESPACE);
            return;
        }

        List<ManifestItem> items = manifestLoader.load();
        if (items.isEmpty()) {
            log.info("No built-in skill manifest items to synchronize");
            return;
        }

        try {
            if (!ensureSystemPublisher(namespace.get())) {
                return;
            }
        } catch (RuntimeException exception) {
            log.error("Failed to initialize built-in skill system publisher, skipping synchronization: {}",
                    exception.getMessage(), exception);
            return;
        }

        int published = 0;
        int idempotentSkipped = 0;
        int conflictSkipped = 0;
        int failed = 0;
        for (ManifestItem item : items) {
            try {
                SyncOutcome outcome = syncItem(namespace.get(), item);
                switch (outcome) {
                    case PUBLISHED -> published++;
                    case IDEMPOTENT_SKIPPED -> idempotentSkipped++;
                    case CONFLICT_SKIPPED -> conflictSkipped++;
                    case FAILED -> failed++;
                }
            } catch (Exception exception) {
                failed++;
                log.error(
                        "Failed to synchronize built-in skill slug={} version={}: {}",
                        item.slug(),
                        item.version(),
                        exception.getMessage(),
                        exception
                );
            }
        }
        log.info(
                "Built-in skill synchronization finished: total={}, published={}, idempotentSkipped={}, conflictSkipped={}, failed={}",
                items.size(),
                published,
                idempotentSkipped,
                conflictSkipped,
                failed
        );
    }

    private boolean ensureSystemPublisher(Namespace namespace) {
        Optional<UserAccount> existingPublisher = userAccountRepository.findById(SYSTEM_PUBLISHER_ID);
        UserAccount publisher;
        if (existingPublisher.isPresent()) {
            publisher = existingPublisher.get();
        } else {
            publisher = UserAccount.systemAccount(
                    SYSTEM_PUBLISHER_ID,
                    "Built-in Skill Publisher",
                    null,
                    null
            );
            userAccountRepository.save(publisher);
        }
        if (!publisher.isSystemAccount()) {
            log.error("Built-in skill publisher account id '{}' already exists but is not a system account; "
                    + "skipping built-in skill synchronization", SYSTEM_PUBLISHER_ID);
            return false;
        }

        if (namespaceMemberRepository.findByNamespaceIdAndUserId(namespace.getId(), SYSTEM_PUBLISHER_ID).isEmpty()) {
            namespaceMemberRepository.save(new NamespaceMember(
                    namespace.getId(),
                    SYSTEM_PUBLISHER_ID,
                    NamespaceRole.OWNER
            ));
        }
        return true;
    }

    private SyncOutcome syncItem(Namespace namespace, ManifestItem item) throws Exception {
        Optional<SyncOutcome> skipBeforeDownload = shouldSkipBeforeDownload(namespace.getId(), item);
        if (skipBeforeDownload.isPresent()) {
            return skipBeforeDownload.get();
        }

        Optional<URI> packageUri = parsePackageUri(item);
        if (packageUri.isEmpty()) {
            return SyncOutcome.FAILED;
        }

        Optional<byte[]> packageBytes = downloader.download(packageUri.get());
        if (packageBytes.isEmpty()) {
            log.warn("Skipping built-in skill slug={} version={} because package download failed",
                    item.slug(), item.version());
            return SyncOutcome.FAILED;
        }

        byte[] downloadedBytes = packageBytes.get();
        String actualSha256 = sha256(downloadedBytes);
        if (!item.sha256().equals(actualSha256)) {
            log.warn(
                    "Skipping built-in skill slug={} version={} because package checksum mismatch: "
                            + "expectedSha256={}, actualSha256={}",
                    item.slug(),
                    item.version(),
                    item.sha256(),
                    actualSha256
            );
            return SyncOutcome.FAILED;
        }

        SkillPackageArchiveExtractor.ExtractionResult extractionResult = extractor.extract(downloadedBytes);
        List<PackageEntry> entries = extractionResult.entries();
        SkillMetadata metadata = parseSkillMetadata(entries);
        String packageSlug = SlugValidator.slugify(metadata.name());
        if (!item.slug().equals(packageSlug)) {
            log.warn(
                    "Skipping built-in skill manifest slug={} version={} because package slug is {}",
                    item.slug(),
                    item.version(),
                    packageSlug
            );
            return SyncOutcome.FAILED;
        }
        if (!item.version().equals(metadata.version())) {
            log.warn(
                    "Skipping built-in skill slug={} because manifest version {} does not match package version {}",
                    item.slug(),
                    item.version(),
                    metadata.version()
            );
            return SyncOutcome.FAILED;
        }

        Optional<SyncOutcome> skipExisting = shouldSkipExisting(namespace.getId(), item, entries);
        if (skipExisting.isPresent()) {
            return skipExisting.get();
        }

        try {
            skillPublishService.publishFromEntries(
                    GLOBAL_NAMESPACE,
                    entries,
                    SYSTEM_PUBLISHER_ID,
                    SkillVisibility.PUBLIC,
                    SYSTEM_PUBLISHER_ROLES,
                    CONFIRM_BUILTIN_PUBLISH_WARNINGS
            );
            log.info("Published built-in skill slug={} version={} to @{}",
                    item.slug(), item.version(), GLOBAL_NAMESPACE);
            return SyncOutcome.PUBLISHED;
        } catch (RuntimeException exception) {
            if (isAlreadyPublishedWithSameFingerprint(namespace.getId(), item, entries)) {
                log.info("Built-in skill slug={} version={} was published concurrently, skipping",
                        item.slug(), item.version());
                return SyncOutcome.IDEMPOTENT_SKIPPED;
            }
            log.error("Failed to publish built-in skill slug={} version={}: {}",
                    item.slug(), item.version(), exception.getMessage(), exception);
            return SyncOutcome.FAILED;
        }
    }

    private Optional<URI> parsePackageUri(ManifestItem item) {
        try {
            return Optional.of(URI.create(item.url()));
        } catch (IllegalArgumentException exception) {
            log.warn("Skipping built-in skill slug={} version={} because URL is not allowed: {}",
                    item.slug(), item.version(), exception.getMessage());
            return Optional.empty();
        }
    }

    private SkillMetadata parseSkillMetadata(List<PackageEntry> entries) {
        PackageEntry skillMd = entries.stream()
                .filter(entry -> SkillPackagePolicy.SKILL_MD_PATH.equals(entry.path()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Built-in skill package must contain " + SkillPackagePolicy.SKILL_MD_PATH));
        return metadataParser.parse(new String(skillMd.content(), StandardCharsets.UTF_8));
    }

    private Optional<SyncOutcome> shouldSkipBeforeDownload(Long namespaceId, ManifestItem item) {
        List<Skill> existingSkills = skillRepository.findByNamespaceIdAndSlug(namespaceId, item.slug());
        if (hasOtherOwnerConflict(existingSkills)) {
            log.warn("Skipping built-in skill slug={} before download because the slug already belongs to another user",
                    item.slug());
            return Optional.of(SyncOutcome.CONFLICT_SKIPPED);
        }

        Optional<Skill> builtinSkill = existingSkills.stream()
                .filter(skill -> SYSTEM_PUBLISHER_ID.equals(skill.getOwnerId()))
                .findFirst();
        if (builtinSkill.isEmpty()) {
            return Optional.empty();
        }

        Optional<SkillVersion> existingVersion = skillVersionRepository
                .findBySkillIdAndVersion(builtinSkill.get().getId(), item.version());
        if (existingVersion.isEmpty()) {
            return Optional.empty();
        }

        SkillVersion version = existingVersion.get();
        if (version.getStatus() == SkillVersionStatus.PUBLISHED) {
            log.info("Skipping built-in skill slug={} version={} before download because it is already published",
                    item.slug(), item.version());
        } else {
            log.info("Skipping built-in skill slug={} version={} before download because existing version status is {}",
                    item.slug(), item.version(), version.getStatus());
        }
        return Optional.of(SyncOutcome.IDEMPOTENT_SKIPPED);
    }

    private Optional<SyncOutcome> shouldSkipExisting(Long namespaceId, ManifestItem item, List<PackageEntry> entries) {
        List<Skill> existingSkills = skillRepository.findByNamespaceIdAndSlug(namespaceId, item.slug());
        if (hasOtherOwnerConflict(existingSkills)) {
            log.warn("Skipping built-in skill slug={} because the slug already belongs to another user",
                    item.slug());
            return Optional.of(SyncOutcome.CONFLICT_SKIPPED);
        }

        Optional<Skill> builtinSkill = existingSkills.stream()
                .filter(skill -> SYSTEM_PUBLISHER_ID.equals(skill.getOwnerId()))
                .findFirst();
        if (builtinSkill.isEmpty()) {
            return Optional.empty();
        }

        Optional<SkillVersion> existingVersion = skillVersionRepository
                .findBySkillIdAndVersion(builtinSkill.get().getId(), item.version());
        if (existingVersion.isEmpty()) {
            return Optional.empty();
        }

        SkillVersion version = existingVersion.get();
        if (version.getStatus() != SkillVersionStatus.PUBLISHED) {
            log.info("Skipping built-in skill slug={} version={} because existing version status is {}",
                    item.slug(), item.version(), version.getStatus());
            return Optional.of(SyncOutcome.IDEMPOTENT_SKIPPED);
        }

        String packageFingerprint = computeFingerprint(entries);
        String existingFingerprint = computeFingerprint(version);
        if (packageFingerprint.equals(existingFingerprint)) {
            log.info("Skipping built-in skill slug={} version={} because it is already published",
                    item.slug(), item.version());
            return Optional.of(SyncOutcome.IDEMPOTENT_SKIPPED);
        } else {
            log.warn(
                    "Skipping built-in skill slug={} version={} because published fingerprint differs: existing={}, package={}",
                    item.slug(),
                    item.version(),
                    existingFingerprint,
                    packageFingerprint
            );
            return Optional.of(SyncOutcome.CONFLICT_SKIPPED);
        }
    }

    private boolean isAlreadyPublishedWithSameFingerprint(Long namespaceId, ManifestItem item, List<PackageEntry> entries) {
        List<Skill> existingSkills = skillRepository.findByNamespaceIdAndSlug(namespaceId, item.slug());
        for (Skill skill : existingSkills) {
            if (!SYSTEM_PUBLISHER_ID.equals(skill.getOwnerId())) {
                continue;
            }
            Optional<SkillVersion> version = skillVersionRepository
                    .findBySkillIdAndVersion(skill.getId(), item.version());
            if (version.isPresent() && version.get().getStatus() == SkillVersionStatus.PUBLISHED) {
                String packageFingerprint = computeFingerprint(entries);
                String existingFingerprint = computeFingerprint(version.get());
                if (packageFingerprint.equals(existingFingerprint)) {
                    return true;
                }
                log.warn(
                        "Built-in skill slug={} version={} was published concurrently with different content: existing={}, package={}",
                        item.slug(),
                        item.version(),
                        existingFingerprint,
                        packageFingerprint
                );
                return false;
            }
        }
        return false;
    }

    private boolean hasOtherOwnerConflict(List<Skill> existingSkills) {
        return existingSkills.stream()
                .anyMatch(skill -> !SYSTEM_PUBLISHER_ID.equals(skill.getOwnerId()));
    }

    private String computeFingerprint(SkillVersion version) {
        List<SkillFile> files = skillFileRepository.findByVersionId(version.getId()).stream()
                .sorted(Comparator.comparing(SkillFile::getFilePath))
                .toList();
        return computeFingerprintFromFileDigests(files.stream()
                .map(file -> new FileDigest(file.getFilePath(), file.getSha256()))
                .toList());
    }

    private String computeFingerprint(List<PackageEntry> entries) {
        return computeFingerprintFromFileDigests(entries.stream()
                .map(entry -> new FileDigest(entry.path(), sha256(entry.content())))
                .toList());
    }

    private String computeFingerprintFromFileDigests(List<FileDigest> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (FileDigest file : files.stream().sorted(Comparator.comparing(FileDigest::path)).toList()) {
                String line = file.path() + ":" + file.sha256() + "\n";
                digest.update(line.getBytes(StandardCharsets.UTF_8));
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to compute built-in skill fingerprint", exception);
        }
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to compute built-in skill file digest", exception);
        }
    }

    private record FileDigest(String path, String sha256) {
    }

    private enum SyncOutcome {
        PUBLISHED,
        IDEMPOTENT_SKIPPED,
        CONFLICT_SKIPPED,
        FAILED
    }
}
