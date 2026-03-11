package com.iflytek.skillhub.domain.skill.metadata;

import org.yaml.snakeyaml.Yaml;

import java.util.Map;

public class SkillMetadataParser {

    private static final String FRONTMATTER_DELIMITER = "---";

    public SkillMetadata parse(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Content cannot be null or empty");
        }

        String trimmedContent = content.trim();

        if (!trimmedContent.startsWith(FRONTMATTER_DELIMITER)) {
            throw new IllegalArgumentException("Missing frontmatter: content must start with '---'");
        }

        int firstDelimiterEnd = trimmedContent.indexOf('\n', FRONTMATTER_DELIMITER.length());
        if (firstDelimiterEnd == -1) {
            throw new IllegalArgumentException("Missing frontmatter: no content after first '---'");
        }

        int secondDelimiterStart = trimmedContent.indexOf(FRONTMATTER_DELIMITER, firstDelimiterEnd + 1);
        if (secondDelimiterStart == -1) {
            throw new IllegalArgumentException("Missing frontmatter: no closing '---' found");
        }

        String yamlContent = trimmedContent.substring(firstDelimiterEnd + 1, secondDelimiterStart).trim();
        String body = trimmedContent.substring(secondDelimiterStart + FRONTMATTER_DELIMITER.length()).trim();

        Map<String, Object> frontmatter;
        try {
            Yaml yaml = new Yaml();
            Object parsed = yaml.load(yamlContent);
            if (!(parsed instanceof Map)) {
                throw new IllegalArgumentException("Invalid YAML: frontmatter must be a map");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parsed;
            frontmatter = map;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid YAML in frontmatter: " + e.getMessage(), e);
        }

        String name = extractRequiredField(frontmatter, "name");
        String description = extractRequiredField(frontmatter, "description");
        String version = extractRequiredField(frontmatter, "version");

        return new SkillMetadata(name, description, version, body, frontmatter);
    }

    private String extractRequiredField(Map<String, Object> frontmatter, String fieldName) {
        Object value = frontmatter.get(fieldName);
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        return value.toString();
    }
}
