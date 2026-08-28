package com.iflytek.skillhub.domain.namespace;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

/**
 * Renders the operator-configured name templates for a personal namespace.
 *
 * <p>Templates use {@code ${placeholder}} syntax. Unknown placeholders are left untouched so a typo
 * shows up in the resulting name instead of silently disappearing.
 */
final class PersonalNamespaceNaming {

    /**
     * Longest slug {@link SlugValidator} accepts, minus room for a de-duplication suffix.
     */
    private static final int SLUG_BASE_BUDGET = 59;

    /**
     * Matches the {@code display_name} column width.
     */
    private static final int DISPLAY_NAME_LIMIT = 128;

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-z_]+)}");

    private PersonalNamespaceNaming() {
    }

    /**
     * Substitutes placeholders in {@code template} using {@code owner}.
     */
    static String render(String template, PersonalNamespaceOwner owner) {
        if (template == null || template.isBlank()) {
            return "";
        }
        Map<String, String> values = Map.of(
                PersonalNamespaceSettings.PLACEHOLDER_USERNAME, username(owner),
                PersonalNamespaceSettings.PLACEHOLDER_EMAIL_PREFIX, emailPrefix(owner),
                PersonalNamespaceSettings.PLACEHOLDER_USER_ID, blankToEmpty(owner.userId()),
                PersonalNamespaceSettings.PLACEHOLDER_RANDOM, randomSuffix());

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String replacement = values.get(matcher.group(1));
            matcher.appendReplacement(rendered,
                    Matcher.quoteReplacement(replacement != null ? replacement : matcher.group()));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    /**
     * Renders {@code template} into a slug base, falling back to the user id when the template
     * cannot produce anything usable.
     */
    static String slugBase(String template, PersonalNamespaceOwner owner) {
        String candidate = truncateSlug(SlugValidator.normalize(render(template, owner)));
        if (candidate.length() >= 2) {
            return candidate;
        }
        String fallback = truncateSlug(SlugValidator.normalize(owner.userId()));
        return fallback.length() >= 2 ? fallback : "user";
    }

    /**
     * Renders {@code template} into a display name, falling back to the slug that was chosen.
     */
    static String displayName(String template, PersonalNamespaceOwner owner, String slug) {
        String rendered = render(template, owner).trim();
        if (rendered.isEmpty()) {
            return slug;
        }
        return rendered.length() > DISPLAY_NAME_LIMIT ? rendered.substring(0, DISPLAY_NAME_LIMIT) : rendered;
    }

    private static String username(PersonalNamespaceOwner owner) {
        if (owner.username() != null && !owner.username().isBlank()) {
            return owner.username().trim();
        }
        String emailPrefix = emailPrefix(owner);
        return !emailPrefix.isEmpty() ? emailPrefix : blankToEmpty(owner.userId());
    }

    private static String emailPrefix(PersonalNamespaceOwner owner) {
        String email = owner.email();
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        return (at > 0 ? email.substring(0, at) : email).trim();
    }

    private static String truncateSlug(String slug) {
        if (slug.length() <= SLUG_BASE_BUDGET) {
            return slug;
        }
        return SlugValidator.normalize(slug.substring(0, SLUG_BASE_BUDGET));
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toLowerCase();
    }
}
