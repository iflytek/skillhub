package com.iflytek.skillhub.service.support;

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
