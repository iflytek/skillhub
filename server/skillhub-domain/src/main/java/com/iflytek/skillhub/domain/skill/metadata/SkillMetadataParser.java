package com.iflytek.skillhub.domain.skill.metadata;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses `SKILL.md` frontmatter and body content into the normalized metadata model used by the
 * publish pipeline.
 */
public class SkillMetadataParser {

    private static final String FRONTMATTER_DELIMITER = "---";

    public SkillMetadata parse(String content) {
        if (content == null || content.isBlank()) {
            throw new DomainBadRequestException("error.skill.metadata.content.empty");
        }

        String trimmedContent = content.trim();

        if (!trimmedContent.startsWith(FRONTMATTER_DELIMITER)) {
            throw new DomainBadRequestException("error.skill.metadata.frontmatter.missingStart");
        }

        int firstDelimiterEnd = trimmedContent.indexOf('\n', FRONTMATTER_DELIMITER.length());
        if (firstDelimiterEnd == -1) {
            throw new DomainBadRequestException("error.skill.metadata.frontmatter.missingContent");
        }

        int secondDelimiterStart = trimmedContent.indexOf(FRONTMATTER_DELIMITER, firstDelimiterEnd + 1);
        if (secondDelimiterStart == -1) {
            throw new DomainBadRequestException("error.skill.metadata.frontmatter.missingEnd");
        }

        String yamlContent = trimmedContent.substring(firstDelimiterEnd + 1, secondDelimiterStart).trim();
        String body = trimmedContent.substring(secondDelimiterStart + FRONTMATTER_DELIMITER.length()).trim();

        Map<String, Object> frontmatter;
        try {
            frontmatter = parseFrontmatter(yamlContent);
        } catch (DomainBadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainBadRequestException("error.skill.metadata.yaml.invalid", e.getMessage());
        }

        String name = extractRequiredField(frontmatter, "name");
        String description = extractRequiredField(frontmatter, "description");
        String version = extractOptionalField(frontmatter, "version");

        return new SkillMetadata(name, description, version, body, frontmatter);
    }

    private Map<String, Object> parseFrontmatter(String yamlContent) {
        try {
            Yaml yaml = new Yaml();
            Object parsed = yaml.load(yamlContent);
            if (!(parsed instanceof Map)) {
                throw new DomainBadRequestException("error.skill.metadata.yaml.notMap");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parsed;
            return map;
        } catch (DomainBadRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            Map<String, Object> fallback = parseLooseFrontmatter(yamlContent);
            if (!fallback.isEmpty()) {
                return fallback;
            }
            throw exception;
        }
    }

    private Map<String, Object> parseLooseFrontmatter(String yamlContent) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String rawLine : yamlContent.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int separatorIndex = line.indexOf(':');
            if (separatorIndex <= 0) {
                continue;
            }

            String key = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + 1).trim();
            if (key.isEmpty()) {
                continue;
            }

            values.put(key, stripWrappingQuotes(value));
        }
        return values;
    }

    private String stripWrappingQuotes(String value) {
        if (value.length() >= 2) {
            boolean wrappedInDoubleQuotes = value.startsWith("\"") && value.endsWith("\"");
            boolean wrappedInSingleQuotes = value.startsWith("'") && value.endsWith("'");
            if (wrappedInDoubleQuotes || wrappedInSingleQuotes) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private String extractRequiredField(Map<String, Object> frontmatter, String fieldName) {
        Object value = frontmatter.get(fieldName);
        if (value == null) {
            throw new DomainBadRequestException("error.skill.metadata.requiredField.missing", fieldName);
        }
        return value.toString();
    }

    private String extractOptionalField(Map<String, Object> frontmatter, String fieldName) {
        Object value = frontmatter.get(fieldName);
        return value == null ? null : value.toString();
    }
}
