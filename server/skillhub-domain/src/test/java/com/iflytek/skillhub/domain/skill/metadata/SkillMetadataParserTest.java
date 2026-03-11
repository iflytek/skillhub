package com.iflytek.skillhub.domain.skill.metadata;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkillMetadataParserTest {

    private final SkillMetadataParser parser = new SkillMetadataParser();

    @Test
    void testParseStandardFrontmatterAndBody() {
        String content = """
            ---
            name: test-skill
            description: A test skill
            version: 1.0.0
            ---
            # Test Skill

            This is the body content.
            """;

        SkillMetadata metadata = parser.parse(content);

        assertEquals("test-skill", metadata.name());
        assertEquals("A test skill", metadata.description());
        assertEquals("1.0.0", metadata.version());
        assertTrue(metadata.body().contains("# Test Skill"));
        assertTrue(metadata.body().contains("This is the body content."));
    }

    @Test
    void testExtensionFieldsPreservedInFrontmatter() {
        String content = """
            ---
            name: extended-skill
            description: Skill with extra fields
            version: 2.0.0
            author: John Doe
            tags:
              - ai
              - automation
            custom_field: custom_value
            ---
            Body content here.
            """;

        SkillMetadata metadata = parser.parse(content);

        assertEquals("extended-skill", metadata.name());
        assertEquals("Skill with extra fields", metadata.description());
        assertEquals("2.0.0", metadata.version());
        assertEquals("John Doe", metadata.frontmatter().get("author"));
        assertEquals("custom_value", metadata.frontmatter().get("custom_field"));
        assertNotNull(metadata.frontmatter().get("tags"));
    }

    @Test
    void testThrowsWhenNoFrontmatter() {
        String content = "# Just a markdown file without frontmatter";

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(content)
        );
        assertTrue(exception.getMessage().contains("Missing frontmatter"));
    }

    @Test
    void testThrowsWhenMissingName() {
        String content = """
            ---
            description: Missing name field
            version: 1.0.0
            ---
            Body
            """;

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(content)
        );
        assertTrue(exception.getMessage().contains("name"));
    }

    @Test
    void testThrowsWhenMissingDescription() {
        String content = """
            ---
            name: test-skill
            version: 1.0.0
            ---
            Body
            """;

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(content)
        );
        assertTrue(exception.getMessage().contains("description"));
    }

    @Test
    void testThrowsWhenMissingVersion() {
        String content = """
            ---
            name: test-skill
            description: Test description
            ---
            Body
            """;

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(content)
        );
        assertTrue(exception.getMessage().contains("version"));
    }

    @Test
    void testThrowsWhenInvalidYaml() {
        String content = """
            ---
            name: test-skill
            description: [unclosed bracket
            version: 1.0.0
            ---
            Body
            """;

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(content)
        );
        assertTrue(exception.getMessage().contains("Invalid YAML"));
    }

    @Test
    void testThrowsWhenNoClosingDelimiter() {
        String content = """
            ---
            name: test-skill
            description: No closing delimiter
            version: 1.0.0
            """;

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(content)
        );
        assertTrue(exception.getMessage().contains("Missing frontmatter"));
    }
}
