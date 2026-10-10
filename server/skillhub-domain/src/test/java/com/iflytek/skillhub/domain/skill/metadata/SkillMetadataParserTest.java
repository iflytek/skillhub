package com.iflytek.skillhub.domain.skill.metadata;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
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

        DomainBadRequestException exception = assertThrows(
            DomainBadRequestException.class,
            () -> parser.parse(content)
        );
        assertEquals("error.skill.metadata.frontmatter.missingStart", exception.messageCode());
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

        DomainBadRequestException exception = assertThrows(
            DomainBadRequestException.class,
            () -> parser.parse(content)
        );
        assertEquals("error.skill.metadata.requiredField.missing", exception.messageCode());
        assertEquals("name", exception.messageArgs()[0]);
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

        DomainBadRequestException exception = assertThrows(
            DomainBadRequestException.class,
            () -> parser.parse(content)
        );
        assertEquals("error.skill.metadata.requiredField.missing", exception.messageCode());
        assertEquals("description", exception.messageArgs()[0]);
    }

    @Test
    void testAllowsMissingVersion() {
        String content = """
            ---
            name: test-skill
            description: Test description
            ---
            Body
            """;

        SkillMetadata metadata = parser.parse(content);

        assertEquals("test-skill", metadata.name());
        assertEquals("Test description", metadata.description());
        assertNull(metadata.version());
    }

    @Test
    void testUsesMetadataVersionWhenTopLevelVersionIsMissing() {
        String content = """
            ---
            name: agentguard
            description: Agent security guard
            metadata:
              author: GoPlusSecurity
              version: "1.1"
            ---
            Body
            """;

        SkillMetadata metadata = parser.parse(content);

        assertEquals("agentguard", metadata.name());
        assertEquals("Agent security guard", metadata.description());
        assertEquals("1.1", metadata.version());
    }

    @Test
    void testTopLevelVersionTakesPrecedenceOverMetadataVersion() {
        String content = """
            ---
            name: versioned-skill
            description: Prefer top-level version
            version: 2.0.0
            metadata:
              version: "1.1"
            ---
            Body
            """;

        SkillMetadata metadata = parser.parse(content);

        assertEquals("versioned-skill", metadata.name());
        assertEquals("Prefer top-level version", metadata.description());
        assertEquals("2.0.0", metadata.version());
    }

    @Test
    void testFallsBackToLooseFrontmatterParsingWhenYamlSyntaxIsNotStrict() {
        String content = """
            ---
            name: test-skill
            description: [unclosed bracket
            version: 1.0.0
            ---
            Body
            """;

        SkillMetadata metadata = parser.parse(content);

        assertEquals("test-skill", metadata.name());
        assertEquals("[unclosed bracket", metadata.description());
        assertEquals("1.0.0", metadata.version());
    }

    @Test
    void testAllowsColonInDescriptionWithoutStrictYamlQuoting() {
        String content = """
            ---
            name: clawdbot
            description: Send messages from Clawdbot via the discord tool: send messages, react, post or edit
            version: 1.0.0
            ---
            Body
            """;

        SkillMetadata metadata = parser.parse(content);

        assertEquals("clawdbot", metadata.name());
        assertEquals("Send messages from Clawdbot via the discord tool: send messages, react, post or edit", metadata.description());
        assertEquals("1.0.0", metadata.version());
    }

    @Test
    void testDoesNotInstantiateUnsafeGlobalYamlTags() {
        String content = """
            ---
            name: safe-skill
            description: Reject unsafe YAML object construction
            payload: !!java.util.Date []
            ---
            Body
            """;

        SkillMetadata metadata = parser.parse(content);

        assertEquals("!!java.util.Date []", metadata.frontmatter().get("payload"));
    }

    @Test
    void testThrowsWhenNoClosingDelimiter() {
        String content = """
            ---
            name: test-skill
            description: No closing delimiter
            version: 1.0.0
            """;

        DomainBadRequestException exception = assertThrows(
            DomainBadRequestException.class,
            () -> parser.parse(content)
        );
        assertEquals("error.skill.metadata.frontmatter.missingEnd", exception.messageCode());
    }
}
