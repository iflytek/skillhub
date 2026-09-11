package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.NoOpPrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.PrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import com.iflytek.skillhub.domain.namespace.SlugValidator;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleCoordinate;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleManifest;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleManifestParser;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMember;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the outer Bundle tree and validates each staged package as an independent Skill package.
 * Only one member is materialized as byte arrays at a time; returned plans keep staged locations.
 */
@Service
public class SkillSuiteBundlePackageAnalyzer {

    private static final int MAX_MANIFEST_BYTES = 256_000;

    private final SkillSuiteBundleManifestParser manifestParser;
    private final SkillMetadataParser metadataParser;
    private final SkillPackageValidator packageValidator;
    private final PrePublishValidator prePublishValidator;

    @Autowired
    public SkillSuiteBundlePackageAnalyzer(
            SkillSuiteBundleManifestParser manifestParser,
            SkillMetadataParser metadataParser,
            SkillPackageValidator packageValidator,
            PrePublishValidator prePublishValidator
    ) {
        this.manifestParser = manifestParser;
        this.metadataParser = metadataParser;
        this.packageValidator = packageValidator;
        this.prePublishValidator = prePublishValidator;
    }

    SkillSuiteBundlePackageAnalyzer(
            SkillSuiteBundleManifestParser manifestParser,
            SkillMetadataParser metadataParser,
            SkillPackageValidator packageValidator
    ) {
        this(manifestParser, metadataParser, packageValidator, new NoOpPrePublishValidator());
    }

    public BundleAnalysis analyze(List<SkillSuiteBundleStagedEntry> stagedEntries) throws IOException {
        List<String> errors = new ArrayList<>();
        List<IndexedEntry> indexed = indexEntries(stagedEntries, errors);
        IndexedEntry manifestEntry = findManifest(indexed);
        String outerPrefix = parentPrefix(manifestEntry.normalizedPath());
        List<IndexedEntry> rooted = removeOuterPrefix(indexed, outerPrefix, errors);

        IndexedEntry rootedManifest = rooted.stream()
                .filter(entry -> SkillSuiteBundleManifest.FILE_NAME.equals(entry.rootPath()))
                .findFirst()
                .orElseThrow(() -> invalid("SUITE.yaml must be at the Bundle root"));
        SkillSuiteBundleManifest manifest = manifestParser.parse(readManifest(rootedManifest.staged()));

        Map<String, List<IndexedEntry>> filesByDirectory = new LinkedHashMap<>();
        for (SkillSuiteBundleMember member : manifest.spec().members()) {
            if (member.packageSource() != null) {
                filesByDirectory.put(member.packageSource().path(), new ArrayList<>());
            }
        }

        for (IndexedEntry entry : rooted) {
            if (SkillSuiteBundleManifest.FILE_NAME.equals(entry.rootPath())) {
                continue;
            }
            String owner = owningDirectory(entry.rootPath(), filesByDirectory.keySet());
            if (owner == null) {
                errors.add("Unclaimed archive entry: " + entry.rootPath());
            } else {
                filesByDirectory.get(owner).add(entry);
            }
        }

        List<MemberPackageAnalysis> packageMembers = new ArrayList<>();
        for (SkillSuiteBundleMember member : manifest.spec().members()) {
            if (member.packageSource() == null) {
                continue;
            }
            packageMembers.add(analyzeMember(
                    member.coordinate(), member.packageSource().path(),
                    filesByDirectory.get(member.packageSource().path())));
        }
        return new BundleAnalysis(manifest, List.copyOf(packageMembers), List.copyOf(errors));
    }

    private List<IndexedEntry> indexEntries(
            List<SkillSuiteBundleStagedEntry> stagedEntries, List<String> errors
    ) {
        List<IndexedEntry> indexed = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        for (SkillSuiteBundleStagedEntry staged : stagedEntries) {
            if (isOsMetadata(staged.path())) {
                continue;
            }
            if (staged.path().contains("\\")) {
                errors.add("Archive entry must use '/' separators: " + staged.path());
                continue;
            }
            String normalized;
            try {
                normalized = SkillPackagePolicy.normalizeEntryPath(staged.path());
            } catch (IllegalArgumentException exception) {
                errors.add(exception.getMessage());
                continue;
            }
            if (!paths.add(normalized)) {
                errors.add("Duplicate archive path: " + normalized);
                continue;
            }
            indexed.add(new IndexedEntry(staged, normalized, normalized));
        }
        return indexed;
    }

    private IndexedEntry findManifest(List<IndexedEntry> entries) {
        List<IndexedEntry> manifests = entries.stream()
                .filter(entry -> entry.normalizedPath().equals(SkillSuiteBundleManifest.FILE_NAME)
                        || entry.normalizedPath().endsWith("/" + SkillSuiteBundleManifest.FILE_NAME))
                .toList();
        if (manifests.size() != 1) {
            throw invalid("Bundle must contain exactly one SUITE.yaml manifest");
        }
        return manifests.getFirst();
    }

    private List<IndexedEntry> removeOuterPrefix(
            List<IndexedEntry> entries, String prefix, List<String> errors
    ) {
        if (prefix.isEmpty()) {
            return entries;
        }
        List<IndexedEntry> rooted = new ArrayList<>();
        for (IndexedEntry entry : entries) {
            if (!entry.normalizedPath().startsWith(prefix)) {
                errors.add("Archive entry is outside the Bundle root: " + entry.normalizedPath());
                continue;
            }
            rooted.add(new IndexedEntry(
                    entry.staged(), entry.normalizedPath(), entry.normalizedPath().substring(prefix.length())));
        }
        return rooted;
    }

