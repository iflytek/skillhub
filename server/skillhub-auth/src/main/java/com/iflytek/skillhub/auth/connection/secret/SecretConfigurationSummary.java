package com.iflytek.skillhub.auth.connection.secret;

import java.time.Instant;
import java.util.Objects;

/** Redacted management projection safe for repository and future API boundaries. */
public record SecretConfigurationSummary(
        boolean configured,
        Instant updatedAt,
        Instant previousValidUntil
) {

    public SecretConfigurationSummary {
        if (configured) {
            Objects.requireNonNull(updatedAt, "updatedAt");
        } else if (updatedAt != null || previousValidUntil != null) {
            throw new IllegalArgumentException("Unconfigured Secret cannot expose timestamps");
        }
    }

    public static SecretConfigurationSummary configured(
            Instant updatedAt,
            Instant previousValidUntil
    ) {
        return new SecretConfigurationSummary(
                true,
                Objects.requireNonNull(updatedAt, "updatedAt"),
                previousValidUntil
        );
    }

    public static SecretConfigurationSummary notConfigured() {
        return new SecretConfigurationSummary(false, null, null);
    }

    @Override
    public String toString() {
        return "SecretConfigurationSummary[configured=" + configured
                + ", updatedAt=" + updatedAt
                + ", previousValidUntil=" + previousValidUntil + "]";
    }
}
