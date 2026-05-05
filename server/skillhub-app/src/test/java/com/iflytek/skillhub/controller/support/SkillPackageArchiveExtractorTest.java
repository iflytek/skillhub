package com.iflytek.skillhub.controller.support;

import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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

    @Test
    void rejectsOversizedFileBeforeExtraction() throws Exception {
        SkillPublishProperties props = new SkillPublishProperties();
        props.setMaxPackageSize(5);
        SkillPackageArchiveExtractor smallExtractor = new SkillPackageArchiveExtractor(props);
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] zip = createZip(Map.of("a.txt", content));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zip);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> smallExtractor.extract(file));

        assertTrue(error.getMessage().contains("Package too large"));
    }

    @Test
    void rejectsTooManyFiles() throws Exception {
        SkillPublishProperties props = new SkillPublishProperties();
        props.setMaxFileCount(1);
        SkillPackageArchiveExtractor limitedExtractor = new SkillPackageArchiveExtractor(props);
        byte[] zip = createZip(Map.of("a.txt", "a".getBytes(), "b.txt", "b".getBytes()));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zip);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> limitedExtractor.extract(file));

        assertTrue(error.getMessage().contains("Too many files"));
    }

    @Test
    void rejectsTotalSizeExceedingLimitDuringExtraction() throws Exception {
        SkillPublishProperties props = new SkillPublishProperties();
        props.setMaxPackageSize(1500);
        SkillPackageArchiveExtractor smallExtractor = new SkillPackageArchiveExtractor(props);
        byte[] zip = createZip(Map.of(
                "a.txt", "a".repeat(1000).getBytes(StandardCharsets.UTF_8),
                "b.txt", "b".repeat(1000).getBytes(StandardCharsets.UTF_8)
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zip);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> smallExtractor.extract(file));

        assertTrue(error.getMessage().contains("Package too large"));
    }

    @Test
    void assignsCorrectContentTypes() throws Exception {
        Map<String, byte[]> entriesMap = new LinkedHashMap<>();
        entriesMap.put("a.py", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("b.json", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("c.yaml", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("d.yml", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("e.txt", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("f.md", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("g.html", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("h.css", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("i.csv", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("j.xml", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("k.js", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("k.cjs", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("k.mjs", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("l.ts", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("m.sh", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("m.bash", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("m.zsh", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("n.png", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("o.jpg", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("p.jpeg", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("q.gif", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("r.svg", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("s.webp", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("t.ico", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("u.pdf", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("v.toml", "x".getBytes(StandardCharsets.UTF_8));
        entriesMap.put("w.bin", "x".getBytes(StandardCharsets.UTF_8));
        byte[] zip = createZip(entriesMap);
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zip);
        List<PackageEntry> entries = extractor.extract(file);

        java.util.Map<String, String> expected = new java.util.LinkedHashMap<>();
        expected.put("a.py", "text/x-python");
        expected.put("b.json", "application/json");
        expected.put("c.yaml", "application/x-yaml");
        expected.put("d.yml", "application/x-yaml");
        expected.put("e.txt", "text/plain");
        expected.put("f.md", "text/markdown");
        expected.put("g.html", "text/html");
        expected.put("h.css", "text/css");
        expected.put("i.csv", "text/csv");
        expected.put("j.xml", "application/xml");
        expected.put("k.js", "text/javascript");
        expected.put("k.cjs", "text/javascript");
        expected.put("k.mjs", "text/javascript");
        expected.put("l.ts", "text/typescript");
        expected.put("m.sh", "text/x-shellscript");
        expected.put("m.bash", "text/x-shellscript");
        expected.put("m.zsh", "text/x-shellscript");
        expected.put("n.png", "image/png");
        expected.put("o.jpg", "image/jpeg");
        expected.put("p.jpeg", "image/jpeg");
        expected.put("q.gif", "image/gif");
        expected.put("r.svg", "image/svg+xml");
        expected.put("s.webp", "image/webp");
        expected.put("t.ico", "image/x-icon");
        expected.put("u.pdf", "application/pdf");
        expected.put("v.toml", "application/toml");
        expected.put("w.bin", "application/octet-stream");

        for (PackageEntry entry : entries) {
            assertEquals(expected.get(entry.path()), entry.contentType(), "path: " + entry.path());
        }
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
