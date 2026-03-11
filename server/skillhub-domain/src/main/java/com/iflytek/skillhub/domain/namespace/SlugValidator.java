package com.iflytek.skillhub.domain.namespace;

import java.util.Set;
import java.util.regex.Pattern;

public class SlugValidator {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 64;
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");
    private static final Set<String> RESERVED_SLUGS = Set.of(
            "admin", "api", "dashboard", "search", "auth",
            "me", "global", "system", "static", "assets", "health"
    );

    public static void validate(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Slug cannot be null or blank");
        }
        if (slug.length() < MIN_LENGTH || slug.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("Slug length must be between %d and %d characters", MIN_LENGTH, MAX_LENGTH));
        }
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException(
                    "Slug must contain only lowercase alphanumeric characters and hyphens, " +
                    "and must start and end with an alphanumeric character");
        }
        if (slug.contains("--")) {
            throw new IllegalArgumentException("Slug cannot contain consecutive hyphens");
        }
        if (RESERVED_SLUGS.contains(slug)) {
            throw new IllegalArgumentException("Slug '" + slug + "' is reserved and cannot be used");
        }
    }
}
