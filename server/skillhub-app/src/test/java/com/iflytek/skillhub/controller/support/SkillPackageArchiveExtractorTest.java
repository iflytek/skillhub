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
        byte[] content = new byte[1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "skill.zip",
            "application/zip",
            createZip("large.txt", content)
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> extractor.extract(file));

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
