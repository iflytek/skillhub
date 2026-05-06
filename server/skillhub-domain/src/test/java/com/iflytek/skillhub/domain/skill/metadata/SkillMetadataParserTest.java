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

    @Test
    void testThrowsWhenContentIsNull() {
        DomainBadRequestException exception = assertThrows(
            DomainBadRequestException.class,
            () -> parser.parse(null)
        );
        assertEquals("error.skill.metadata.content.empty", exception.messageCode());
    }

    @Test
    void testThrowsWhenContentIsBlank() {
        DomainBadRequestException exception = assertThrows(
            DomainBadRequestException.class,
            () -> parser.parse("   ")
        );
        assertEquals("error.skill.metadata.content.empty", exception.messageCode());
    }

    @Test
    void testThrowsWhenFrontmatterContentMissing() {
        String content = "---";

        DomainBadRequestException exception = assertThrows(
            DomainBadRequestException.class,
            () -> parser.parse(content)
        );
        assertEquals("error.skill.metadata.frontmatter.missingContent", exception.messageCode());
    }

    @Test
    void testThrowsWhenFrontmatterIsNotAMap() {
        String content = """
            ---
            - item1
            - item2
            ---
            Body
            """;

        DomainBadRequestException exception = assertThrows(
            DomainBadRequestException.class,
            () -> parser.parse(content)
        );
        assertEquals("error.skill.metadata.yaml.notMap", exception.messageCode());
    }

    @Test
    void testStripsWrappingQuotes() {
        String content = """
            ---
            name: "quoted-name"
            description: 'quoted-description'
            version: \"1.0.0\"
            ---
            Body
            """;

        SkillMetadata metadata = parser.parse(content);

        assertEquals("quoted-name", metadata.name());
        assertEquals("quoted-description", metadata.description());
        assertEquals("1.0.0", metadata.version());
    }

    @Test
    void testLooseFrontmatterIgnoresCommentsAndEmptyLines() {
        String content = """
            ---
            name: loose-skill
            # this is a comment
            description: loose description
            version: 1.0.0
            ---
            Body
            """;

        SkillMetadata metadata = parser.parse(content);

        assertEquals("loose-skill", metadata.name());
        assertEquals("loose description", metadata.description());
        assertEquals("1.0.0", metadata.version());
    }

    @Test
    void testLooseFrontmatterIgnoresLinesWithoutColon() {
        String content = """
            ---
            name: loose-skill
            no colon here
            description: desc
            version: 1.0.0
            ---
            Body
            """;

        SkillMetadata metadata = parser.parse(content);

        assertEquals("loose-skill", metadata.name());
        assertEquals("desc", metadata.description());
        assertEquals("1.0.0", metadata.version());
    }

    @Test
    void testLooseFrontmatterIgnoresEmptyKey() {
        String content = """
            ---
            name: loose-skill
             : empty key
            description: desc
            version: 1.0.0
            ---
            Body
            """;

        SkillMetadata metadata = parser.parse(content);

        assertEquals("loose-skill", metadata.name());
        assertEquals("desc", metadata.description());
        assertEquals("1.0.0", metadata.version());
    }

    @Test
    void testThrowsWhenYamlInvalidAndLooseParsingEmpty() {
        // SnakeYAML throws on unclosed mapping start, and loose parsing returns empty because no colons
        String content = "---\n{\n---\nBody";

        DomainBadRequestException exception = assertThrows(
            DomainBadRequestException.class,
            () -> parser.parse(content)
        );
        assertEquals("error.skill.metadata.yaml.invalid", exception.messageCode());
    }

    @Test
    void testLooseFrontmatterIgnoresCommentsAndEmptyLinesInMalformedYaml() {
        String content = """
            ---
            name: loose-skill
            # this is a comment

            description: loose description
            version: 1.0.0
            ---
            Body
            """;

        // Force loose parsing by making SnakeYAML throw — use tab indentation which SnakeYAML rejects
        String malformed = "---\n\tname: loose-skill\n\tdescription: desc\n\tversion: 1.0.0\n---\nBody";

        SkillMetadata metadata = parser.parse(malformed);

        assertEquals("loose-skill", metadata.name());
        assertEquals("desc", metadata.description());
        assertEquals("1.0.0", metadata.version());
    }

    @Test
    void testStripsSingleQuotes() {
        String content = """
            ---
            name: 'single-quoted-name'
            description: 'single-quoted-description'
            version: '1.0.0'
            ---
            Body
            """;

        SkillMetadata metadata = parser.parse(content);

        assertEquals("single-quoted-name", metadata.name());
        assertEquals("single-quoted-description", metadata.description());
        assertEquals("1.0.0", metadata.version());
    }

    @Test
    void testLooseFrontmatterIgnoresCommentsInMalformedYaml() {
        // Tab indentation triggers loose parsing; # comment line hits the continue branch
        String malformed = "---\n\tname: loose-skill\n\t# this is a comment\n\tdescription: desc\n\tversion: 1.0.0\n---\nBody";

        SkillMetadata metadata = parser.parse(malformed);

        assertEquals("loose-skill", metadata.name());
        assertEquals("desc", metadata.description());
        assertEquals("1.0.0", metadata.version());
    }

    @Test
    void testLooseFrontmatterStripsQuotesInMalformedYaml() {
        // Tab indentation triggers loose parsing; quoted values exercise stripWrappingQuotes
        String malformed = "---\n\tname: \"quoted-name\"\n\tdescription: 'quoted-desc'\n\tversion: \"1.0.0\"\n---\nBody";

        SkillMetadata metadata = parser.parse(malformed);

        assertEquals("quoted-name", metadata.name());
        assertEquals("quoted-desc", metadata.description());
        assertEquals("1.0.0", metadata.version());
    }
}
