package com.iflytek.skillhub.domain.skill.validation;

import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillPackageValidatorTest {

    private SkillPackageValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SkillPackageValidator(new SkillMetadataParser());
    }

    @Test
    void testValidPackage() {
        String skillMdContent = """
            ---
            name: test-skill
            description: A test skill
            version: 1.0.0
            ---
            # Test Skill
            """;

        List<PackageEntry> entries = List.of(
            new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown"),
            new PackageEntry("README.md", "readme".getBytes(), 6, "text/markdown")
        );

        ValidationResult result = validator.validate(entries);

        assertTrue(result.passed());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void testMissingSkillMd() {
        List<PackageEntry> entries = List.of(
            new PackageEntry("README.md", "readme".getBytes(), 6, "text/markdown")
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Missing required file: SKILL.md")));
    }

    @Test
    void testDisallowedExtension() {
        String skillMdContent = """
            ---
            name: test-skill
            description: A test skill
            version: 1.0.0
            ---
            Body
            """;

        List<PackageEntry> entries = List.of(
            new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown"),
            new PackageEntry("malware.exe", "bad".getBytes(), 3, "application/octet-stream")
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Disallowed file extension") && e.contains("malware.exe")));
    }

    @Test
    void testFileTooLarge() {
        String skillMdContent = """
            ---
            name: test-skill
            description: A test skill
            version: 1.0.0
            ---
            Body
            """;

        byte[] largeContent = new byte[2 * 1024 * 1024]; // 2MB

        List<PackageEntry> entries = List.of(
            new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown"),
            new PackageEntry("large.txt", largeContent, largeContent.length, "text/plain")
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("File too large") && e.contains("large.txt")));
    }

    @Test
    void testTooManyFiles() {
        String skillMdContent = """
            ---
            name: test-skill
            description: A test skill
            version: 1.0.0
            ---
            Body
            """;

        List<PackageEntry> entries = new ArrayList<>();
        entries.add(new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown"));

        for (int i = 0; i < 100; i++) {
            entries.add(new PackageEntry("file" + i + ".txt", "content".getBytes(), 7, "text/plain"));
        }

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Too many files")));
    }

    @Test
    void testMissingFrontmatterName() {
        String skillMdContent = """
            ---
            description: A test skill
            version: 1.0.0
            ---
            Body
            """;

        List<PackageEntry> entries = List.of(
            new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown")
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Invalid SKILL.md frontmatter") && e.contains("name")));
    }

    @Test
    void testPackageTooLarge() {
        String skillMdContent = """
            ---
            name: test-skill
            description: A test skill
            version: 1.0.0
            ---
            Body
            """;

        byte[] largeContent = new byte[900 * 1024]; // 900KB each

        List<PackageEntry> entries = List.of(
            new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown"),
            new PackageEntry("file1.txt", largeContent, largeContent.length, "text/plain"),
            new PackageEntry("file2.txt", largeContent, largeContent.length, "text/plain"),
            new PackageEntry("file3.txt", largeContent, largeContent.length, "text/plain"),
            new PackageEntry("file4.txt", largeContent, largeContent.length, "text/plain"),
            new PackageEntry("file5.txt", largeContent, largeContent.length, "text/plain"),
            new PackageEntry("file6.txt", largeContent, largeContent.length, "text/plain"),
            new PackageEntry("file7.txt", largeContent, largeContent.length, "text/plain"),
            new PackageEntry("file8.txt", largeContent, largeContent.length, "text/plain"),
            new PackageEntry("file9.txt", largeContent, largeContent.length, "text/plain"),
            new PackageEntry("file10.txt", largeContent, largeContent.length, "text/plain"),
            new PackageEntry("file11.txt", largeContent, largeContent.length, "text/plain"),
            new PackageEntry("file12.txt", largeContent, largeContent.length, "text/plain")
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Package too large")));
    }

    @Test
    void testPathTraversalEntryRejected() {
        String skillMdContent = """
            ---
            name: test-skill
            description: A test skill
            version: 1.0.0
            ---
            Body
            """;

        List<PackageEntry> entries = List.of(
            new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown"),
            new PackageEntry("../secrets.txt", "hidden".getBytes(), 6, "text/plain")
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("escapes package root")));
    }

    @Test
    void testDuplicateNormalizedPathRejected() {
        String skillMdContent = """
            ---
            name: test-skill
            description: A test skill
            version: 1.0.0
            ---
            Body
            """;

        List<PackageEntry> entries = List.of(
            new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown"),
            new PackageEntry("docs\\guide.md", "first".getBytes(), 5, "text/markdown"),
            new PackageEntry("docs/guide.md", "second".getBytes(), 6, "text/markdown")
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Duplicate package entry path: docs/guide.md")));
    }

    @Test
    void testSpoofedBinaryTextFileRejected() {
        String skillMdContent = """
            ---
            name: test-skill
            description: A test skill
            version: 1.0.0
            ---
            Body
            """;

        byte[] binaryPayload = new byte[] {0x4d, 0x5a, 0x00, 0x02};

        List<PackageEntry> entries = List.of(
            new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown"),
            new PackageEntry("notes.md", binaryPayload, binaryPayload.length, "text/markdown")
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("File content does not match extension")));
    }

    @Test
    void testInvalidSvgPayloadRejected() {
        String skillMdContent = """
            ---
            name: test-skill
            description: A test skill
            version: 1.0.0
            ---
            Body
            """;

        List<PackageEntry> entries = List.of(
            new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown"),
            new PackageEntry("icon.svg", "not actually svg".getBytes(), 16, "image/svg+xml")
        );

        ValidationResult result = validator.validate(entries);

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("File content does not match extension")));
    }
}
