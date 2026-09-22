package com.iflytek.skillhub.auth.connection.control;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.secret.SecretPurpose;
import com.iflytek.skillhub.auth.connection.secret.SecretReference;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StoredLoginConnectionRevisionSecretReferenceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T06:00:00Z");

    @Test
    void materializationExpandsBindingVersionIntoTenantBoundClientSecretReference() {
        LoginConnection connection = LoginConnection.createOrganization(
                "organization-1",
                "Corporate OIDC",
                new AdapterKey("oidc"),
                "identity-admin",
                NOW
        );
        StoredLoginConnectionRevision revision = revision(connection, 7L);

        assertThat(revision.snapshot(connection).secretReference()).get().satisfies(reference -> {
            assertThat(reference.scopeKey()).isEqualTo("organization-1");
            assertThat(reference.connectionId()).isEqualTo(connection.getId());
            assertThat(reference.purpose()).isEqualTo(SecretPurpose.LOGIN_CLIENT_SECRET);
            assertThat(reference.bindingVersion()).isEqualTo(7);
        });
        assertThat(revision.snapshot(connection).toString())
                .contains("secretReference=present")
                .doesNotContain("bindingVersion=7");
    }

    @Test
    void platformRevisionMaterializesTheReservedPlatformSecretScope() {
        LoginConnection platform = LoginConnection.createPlatform(
                "github",
                "GitHub",
                new AdapterKey("oauth"),
                "platform-admin",
                NOW
        );

        assertThat(revision(platform, 1L).snapshot(platform).secretReference())
                .get()
                .extracting(reference -> reference.scopeKey())
                .isEqualTo(SecretReference.PLATFORM_SCOPE_KEY);
    }

    private static StoredLoginConnectionRevision revision(
            LoginConnection connection,
            Long secretBindingVersion
    ) {
        return StoredLoginConnectionRevision.create(
                connection.getId(),
                1,
                "1.0",
                1,
                "[\"IDENTITY_ASSERTION\"]",
                "{\"issuer\":\"https://id.example.com\"}",
                secretBindingVersion,
                "identity-admin",
                NOW
        );
    }
}
