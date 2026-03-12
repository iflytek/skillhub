package com.iflytek.skillhub.compat;

import org.springframework.stereotype.Component;

@Component
public class CanonicalSlugMapper {

    private static final String GLOBAL_NAMESPACE = "global";
    private static final String SEPARATOR = "--";

    /**
     * Convert namespace and slug to canonical slug format.
     * If namespace is "global", return slug as-is.
     * Otherwise, return "namespace--slug".
     */
    public String toCanonical(String namespace, String slug) {
        if (GLOBAL_NAMESPACE.equals(namespace)) {
            return slug;
        }
        return namespace + SEPARATOR + slug;
    }

    /**
     * Convert canonical slug back to namespace and slug.
     * If contains "--", split into namespace and slug.
     * Otherwise, treat as global namespace.
     */
    public SkillCoordinate fromCanonical(String canonicalSlug) {
        int separatorIndex = canonicalSlug.indexOf(SEPARATOR);
        if (separatorIndex > 0) {
            String namespace = canonicalSlug.substring(0, separatorIndex);
            String slug = canonicalSlug.substring(separatorIndex + SEPARATOR.length());
            return new SkillCoordinate(namespace, slug);
        }
        return new SkillCoordinate(GLOBAL_NAMESPACE, canonicalSlug);
    }
}
