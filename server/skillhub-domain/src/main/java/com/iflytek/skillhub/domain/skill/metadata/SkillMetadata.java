package com.iflytek.skillhub.domain.skill.metadata;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.Map;

public record SkillMetadata(
    String name,
    String description,
    String version,
    String body,
    Map<String, Object> frontmatter,
    String displayNameOverride,
    String shortDescriptionOverride,
    String overviewOverride,
    String presentationInheritedFrom
) {
    public SkillMetadata(String name, String description, String version, String body, Map<String, Object> frontmatter) {
        this(name, description, version, body, frontmatter, null, null, null, null);
    }

    public SkillMetadata(String name, String description, String version, String body, Map<String, Object> frontmatter,
                         String displayNameOverride) {
        this(name, description, version, body, frontmatter, displayNameOverride, null, null, null);
    }

    public SkillMetadata withPresentation(String shortDescription, String overview, String inheritedFrom) {
        String shortValue = normalize(shortDescription);
        String overviewValue = normalize(overview);
        if (shortValue != null && shortValue.length() > 200) {
            throw new DomainBadRequestException("error.skill.shortDescription.tooLong", 200);
        }
        if (overviewValue != null && overviewValue.length() > 20000) {
            throw new DomainBadRequestException("error.skill.overview.tooLong", 20000);
        }
        return new SkillMetadata(name, description, version, body, frontmatter, displayNameOverride,
                shortValue, overviewValue, inheritedFrom);
    }

    private static String normalize(String value) {
        return value == null || value.strip().isEmpty() ? null : value.strip();
    }

    /** Explicit portal input is validated before it becomes part of the version snapshot. */
    public SkillMetadata withDisplayNameOverride(String value) {
        String override = value == null ? null : value.strip();
        if (override != null && override.length() > 200) {
            throw new DomainBadRequestException(
                    "error.skill.displayName.tooLong", 200);
        }
        return new SkillMetadata(name, description, version, body, frontmatter,
                override == null || override.isEmpty() ? null : override);
    }

    public String shortDescription() {
        return shortDescriptionOverride != null ? shortDescriptionOverride
                : nestedText(frontmatter, "short-description", description);
    }

    public String overview() {
        String packageOverview = nestedText(frontmatter, "overview", null);
        return overviewOverride != null ? overviewOverride : (packageOverview != null ? packageOverview : body);
    }

    private static String nestedText(Map<String, Object> values, String key, String fallback) {
        if (values != null && values.get("metadata") instanceof Map<?, ?> metadata
                && metadata.get(key) instanceof String text && !text.strip().isEmpty()) {
            return text.strip();
        }
        return fallback;
    }

    /** Human-readable label; unsupported legacy snapshot values fall back to the package name. */
    public String displayName() {
        if (displayNameOverride != null && !displayNameOverride.isBlank()
                && displayNameOverride.strip().length() <= 200) {
            return displayNameOverride.strip();
        }
        return resolveDisplayName(name, frontmatter);
    }

    private static String resolveDisplayName(String name, Map<String, Object> frontmatter) {
        if (frontmatter == null || !(frontmatter.get("metadata") instanceof Map<?, ?> metadata)) {
            return name;
        }
        Object value = metadata.get("display-name");
        if (value == null) {
            return name;
        }
        if (!(value instanceof String text)) {
            return name;
        }
        String displayName = text.strip();
        if (displayName.isEmpty()) {
            return name;
        }
        if (displayName.length() > 200) {
            return name;
        }
        return displayName;
    }
}
