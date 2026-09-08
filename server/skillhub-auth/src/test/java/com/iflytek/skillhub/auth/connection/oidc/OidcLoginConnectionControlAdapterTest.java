package com.iflytek.skillhub.auth.connection.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.auth.connection.control.LoginConnectionConfigurationInput;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRevisionSnapshot;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRuntimeSnapshotMaterializer;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionTestException;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionTestFailureReason;
import com.iflytek.skillhub.auth.connection.control.PreparedLoginConnectionRevision;
import com.iflytek.skillhub.auth.connection.core.LoginConnectionRuntimeSnapshot;
import com.iflytek.skillhub.auth.connection.core.ConnectionHandle;
import com.iflytek.skillhub.auth.connection.secret.SecretMaterial;
import com.iflytek.skillhub.auth.connection.secret.SecretMaterialResolver;
import com.iflytek.skillhub.auth.connection.secret.SecretPurpose;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OidcLoginConnectionControlAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

    @Test
    void compilesOnlyTheVersionedNonSecretOidcConfiguration() {
        OidcLoginConnectionControlAdapter adapter = adapter(
                mock(LoginConnectionRuntimeSnapshotMaterializer.class),
                mock(SecretMaterialResolver.class),
                mock(OidcConnectionMetadataProbe.class)
        );

        PreparedLoginConnectionRevision prepared = adapter.prepare(
                new LoginConnectionConfigurationInput(Map.of(
                        "issuer", "https://id.example.com",
                        "clientId", "skillhub",
                        "scopes", Set.of("openid", "email")
                ))
        );

        assertThat(prepared.adapterDescriptor()).isEqualTo(
                OidcRedirectAuthenticationAdapter.supportedDescriptor()
        );
        assertThat(prepared.typedConfigJson()).contains(
                "https://id.example.com", "skillhub", "openid", "email"
        );
        assertThat(prepared.typedConfigJson()).doesNotContain("secret");
        assertThat(prepared.secretPurpose()).contains(SecretPurpose.LOGIN_CLIENT_SECRET);
    }

    @Test
    void rejectsUnknownConfigurationFields() {
        OidcLoginConnectionControlAdapter adapter = adapter(
                mock(LoginConnectionRuntimeSnapshotMaterializer.class),
                mock(SecretMaterialResolver.class),
                mock(OidcConnectionMetadataProbe.class)
        );

        assertThatThrownBy(() -> adapter.prepare(new LoginConnectionConfigurationInput(Map.of(
                "issuer", "https://id.example.com",
                "clientId", "skillhub",
                "scopes", Set.of("openid"),
                "clientSecret", "must-not-be-accepted-here"
        )))).hasMessage("error.loginConnection.configuration.invalid");
    }

    @Test
    @SuppressWarnings("unchecked")
    void preflightVerifiesSecretAndMetadataButNeverReturnsSecretMaterial() {
        LoginConnectionRuntimeSnapshotMaterializer materializer =
                mock(LoginConnectionRuntimeSnapshotMaterializer.class);
        SecretMaterialResolver secrets = mock(SecretMaterialResolver.class);
        OidcConnectionMetadataProbe metadata = mock(OidcConnectionMetadataProbe.class);
        LoginConnectionRevisionSnapshot revision = revision();
        LoginConnectionRuntimeSnapshot<OidcLoginConnectionRuntimeConfig> runtime = runtime();
        doReturn(runtime).when(materializer).materialize(revision);
        given(secrets.resolve(
                runtime.secretReference().orElseThrow(),
                SecretPurpose.LOGIN_CLIENT_SECRET,
                NOW
        )).willReturn(SecretMaterial.copyOf("top-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        adapter(materializer, secrets, metadata).testProbe().verify(revision);
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsMetadataFailureToABoundedSafeCategory() {
        LoginConnectionRuntimeSnapshotMaterializer materializer =
                mock(LoginConnectionRuntimeSnapshotMaterializer.class);
        SecretMaterialResolver secrets = mock(SecretMaterialResolver.class);
        OidcConnectionMetadataProbe metadata = mock(OidcConnectionMetadataProbe.class);
        LoginConnectionRevisionSnapshot revision = revision();
        LoginConnectionRuntimeSnapshot<OidcLoginConnectionRuntimeConfig> runtime = runtime();
        doReturn(runtime).when(materializer).materialize(revision);
        given(secrets.resolve(
                runtime.secretReference().orElseThrow(),
                SecretPurpose.LOGIN_CLIENT_SECRET,
                NOW
        )).willReturn(SecretMaterial.copyOf("top-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        org.mockito.BDDMockito.willThrow(new OidcMetadataUnavailableException()).given(metadata).verify(
                new OidcMetadataCacheKey("org-1", "connection-1", 1),
                runtime.config().issuer(),
                NOW
        );

        assertThatThrownBy(() -> adapter(materializer, secrets, metadata)
                .testProbe().verify(revision))
                .isInstanceOf(LoginConnectionTestException.class)
                .extracting(error -> ((LoginConnectionTestException) error).reason())
                .isEqualTo(LoginConnectionTestFailureReason.METADATA);
    }

    private static OidcLoginConnectionControlAdapter adapter(
            LoginConnectionRuntimeSnapshotMaterializer materializer,
            SecretMaterialResolver secrets,
            OidcConnectionMetadataProbe metadata
    ) {
        return new OidcLoginConnectionControlAdapter(
                materializer,
                secrets,
                metadata,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static LoginConnectionRuntimeSnapshot<OidcLoginConnectionRuntimeConfig> runtime() {
        return new LoginConnectionRuntimeSnapshot<>(
                Optional.of("org-1"),
                "connection-1",
                new ConnectionHandle("login-test-handle"),
                1,
                OidcRedirectAuthenticationAdapter.supportedDescriptor(),
                Optional.of(new com.iflytek.skillhub.auth.connection.secret.SecretReference(
                        "org-1", "connection-1", SecretPurpose.LOGIN_CLIENT_SECRET, 1
                )),
                new OidcLoginConnectionRuntimeConfig(
                        new OidcIssuer("https://id.example.com"),
                        "skillhub",
                        Set.of("openid")
                )
        );
    }

    private static LoginConnectionRevisionSnapshot revision() {
        return new LoginConnectionRevisionSnapshot(
                Optional.of("org-1"),
                "connection-1",
                new ConnectionHandle("login-test-handle"),
                "revision-1",
                1,
                OidcLoginConnectionRuntimeConfig.ADAPTER_KEY,
                "1.0",
                1,
                "[\"IDENTITY_ASSERTION\"]",
                "{\"issuer\":\"https://id.example.com\",\"clientId\":\"skillhub\",\"scopes\":[\"openid\"]}",
                Optional.of(new com.iflytek.skillhub.auth.connection.secret.SecretReference(
                        "org-1", "connection-1", SecretPurpose.LOGIN_CLIENT_SECRET, 1
                )),
                com.iflytek.skillhub.auth.federation.core.IdentityCorrelationPolicySettings.disabled()
        );
    }
}
