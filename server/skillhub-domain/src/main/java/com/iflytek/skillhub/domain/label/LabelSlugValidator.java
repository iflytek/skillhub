package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.Locale;
import java.util.regex.Pattern;

public final class LabelSlugValidator {

    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 64;
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");

    private LabelSlugValidator() {
    }

    public static String normalize(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new DomainBadRequestException("error.slug.blank");
        }
        String normalized = slug.trim().toLowerCase(Locale.ROOT);
        validate(normalized);
        return normalized;
    }

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
    }
}
