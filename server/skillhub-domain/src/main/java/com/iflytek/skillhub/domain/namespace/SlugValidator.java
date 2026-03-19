package com.iflytek.skillhub.domain.namespace;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates and normalizes namespace-style slugs used across public identifiers.
 */
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
            throw new DomainBadRequestException("error.slug.blank");
        }
        if (slug.length() < MIN_LENGTH || slug.length() > MAX_LENGTH) {
            throw new DomainBadRequestException("error.slug.length", MIN_LENGTH, MAX_LENGTH);
        }
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new DomainBadRequestException("error.slug.pattern");
        }
        if (slug.contains("--")) {
            throw new DomainBadRequestException("error.slug.doubleHyphen");
        }
        if (RESERVED_SLUGS.contains(slug)) {
            throw new DomainBadRequestException("error.slug.reserved", slug);
        }
    }

    public static String slugify(String raw) {
        if (raw == null) {
            throw new DomainBadRequestException("error.slug.blank");
        }
        String slug = raw.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "")
                .replaceAll("-{2,}", "-");
        validate(slug);
        return slug;
    }
}
