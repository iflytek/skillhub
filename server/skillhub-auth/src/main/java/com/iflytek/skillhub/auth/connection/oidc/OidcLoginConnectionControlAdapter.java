package com.iflytek.skillhub.auth.connection.oidc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionConfigurationInput;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionControlAdapter;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRuntimeSnapshotMaterializer;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionTestException;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionTestFailureReason;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionTestProbe;
import com.iflytek.skillhub.auth.connection.control.PreparedLoginConnectionRevision;
import com.iflytek.skillhub.auth.connection.core.AdapterDescriptor;
import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.core.AdapterContractVersion;
import com.iflytek.skillhub.auth.connection.core.ConnectionKind;
import com.iflytek.skillhub.auth.connection.core.ConnectionUnavailableException;
import com.iflytek.skillhub.auth.connection.core.InteractionModel;
import com.iflytek.skillhub.auth.connection.core.LoginConnectionRuntimeSnapshot;
import com.iflytek.skillhub.auth.connection.secret.SecretMaterial;
import com.iflytek.skillhub.auth.connection.secret.SecretMaterialResolver;
import com.iflytek.skillhub.auth.connection.secret.SecretMaterialUnavailableException;
import com.iflytek.skillhub.auth.connection.secret.SecretPurpose;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** OIDC control-plane edge: validates drafts and performs a non-mutating server preflight. */
public final class OidcLoginConnectionControlAdapter implements LoginConnectionControlAdapter {

    private static final Set<String> FIELDS = Set.of("issuer", "clientId", "scopes");
    private static final int MAX_SCOPES = 16;

