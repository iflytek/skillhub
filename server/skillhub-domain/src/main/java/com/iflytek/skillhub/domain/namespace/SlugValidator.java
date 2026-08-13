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
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[\\p{L}\\p{N}\\p{So}][\\p{L}\\p{N}\\p{So}\\-]*[\\p{L}\\p{N}\\p{So}]$");
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
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
        if (UPPERCASE_PATTERN.matcher(slug).find()) {
            throw new DomainBadRequestException("error.slug.pattern");
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
        String slug = normalize(raw);
        validate(slug);
        return slug;
    }

    /**
     * Applies the slug character rules without asserting the result is usable.
     *
     * <p>Callers that generate candidate slugs — rather than accepting one from a user — need to
     * inspect and adjust the result (append a suffix, truncate) before validating it.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase()
                .replaceAll("[^\\p{L}\\p{N}\\p{So}]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "")
                .replaceAll("-{2,}", "-");
    }

    /**
     * Returns whether {@code slug} would pass {@link #validate(String)}.
     */
    public static boolean isValid(String slug) {
        try {
            validate(slug);
            return true;
        } catch (DomainBadRequestException e) {
            return false;
        }
    }
}
