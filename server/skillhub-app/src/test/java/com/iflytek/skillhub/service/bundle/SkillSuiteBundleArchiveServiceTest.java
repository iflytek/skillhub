package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleManifestParser;
import com.iflytek.skillhub.storage.ObjectMetadata;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillSuiteBundleArchiveServiceTest {

    @Test
    void stagesValidArchiveAndKeepsOnlyObjectLocationsInThePlan() throws Exception {
        InMemoryObjectStorage storage = new InMemoryObjectStorage();
        SkillSuiteBundleArchiveService service = service(storage, 1024 * 1024);
        byte[] archive = zip(List.of(
                file("outer/SUITE.yaml", manifest()),
                file("outer/skills/member/SKILL.md", skillMd()),
                file("outer/skills/member/notes.txt", "notes"),
                file("__MACOSX/._notes.txt", "ignored")
        ));

        SkillSuiteBundleArchiveService.StagedBundleAnalysis result = service.stageAndAnalyze(
                new MockMultipartFile("file", "bundle.zip", "application/zip", archive));

        assertThat(result.analysis().confirmable()).isTrue();
        assertThat(result.archiveSha256()).hasSize(64);
        assertThat(result.objectKeys()).hasSize(3);
        assertThat(storage.objects).containsOnlyKeys(result.objectKeys().toArray(String[]::new));
        assertThat(result.analysis().packageMembers()).singleElement().satisfies(member -> {
            assertThat(member.fingerprint()).startsWith("sha256:");
            assertThat(member.files()).extracting(
                    SkillSuiteBundlePackageAnalyzer.StagedMemberFile::relativePath)
                    .containsExactly("SKILL.md", "notes.txt");
            assertThat(member.files()).allSatisfy(stagedFile ->
                    assertThat(storage.objects).containsKey(stagedFile.objectKey()));
        });
        assertThat(storage.putCounts.values()).allMatch(count -> count == 1);
    }

    @Test
    void zipOrderAndCompressionMetadataDoNotChangeMemberFingerprint() throws Exception {
        List<ArchiveFile> forward = List.of(
                file("SUITE.yaml", manifest()),
                file("skills/member/SKILL.md", skillMd()),
                file("skills/member/notes.txt", "notes")
        );
        List<ArchiveFile> reverse = List.of(forward.get(2), forward.get(1), forward.get(0));

        SkillSuiteBundleArchiveService.StagedBundleAnalysis first = service(
                new InMemoryObjectStorage(), 1024 * 1024).stageAndAnalyze(
                new MockMultipartFile("file", "first.zip", "application/zip", zip(forward)));
        SkillSuiteBundleArchiveService.StagedBundleAnalysis second = service(
                new InMemoryObjectStorage(), 1024 * 1024).stageAndAnalyze(
                new MockMultipartFile("file", "second.zip", "application/zip", zip(reverse)));

        assertThat(first.analysis().packageMembers().getFirst().fingerprint())
                .isEqualTo(second.analysis().packageMembers().getFirst().fingerprint());
    }

    @Test
    void zipAndNormalizedDirectoryTreesProduceTheSameMemberFingerprint() throws Exception {
        List<ArchiveFile> files = List.of(
                file("SUITE.yaml", manifest()),
                file("skills/member/SKILL.md", skillMd()),
                file("skills/member/notes.txt", "notes")
        );
        SkillSuiteBundleArchiveService.StagedBundleAnalysis zipped = service(
                new InMemoryObjectStorage(), 1024 * 1024).stageAndAnalyze(
                new MockMultipartFile("file", "bundle.zip", "application/zip", zip(files)));

        SkillMetadataParser metadataParser = new SkillMetadataParser();
        SkillSuiteBundlePackageAnalyzer directoryAnalyzer = new SkillSuiteBundlePackageAnalyzer(
                new SkillSuiteBundleManifestParser(), metadataParser,
                new SkillPackageValidator(metadataParser));
        List<SkillSuiteBundleStagedEntry> directoryEntries = files.stream()
                .map(this::stagedEntry)
                .toList();
        SkillSuiteBundlePackageAnalyzer.BundleAnalysis directory = directoryAnalyzer.analyze(directoryEntries);

        assertThat(directory.packageMembers().getFirst().fingerprint())
                .isEqualTo(zipped.analysis().packageMembers().getFirst().fingerprint());
    }

    @Test
    void analyzesOneHundredPackagedMembersWithBoundedResultDescriptors() throws Exception {
        StringBuilder members = new StringBuilder();
        List<ArchiveFile> files = new java.util.ArrayList<>();
        for (int index = 0; index < 100; index++) {
            String slug = "member-" + index;
            members.append("    - skill: \"@global/").append(slug).append("\"\n")
                    .append("      package:\n")
                    .append("        path: skills/").append(slug).append("\n")
                    .append("        visibility: PUBLIC\n");
            files.add(file("skills/" + slug + "/SKILL.md", skillMd(slug)));
        }
        files.add(0, file("SUITE.yaml", manifest(members.toString(), "@global/member-0")));

        SkillSuiteBundleArchiveService.StagedBundleAnalysis result = service(
                new InMemoryObjectStorage(), 1024 * 1024).stageAndAnalyze(
                new MockMultipartFile("file", "maximum.zip", "application/zip", zip(files)));

        assertThat(result.analysis().confirmable()).isTrue();
        assertThat(result.analysis().packageMembers()).hasSize(100);
        assertThat(result.analysis().packageMembers())
                .allSatisfy(member -> assertThat(member.files()).hasSize(1));
    }

    @Test
    void rejectsExpandedArchiveLimitAndCleansUploadedObjects() throws Exception {
        InMemoryObjectStorage storage = new InMemoryObjectStorage();
        SkillSuiteBundleArchiveService service = service(storage, 2_000);
        byte[] archive = zip(List.of(
                file("SUITE.yaml", manifest()),
                file("skills/member/SKILL.md", skillMd()),
                file("skills/member/large.txt", "x".repeat(3_000))
        ));

        assertThatThrownBy(() -> service.stageAndAnalyze(
                new MockMultipartFile("file", "large.zip", "application/zip", archive)))
                .isInstanceOf(DomainBadRequestException.class);
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void rejectsUnixSymbolicLinksBeforeUploadingAnything() throws Exception {
        InMemoryObjectStorage storage = new InMemoryObjectStorage();
        byte[] archive = markFirstEntryAsUnixSymlink(zip(List.of(file("link", "target"))));

        assertThatThrownBy(() -> service(storage, 1024 * 1024).stageAndAnalyze(
                new MockMultipartFile("file", "link.zip", "application/zip", archive)))
                .isInstanceOf(DomainBadRequestException.class);
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void invalidBundleAnalysisDeletesTemporaryObjects() throws Exception {
        InMemoryObjectStorage storage = new InMemoryObjectStorage();
        byte[] archive = zip(List.of(
                file("SUITE.yaml", manifest()),
                file("skills/member/SKILL.md", skillMd()),
                file("README.md", "not declared")
        ));

        SkillSuiteBundleArchiveService.StagedBundleAnalysis result = service(
                storage, 1024 * 1024).stageAndAnalyze(
                new MockMultipartFile("file", "invalid.zip", "application/zip", archive));

        assertThat(result.analysis().confirmable()).isFalse();
        assertThat(result.archiveObjectKey()).isNull();
        assertThat(result.objectKeys()).isEmpty();
        assertThat(storage.objects).isEmpty();
    }

    private SkillSuiteBundleArchiveService service(InMemoryObjectStorage storage, long maxPackageSize) {
        SkillMetadataParser metadataParser = new SkillMetadataParser();
        SkillSuiteBundlePackageAnalyzer analyzer = new SkillSuiteBundlePackageAnalyzer(
                new SkillSuiteBundleManifestParser(), metadataParser,
                new SkillPackageValidator(metadataParser));
        SkillPublishProperties properties = new SkillPublishProperties();
        properties.setMaxPackageSize(maxPackageSize);
        properties.setMaxSingleFileSize(maxPackageSize);
        return new SkillSuiteBundleArchiveService(analyzer, storage, properties);
    }

    private byte[] zip(List<ArchiveFile> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (ArchiveFile file : files) {
                zip.putNextEntry(new ZipEntry(file.path()));
                zip.write(file.content());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private byte[] markFirstEntryAsUnixSymlink(byte[] archive) {
        ByteBuffer bytes = ByteBuffer.wrap(archive).order(ByteOrder.LITTLE_ENDIAN);
        for (int index = 0; index <= archive.length - 46; index++) {
            if (bytes.getInt(index) == 0x02014b50) {
                archive[index + 5] = 3;
                bytes.putInt(index + 38, 0120777 << 16);
                return archive;
            }
        }
        throw new AssertionError("ZIP central directory not found");
    }

    private ArchiveFile file(String path, String content) {
        return new ArchiveFile(path, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private SkillSuiteBundleStagedEntry stagedEntry(ArchiveFile file) {
        try {
            String sha = java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(file.content()));
            return new SkillSuiteBundleStagedEntry(
                    file.path(), file.content().length, "text/plain", sha,
                    "directory/" + file.path(), () -> new ByteArrayInputStream(file.content()));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String manifest() {
        return manifest("""
                    - skill: "@global/member"
                      package:
                        path: skills/member
                        visibility: PUBLIC
                """, "@global/member");
    }

    private String manifest(String members, String entry) {
        return """
                apiVersion: skillhub.iflytek.com/v1alpha1
                kind: SkillSuiteBundle
                metadata:
                  namespace: global
                  slug: archive-test
                spec:
                  mode: CREATE
                  version: 1.0.0
                  displayName: Archive Test
                  summary: Archive summary
                  overview: Archive overview
                  visibility: PUBLIC
                  entry: "%s"
                  members:
                %s
                """.formatted(entry, members);
    }

    private String skillMd() {
        return skillMd("member");
    }

    private String skillMd(String name) {
        return """
                ---
                name: %s
                description: Member skill
                version: 1.0.0
                ---
                Instructions.
                """.formatted(name);
    }

    private record ArchiveFile(String path, byte[] content) {
    }

    private static final class InMemoryObjectStorage implements ObjectStorageService {
        private final Map<String, byte[]> objects = new LinkedHashMap<>();
        private final Map<String, Integer> putCounts = new LinkedHashMap<>();

        @Override
        public void putObject(String key, InputStream data, long size, String contentType) {
            try {
                byte[] bytes = data.readAllBytes();
                if (bytes.length != size) {
                    throw new AssertionError("size mismatch");
                }
                objects.put(key, bytes);
                putCounts.merge(key, 1, Integer::sum);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public InputStream getObject(String key) {
            return new ByteArrayInputStream(objects.get(key));
        }

        @Override
        public void deleteObject(String key) {
            objects.remove(key);
        }

        @Override
        public void deleteObjects(List<String> keys) {
            keys.forEach(objects::remove);
        }

        @Override
        public boolean exists(String key) {
            return objects.containsKey(key);
        }

        @Override
        public ObjectMetadata getMetadata(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String generatePresignedUrl(String key, Duration expiry, String downloadFilename) {
            throw new UnsupportedOperationException();
        }
    }
}