    private final LoginConnectionRuntimeSnapshotMaterializer materializer;
    private final SecretMaterialResolver secrets;
    private final OidcConnectionMetadataProbe metadata;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public OidcLoginConnectionControlAdapter(
            LoginConnectionRuntimeSnapshotMaterializer materializer,
            SecretMaterialResolver secrets,
            OidcConnectionMetadataProbe metadata,
            Clock clock
    ) {
        this.materializer = Objects.requireNonNull(materializer, "materializer");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public AdapterKey adapterKey() {
        return OidcLoginConnectionRuntimeConfig.ADAPTER_KEY;
    }

    @Override
    public Optional<SecretPurpose> secretPurpose() {
        return Optional.of(SecretPurpose.LOGIN_CLIENT_SECRET);
    }

    @Override
    public PreparedLoginConnectionRevision prepare(
            LoginConnectionConfigurationInput configuration
    ) {
        try {
            Map<String, Object> values = Objects.requireNonNull(configuration, "configuration")
                    .values();
            if (!values.keySet().equals(FIELDS)) {
                throw invalid();
            }
            OidcIssuer issuer = new OidcIssuer(requiredString(values, "issuer"));
            String clientId = requiredString(values, "clientId");
            List<String> scopes = scopes(values.get("scopes"));
            AdapterDescriptor descriptor = supportedDescriptor();
            Map<String, Object> persisted = new LinkedHashMap<>();
            persisted.put("issuer", issuer.value());
            persisted.put("clientId", clientId);
            persisted.put("scopes", scopes);
            List<String> capabilities = descriptor.capabilities().stream()
                    .map(Enum::name)
                    .sorted()
                    .toList();
            return new PreparedLoginConnectionRevision(
                    descriptor,
                    objectMapper.writeValueAsString(capabilities),
                    objectMapper.writeValueAsString(persisted),
                    Optional.of(SecretPurpose.LOGIN_CLIENT_SECRET)
            );
        } catch (DomainBadRequestException invalid) {
            throw invalid;
        } catch (JsonProcessingException | RuntimeException invalid) {
            throw invalid();
        }
    }

    @Override
    public LoginConnectionTestProbe testProbe() {
        return candidate -> {
            LoginConnectionRuntimeSnapshot<OidcLoginConnectionRuntimeConfig> runtime =
                    requireOidcRuntime(candidate);
            var reference = candidate.secretReference().orElseThrow(() -> failure(
                    LoginConnectionTestFailureReason.CREDENTIAL
            ));
            try (SecretMaterial ignored = secrets.resolve(
                    reference,
                    SecretPurpose.LOGIN_CLIENT_SECRET,
                    clock.instant()
            )) {
                // Successful decryption proves that the configured binding is usable locally.
            } catch (SecretMaterialUnavailableException unavailable) {
                throw failure(LoginConnectionTestFailureReason.CREDENTIAL);
            }
            try {
                metadata.verify(
                        new OidcMetadataCacheKey(
                                runtime.organizationId().orElse("@platform"),
                                runtime.connectionId(),
                                runtime.revision()
                        ),
                        runtime.config().issuer(),
                        clock.instant()
                );
            } catch (OidcMetadataUnavailableException unavailable) {
                throw failure(LoginConnectionTestFailureReason.METADATA);
            } catch (RuntimeException unavailable) {
                throw failure(LoginConnectionTestFailureReason.UPSTREAM);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private LoginConnectionRuntimeSnapshot<OidcLoginConnectionRuntimeConfig> requireOidcRuntime(
            com.iflytek.skillhub.auth.connection.control.LoginConnectionRevisionSnapshot candidate
    ) {
        try {
            LoginConnectionRuntimeSnapshot<?> runtime = materializer.materialize(candidate);
            if (!(runtime.config() instanceof OidcLoginConnectionRuntimeConfig)
                    || !runtime.adapterKey().equals(adapterKey())) {
                throw new ConnectionUnavailableException();
            }
            return (LoginConnectionRuntimeSnapshot<OidcLoginConnectionRuntimeConfig>) runtime;
        } catch (ConnectionUnavailableException invalid) {
            throw failure(LoginConnectionTestFailureReason.CONFIGURATION);
        } catch (RuntimeException invalid) {
            throw failure(LoginConnectionTestFailureReason.CONFIGURATION);
        }
    }

    private static List<String> scopes(Object raw) {
        if (!(raw instanceof java.util.Collection<?> values)
                || values.isEmpty()
                || values.size() > MAX_SCOPES) {
            throw invalid();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (Object value : values) {
            if (!(value instanceof String text)) {
                throw invalid();
            }
            String scope = requireText(text);
            if (scope.length() > 128) {
                throw invalid();
            }
            normalized.add(scope);
        }
        if (!normalized.contains("openid")) {
            throw invalid();
        }
        List<String> ordered = new ArrayList<>(normalized);
        ordered.sort(Comparator.naturalOrder());
        return List.copyOf(ordered);
    }

    private static String requiredString(Map<String, Object> values, String field) {
        Object value = values.get(field);
        if (!(value instanceof String text)) {
            throw invalid();
        }
        String normalized = requireText(text);
        if (normalized.length() > 512) {
            throw invalid();
        }
        return normalized;
    }

    private static String requireText(String value) {
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw invalid();
        }
        return normalized;
    }

    private static DomainBadRequestException invalid() {
        return new DomainBadRequestException("error.loginConnection.configuration.invalid");
    }

    private static LoginConnectionTestException failure(
            LoginConnectionTestFailureReason reason
    ) {
        return new LoginConnectionTestException(reason);
    }

    private static AdapterDescriptor supportedDescriptor() {
        return new AdapterDescriptor(
                OidcLoginConnectionRuntimeConfig.ADAPTER_KEY,
                new AdapterContractVersion(1, 0),
                ConnectionKind.LOGIN,
                1,
                Optional.of(InteractionModel.REDIRECT),
                Set.of(
                        com.iflytek.skillhub.auth.connection.core.AdapterCapability.IDENTITY_ASSERTION,
                        com.iflytek.skillhub.auth.connection.core.AdapterCapability.VERIFIED_EMAIL_ASSERTION,
                        com.iflytek.skillhub.auth.connection.core.AdapterCapability.PROFILE_ATTRIBUTE_ASSERTION
                )
        );
    }
}
