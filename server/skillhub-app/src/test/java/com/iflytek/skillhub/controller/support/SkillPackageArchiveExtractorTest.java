package com.iflytek.skillhub.controller.support;

import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPackageArchiveExtractorTest {

    private SkillPackageArchiveExtractor extractor;

    @BeforeEach
    void setUp() {
        SkillPublishProperties props = new SkillPublishProperties();
        extractor = new SkillPackageArchiveExtractor(props);
    }

    @Test
    void shouldRejectPathTraversalEntry() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "skill.zip",
            "application/zip",
            createZip("../secrets.txt", "hidden")
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> extractor.extract(file));

        assertTrue(error.getMessage().contains("escapes package root"));
    }

    @Test
    void shouldRejectOversizedZipEntry() throws Exception {
        SkillPublishProperties props = new SkillPublishProperties();
        props.setMaxSingleFileSize(1024); // 1KB limit
        SkillPackageArchiveExtractor smallExtractor = new SkillPackageArchiveExtractor(props);

        byte[] content = new byte[1025]; // >1KB
        byte[] zip = createZip(Map.of("large.txt", content));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zip);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> smallExtractor.extract(file));

        assertTrue(error.getMessage().contains("File too large: large.txt"));
    }

    @Test
    void respectsConfiguredSingleFileLimit() throws Exception {
        SkillPublishProperties props = new SkillPublishProperties();
        props.setMaxSingleFileSize(5 * 1024 * 1024); // 5MB
        SkillPackageArchiveExtractor customExtractor = new SkillPackageArchiveExtractor(props);

        byte[] content = new byte[3 * 1024 * 1024]; // 3MB — under 5MB limit
        byte[] zip = createZip(Map.of("data.md", content));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zip);

        List<PackageEntry> entries = customExtractor.extract(file);
        assertEquals(1, entries.size());
    }

    @Test
    void stripsRootDirectoryWhenSingleFolder() throws Exception {
        byte[] zipBytes = createZip(Map.of(
                "my-skill/SKILL.md", "---\nname: test\n---\n".getBytes(),
                "my-skill/config.json", "{}".getBytes()
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zipBytes);
        List<PackageEntry> entries = extractor.extract(file);

        assertTrue(entries.stream().anyMatch(e -> e.path().equals("SKILL.md")));
        assertTrue(entries.stream().anyMatch(e -> e.path().equals("config.json")));
    }

    @Test
    void doesNotStripWhenMultipleRootEntries() throws Exception {
        byte[] zipBytes = createZip(Map.of(
                "SKILL.md", "---\nname: test\n---\n".getBytes(),
                "config.json", "{}".getBytes()
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zipBytes);
        List<PackageEntry> entries = extractor.extract(file);

        assertTrue(entries.stream().anyMatch(e -> e.path().equals("SKILL.md")));
    }

    @Test
    void stripsRootDirectoryWhenZipHasExplicitDirEntry() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("my-skill/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("my-skill/SKILL.md"));
            zos.write("---\nname: test\n---".getBytes());
            zos.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", baos.toByteArray());
        List<PackageEntry> entries = extractor.extract(file);

        assertEquals(1, entries.size());
        assertEquals("SKILL.md", entries.get(0).path());
    }

    @Test
    void doesNotStripWhenMultipleRootDirectories() throws Exception {
        byte[] zipBytes = createZip(Map.of(
                "dir-a/SKILL.md", "---\nname: test\n---\n".getBytes(),
                "dir-b/other.md", "# other".getBytes()
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zipBytes);
        List<PackageEntry> entries = extractor.extract(file);

        assertTrue(entries.stream().anyMatch(e -> e.path().equals("dir-a/SKILL.md")));
        assertTrue(entries.stream().anyMatch(e -> e.path().equals("dir-b/other.md")));
    }

    @Test
    void promotesSkillMdFromSubdirectoryAndDiscardsRootFiles() throws Exception {
        byte[] zipBytes = createZip(Map.of(
                "my-skill/SKILL.md", "---\nname: test\n---\n".getBytes(),
                "my-skill/README.md", "# readme".getBytes(),
                "other.txt", "stray file".getBytes()
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zipBytes);

        SkillPackageArchiveExtractor.ExtractionResult result = extractor.extractWithWarnings(file);

        assertEquals(2, result.entries().size());
        assertTrue(result.entries().stream().anyMatch(e -> e.path().equals("SKILL.md")));
        assertTrue(result.entries().stream().anyMatch(e -> e.path().equals("README.md")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("other.txt")));
    }

    @Test
    void rejectsAmbiguousMultipleSkillMdInSubdirectories() throws Exception {
        byte[] zipBytes = createZip(Map.of(
                "dir1/SKILL.md", "---\nname: a\n---\n".getBytes(),
                "dir2/SKILL.md", "---\nname: b\n---\n".getBytes()
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zipBytes);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> extractor.extractWithWarnings(file));
        assertTrue(error.getMessage().contains("Ambiguous"));
    }

    @Test
    void noPromotionWhenSkillMdAtRoot() throws Exception {
        byte[] zipBytes = createZip(Map.of(
                "SKILL.md", "---\nname: test\n---\n".getBytes(),
                "sub/file.txt", "content".getBytes()
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zipBytes);

        SkillPackageArchiveExtractor.ExtractionResult result = extractor.extractWithWarnings(file);

        assertEquals(2, result.entries().size());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void promotesSubdirectoryPreservingNestedPaths() throws Exception {
        byte[] zipBytes = createZip(Map.of(
                "my-skill/SKILL.md", "---\nname: test\n---\n".getBytes(),
                "my-skill/sub/deep.md", "nested".getBytes(),
                "stray.txt", "ignored".getBytes()
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zipBytes);

        SkillPackageArchiveExtractor.ExtractionResult result = extractor.extractWithWarnings(file);

        assertEquals(2, result.entries().size());
        assertTrue(result.entries().stream().anyMatch(e -> e.path().equals("SKILL.md")));
        assertTrue(result.entries().stream().anyMatch(e -> e.path().equals("sub/deep.md")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("stray.txt")));
    }

    @Test
    void filtersMacOsMetadataEntries() throws Exception {
        byte[] zipBytes = createZip(Map.of(
                "my-skill/SKILL.md", "---\nname: test\n---\n".getBytes(),
                "my-skill/README.md", "# readme".getBytes(),
                "__MACOSX/my-skill/._SKILL.md", "resource fork".getBytes(),
                "my-skill/.DS_Store", "binary".getBytes()
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zipBytes);

        List<PackageEntry> entries = extractor.extract(file);

        assertEquals(2, entries.size());
        assertTrue(entries.stream().noneMatch(e -> e.path().contains("MACOSX")));
        assertTrue(entries.stream().noneMatch(e -> e.path().contains(".DS_Store")));
    }

    @Test
    void realWorldMacZipWithNestedSkillMd() throws Exception {
        // Simulates: ui-ux-pro-max/uiux/SKILL.md + __MACOSX + .DS_Store + stray csv
        byte[] zipBytes = createZip(Map.of(
                "ui-ux-pro-max/uiux/SKILL.md", "---\nname: uiux\nversion: 1.0.0\n---\nBody".getBytes(),
                "ui-ux-pro-max/uiux/scripts/core.py", "# code".getBytes(),
                "ui-ux-pro-max/uiux/data/styles.csv", "col1,col2".getBytes(),
                "ui-ux-pro-max/stray.csv", "stray data".getBytes(),
                "__MACOSX/ui-ux-pro-max/._stray.csv", "resource fork".getBytes(),
                "ui-ux-pro-max/.DS_Store", "binary".getBytes(),
                "__MACOSX/._ui-ux-pro-max", "resource fork".getBytes()
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zipBytes);

        SkillPackageArchiveExtractor.ExtractionResult result = extractor.extractWithWarnings(file);

        // macOS files filtered, root stripped to ui-ux-pro-max/, then uiux/ promoted
        assertTrue(result.entries().stream().anyMatch(e -> e.path().equals("SKILL.md")));
        assertTrue(result.entries().stream().anyMatch(e -> e.path().equals("scripts/core.py")));
        assertTrue(result.entries().stream().anyMatch(e -> e.path().equals("data/styles.csv")));
        assertTrue(result.entries().stream().noneMatch(e -> e.path().contains("MACOSX")));
        assertTrue(result.entries().stream().noneMatch(e -> e.path().contains(".DS_Store")));
        // stray.csv outside uiux/ should be in warnings
        assertFalse(result.warnings().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("stray.csv")));
    }

    @Test
    void macZipWithSingleFolderAndSkillMdAtRoot() throws Exception {
        // All files under one folder, SKILL.md at folder root — simplest macOS case
        byte[] zipBytes = createZip(Map.of(
                "my-skill/SKILL.md", "---\nname: test\nversion: 1.0.0\n---\nBody".getBytes(),
                "my-skill/README.md", "# readme".getBytes(),
                "__MACOSX/my-skill/._SKILL.md", "fork".getBytes(),
                "__MACOSX/._my-skill", "fork".getBytes()
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zipBytes);

        SkillPackageArchiveExtractor.ExtractionResult result = extractor.extractWithWarnings(file);

        assertEquals(2, result.entries().size());
        assertTrue(result.entries().stream().anyMatch(e -> e.path().equals("SKILL.md")));
        assertTrue(result.entries().stream().anyMatch(e -> e.path().equals("README.md")));
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void extractWithWarningsNoSkillMdAnywhere() throws Exception {
        byte[] zipBytes = createZip(Map.of(
                "README.md", "# no skill".getBytes(),
                "config.json", "{}".getBytes()
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zipBytes);

        SkillPackageArchiveExtractor.ExtractionResult result = extractor.extractWithWarnings(file);

        // No SKILL.md found — entries returned as-is, validator will catch the error
        assertEquals(2, result.entries().size());
        assertTrue(result.warnings().isEmpty());
    }

    private byte[] createZip(String entryName, String content) throws Exception {
        return createZip(entryName, content.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] createZip(String entryName, byte[] content) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(content);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createZip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                zos.putNextEntry(entry);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