    private MemberPackageAnalysis analyzeMember(
            SkillSuiteBundleCoordinate coordinate, String directory, List<IndexedEntry> indexedEntries
    ) throws IOException {
        List<PackageEntry> packageEntries = new ArrayList<>();
        List<StagedMemberFile> stagedFiles = new ArrayList<>();
        String prefix = directory + "/";
        for (IndexedEntry indexed : indexedEntries) {
            String relativePath = indexed.rootPath().substring(prefix.length());
            byte[] bytes = readStagedContent(indexed.staged());
            packageEntries.add(new PackageEntry(
                    relativePath, bytes, bytes.length, indexed.staged().contentType()));
            stagedFiles.add(new StagedMemberFile(
                    relativePath, bytes.length, indexed.staged().contentType(),
                    indexed.staged().sha256(), indexed.staged().objectKey()));
        }
        packageEntries.sort(Comparator.comparing(PackageEntry::path));
        stagedFiles.sort(Comparator.comparing(StagedMemberFile::relativePath));

        ValidationResult base = packageValidator.validate(packageEntries);
        List<String> memberErrors = new ArrayList<>(base.errors());
        List<String> memberWarnings = new ArrayList<>(base.warnings());
        packageEntries.stream()
                .map(PackageEntry::path)
                .filter(path -> path.endsWith("/" + SkillPackagePolicy.SKILL_MD_PATH))
                .forEach(path -> memberErrors.add("Nested SKILL.md is not allowed: " + path));

        SkillMetadata metadata = null;
        PackageEntry skillMd = packageEntries.stream()
                .filter(entry -> SkillPackagePolicy.SKILL_MD_PATH.equals(entry.path()))
                .findFirst()
                .orElse(null);
        if (skillMd != null && base.passed()) {
            metadata = metadataParser.parse(new String(skillMd.content(), StandardCharsets.UTF_8));
            String metadataSlug = SlugValidator.slugify(metadata.name());
            if (!coordinate.slug().equals(metadataSlug)) {
                memberErrors.add("SKILL.md name resolves to " + metadataSlug
                        + " and does not match manifest skill " + coordinate.canonical());
            }
            ValidationResult prePublish = prePublishValidator.validate(
                    new PrePublishValidator.SkillPackageContext(packageEntries, metadata, null, null));
            memberErrors.addAll(prePublish.errors());
            memberWarnings.addAll(prePublish.warnings());
        }

        ValidationResult validation = ValidationResult.of(memberErrors, memberWarnings);
        return new MemberPackageAnalysis(
                coordinate, directory, metadata, validation, List.copyOf(stagedFiles),
                fingerprint(stagedFiles));
    }

    private String owningDirectory(String path, Set<String> directories) {
        for (String directory : directories) {
            if (path.startsWith(directory + "/")) {
                return directory;
            }
        }
        return null;
    }

    private String readManifest(SkillSuiteBundleStagedEntry entry) throws IOException {
        try (InputStream input = entry.content().open()) {
            byte[] bytes = input.readNBytes(MAX_MANIFEST_BYTES + 1);
            if (bytes.length > MAX_MANIFEST_BYTES) {
                throw invalid("SUITE.yaml exceeds max size " + MAX_MANIFEST_BYTES);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private byte[] readStagedContent(SkillSuiteBundleStagedEntry entry) throws IOException {
        if (entry.size() < 0 || entry.size() >= Integer.MAX_VALUE) {
            throw invalid("Invalid staged file size: " + entry.path());
        }
        try (InputStream input = entry.content().open()) {
            byte[] bytes = input.readNBytes((int) entry.size() + 1);
            if (bytes.length != entry.size()) {
                throw invalid("Staged file size changed: " + entry.path());
            }
            return bytes;
        }
    }

    private String fingerprint(List<StagedMemberFile> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            files.stream()
                    .sorted(Comparator.comparing(StagedMemberFile::relativePath))
                    .forEach(file -> digest.update((file.relativePath() + ":" + file.sha256() + "\n")
                            .getBytes(StandardCharsets.UTF_8)));
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String parentPrefix(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash + 1);
    }

    private boolean isOsMetadata(String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.equals("__MACOSX") || normalized.startsWith("__MACOSX/")) {
            return true;
        }
        int slash = normalized.lastIndexOf('/');
        String fileName = slash < 0 ? normalized : normalized.substring(slash + 1);
        return fileName.equals(".DS_Store") || fileName.startsWith("._");
    }

    private DomainBadRequestException invalid(String detail) {
        return new DomainBadRequestException("error.suite.bundle.manifest.invalid", detail);
    }

    private record IndexedEntry(
            SkillSuiteBundleStagedEntry staged,
            String normalizedPath,
            String rootPath
    ) {
    }

    public record BundleAnalysis(
            SkillSuiteBundleManifest manifest,
            List<MemberPackageAnalysis> packageMembers,
            List<String> errors
    ) {
        public boolean confirmable() {
            return errors.isEmpty() && packageMembers.stream().allMatch(member -> member.validation().passed());
        }
    }

    public record MemberPackageAnalysis(
            SkillSuiteBundleCoordinate coordinate,
            String directory,
            SkillMetadata metadata,
            ValidationResult validation,
            List<StagedMemberFile> files,
            String fingerprint
    ) {
    }

    public record StagedMemberFile(
            String relativePath,
            long size,
            String contentType,
            String sha256,
            String objectKey
    ) {
    }
}
