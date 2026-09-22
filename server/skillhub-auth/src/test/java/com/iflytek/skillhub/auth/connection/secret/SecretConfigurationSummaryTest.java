package com.iflytek.skillhub.auth.connection.secret;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SecretConfigurationSummaryTest {

    @Test
    void managementDtoAndToStringExposeOnlyNonSecretMetadata() throws Exception {
        SecretConfigurationSummary summary = SecretConfigurationSummary.configured(
                Instant.parse("2026-09-08T06:00:00Z"),
                Instant.parse("2026-09-08T07:00:00Z")
        );

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(summary);

        assertThat(json)
                .contains("\"configured\":true", "\"updatedAt\"", "\"previousValidUntil\"")
                .doesNotContain("value", "ciphertext", "nonce", "keyId", "clientSecret");
        assertThat(summary.toString())
                .doesNotContain("ciphertext", "nonce", "keyId", "clientSecret");
    }
}
