package com.iflytek.skillhub.controller.support;

import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipPackageExtractorTest {

    @Test
    void extractCanonicalizesCaseInsensitiveSkillMd() throws Exception {
        ZipPackageExtractor extractor = new ZipPackageExtractor(new SkillPublishProperties());
        byte[] zipBytes = createZip(Map.of(
                "skill.md", "---\nname: test\n---\n".getBytes(),
                "README.md", "# readme".getBytes()
        ));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", zipBytes);

        List<PackageEntry> entries = extractor.extract(file);

        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(e -> e.path().equals("SKILL.md")));
        assertTrue(entries.stream().noneMatch(e -> e.path().equals("skill.md")));
    }

    @Test
    void skipsWindowsStyleDirectoryEntries() throws Exception {
        ZipPackageExtractor extractor = new ZipPackageExtractor(new SkillPublishProperties());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("my-skill\\"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("my-skill/SKILL.md"));
            zos.write("---\nname: test\n---\n".getBytes());
            zos.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.zip", "application/zip", baos.toByteArray());

        List<PackageEntry> entries = extractor.extract(file);

        assertEquals(1, entries.size());
        assertEquals("SKILL.md", entries.get(0).path());
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
