package com.iflytek.skillhub.domain.organization;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;

/** One-time plaintext proof plus the digest that may be persisted. */
public final class OrganizationDomainChallengeToken {

    private final String plaintext;
    private final String digest;

    private OrganizationDomainChallengeToken(String plaintext, String digest) {
        this.plaintext = requireNonBlank(plaintext, "plaintext");
        this.digest = requireNonBlank(digest, "digest");
    }

    public static OrganizationDomainChallengeToken issued(String plaintext, String digest) {
        return new OrganizationDomainChallengeToken(plaintext, digest);
    }

    public String plaintext() {
        return plaintext;
    }

    public String digest() {
        return digest;
    }

    @Override
    public String toString() {
        return "OrganizationDomainChallengeToken[redacted]";
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainBadRequestException(
                    "error.organization.domain.challenge.invalid",
                    field
            );
        }
        return value;
    }
}
