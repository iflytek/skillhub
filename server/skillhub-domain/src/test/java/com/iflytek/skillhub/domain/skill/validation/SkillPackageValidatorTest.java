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

        assertTrue(result.passed());
        assertTrue(result.warnings().stream().anyMatch(e -> e.contains("Disallowed file extension") && e.contains("malware.exe")));
    }

    @Test
    void testFileTooLarge() {
        // Use a custom validator with 1KB single file limit to test the logic
        SkillPackageValidator smallValidator = new SkillPackageValidator(
                new SkillMetadataParser(), 100, 1024, 100 * 1024 * 1024,
                SkillPackagePolicy.ALLOWED_EXTENSIONS);
        byte[] bigContent = new byte[1025]; // >1KB
        List<PackageEntry> entries = List.of(
                skillMdEntry(),
                new PackageEntry("big.txt", bigContent, bigContent.length, "text/plain")
        );
        ValidationResult result = smallValidator.validate(entries);
        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("File too large")));
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
    void testInvalidYamlFrontmatterWithColonInValueShouldStillPass() {
        String skillMdContent = """
            ---
            name: clawdbot
            description: Send messages from Clawdbot via the discord tool: send messages, react, post or edit
            version: 1.0.0
            ---
            Body
            """;

        List<PackageEntry> entries = List.of(
                new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown")
        );

        ValidationResult result = validator.validate(entries);

        assertTrue(result.passed());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void testPackageTooLarge() {
        // Use a custom validator with 2KB total limit to test the logic
        SkillPackageValidator smallValidator = new SkillPackageValidator(
                new SkillMetadataParser(), 100, 10 * 1024 * 1024, 2048,
                SkillPackagePolicy.ALLOWED_EXTENSIONS);
        byte[] content = new byte[2000]; // 2KB
        List<PackageEntry> entries = List.of(
                skillMdEntry(),  // ~50 bytes
                new PackageEntry("data.txt", content, content.length, "text/plain")
        );
        ValidationResult result = smallValidator.validate(entries);
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

        assertTrue(result.passed());
        assertTrue(result.warnings().stream().anyMatch(e -> e.contains("File content does not match extension")));
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

        assertTrue(result.passed());
        assertTrue(result.warnings().stream().anyMatch(e -> e.contains("File content does not match extension")));
    }

    @Test
    void rejectsJpegWithWrongMagicBytes() {
        List<PackageEntry> entries = List.of(
                skillMdEntry(),
                new PackageEntry("photo.jpeg", new byte[]{0x00, 0x00}, 2, "image/jpeg")
        );
        ValidationResult result = validator.validate(entries);
        assertTrue(result.passed());
        assertTrue(result.warnings().stream().anyMatch(e -> e.contains("photo.jpeg")));
    }

    @Test
    void acceptsValidGif() {
        byte[] gifHeader = "GIF89a".getBytes();
        byte[] content = new byte[20];
        System.arraycopy(gifHeader, 0, content, 0, gifHeader.length);
        List<PackageEntry> entries = List.of(
                skillMdEntry(),
                new PackageEntry("anim.gif", content, content.length, "image/gif")
        );
        ValidationResult result = validator.validate(entries);
        assertTrue(result.passed());
    }

    @Test
    void acceptsDocxFile() {
        // DOCX is a ZIP-based format; PK magic bytes (0x50, 0x4b, 0x03, 0x04)
        byte[] docxContent = new byte[] {0x50, 0x4b, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        List<PackageEntry> entries = List.of(
                skillMdEntry(),
                new PackageEntry("document.docx", docxContent, docxContent.length, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        );
        ValidationResult result = validator.validate(entries);
        assertTrue(result.passed());
    }

    @Test
    void acceptsXsdSchemaFile() {
        byte[] xsdContent = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"></xs:schema>".getBytes();
        List<PackageEntry> entries = List.of(
                skillMdEntry(),
                new PackageEntry("schema.xsd", xsdContent, xsdContent.length, "application/xml")
        );
        ValidationResult result = validator.validate(entries);
        assertTrue(result.passed());
    }

    private PackageEntry skillMdEntry() {
        String skillMdContent = """
            ---
            name: test-skill
            description: A test skill
            version: 1.0.0
            ---
            Body
            """;
        return new PackageEntry("SKILL.md", skillMdContent.getBytes(), skillMdContent.length(), "text/markdown");
    }
}
