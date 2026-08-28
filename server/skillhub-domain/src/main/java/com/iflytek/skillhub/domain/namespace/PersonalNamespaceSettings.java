package com.iflytek.skillhub.domain.namespace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Operator-controlled policy for giving each new account its own namespace.
 *
 * @param slugTemplate        template for the namespace slug, e.g. {@code ${username}-space}
 * @param displayNameTemplate template for the namespace display name
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PersonalNamespaceSettings(
        boolean enabled,
        String slugTemplate,
        String displayNameTemplate) {

    /**
     * Supported placeholders, in the order they are documented to operators.
     */
    public static final String PLACEHOLDER_USERNAME = "username";
    public static final String PLACEHOLDER_EMAIL_PREFIX = "email_prefix";
    public static final String PLACEHOLDER_USER_ID = "user_id";
    public static final String PLACEHOLDER_RANDOM = "random";
}
