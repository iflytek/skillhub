package com.iflytek.skillhub.auth.connection.oidc;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRevisionSnapshot;
import com.iflytek.skillhub.auth.connection.control.LoginConnectionRuntimeSnapshotDecoder;
import com.iflytek.skillhub.auth.connection.core.AdapterCapability;
import com.iflytek.skillhub.auth.connection.core.AdapterContractVersion;
import com.iflytek.skillhub.auth.connection.core.AdapterDescriptor;
import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.core.ConnectionKind;
import com.iflytek.skillhub.auth.connection.core.ConnectionUnavailableException;
import com.iflytek.skillhub.auth.connection.core.InteractionModel;
import com.iflytek.skillhub.auth.connection.core.LoginConnectionRuntimeSnapshot;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Strictly decodes immutable OIDC revisions; unknown or incompatible fields fail closed. */
public final class OidcLoginConnectionRuntimeSnapshotMaterializer
        implements LoginConnectionRuntimeSnapshotDecoder {

    private static final int MAX_JSON_BYTES = 16 * 1024;
    private static final Set<String> CONFIG_FIELDS = Set.of("issuer", "clientId", "scopes");

    private final ObjectMapper objectMapper;

    public OidcLoginConnectionRuntimeSnapshotMaterializer() {
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(8)
                        .maxStringLength(8 * 1024)
                        .maxNumberLength(32)
                        .build())
                .build();
        factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.objectMapper = new ObjectMapper(factory);
    }

    static AdapterDescriptor supportedDescriptor() {
        return new AdapterDescriptor(
                OidcLoginConnectionRuntimeConfig.ADAPTER_KEY,
                new AdapterContractVersion(1, 0),
                ConnectionKind.LOGIN,
                1,
                Optional.of(InteractionModel.REDIRECT),
                Set.of(
                        AdapterCapability.IDENTITY_ASSERTION,
                        AdapterCapability.VERIFIED_EMAIL_ASSERTION,
                        AdapterCapability.PROFILE_ATTRIBUTE_ASSERTION
                )
        );
    }

    @Override
    public AdapterKey adapterKey() {
        return OidcLoginConnectionRuntimeConfig.ADAPTER_KEY;
    }

    @Override
    public int contractMajor() {
        return supportedDescriptor().contractVersion().major();
    }

    @Override
    public LoginConnectionRuntimeSnapshot<?> decode(
            LoginConnectionRevisionSnapshot revision
    ) {
        try {
            AdapterDescriptor descriptor = supportedDescriptor();
            requireCompatible(revision, descriptor);
            JsonNode config = parseObject(revision.typedConfigJson());
            if (!fieldNames(config).equals(CONFIG_FIELDS)) {
                throw new ConnectionUnavailableException();
            }
            Set<String> scopes = new LinkedHashSet<>();
            JsonNode scopeValues = config.get("scopes");
            if (scopeValues == null || !scopeValues.isArray()) {
                throw new ConnectionUnavailableException();
            }
            scopeValues.forEach(scope -> {
                if (!scope.isTextual()) {
                    throw new ConnectionUnavailableException();
                }
                scopes.add(scope.textValue());
            });
            return new LoginConnectionRuntimeSnapshot<>(
                    revision.organizationId(),
                    revision.connectionId(),
                    revision.publicHandle(),
                    revision.revision(),
                    descriptor,
                    new OidcLoginConnectionRuntimeConfig(
                            new OidcIssuer(requiredText(config, "issuer")),
                            requiredText(config, "clientId"),
                            scopes
                    )
            );
        } catch (ConnectionUnavailableException unavailable) {
            throw unavailable;
        } catch (RuntimeException invalidRevision) {
            throw new ConnectionUnavailableException();
        }
    }

    private void requireCompatible(
            LoginConnectionRevisionSnapshot revision,
            AdapterDescriptor descriptor
    ) {
        AdapterContractVersion contract = parseContract(revision.adapterContractVersion());
        Set<AdapterCapability> capabilities = parseCapabilities(revision.capabilitiesJson());
        if (!revision.adapterKey().equals(descriptor.adapterKey())
                || contract.major() != descriptor.contractVersion().major()
                || contract.minor() > descriptor.contractVersion().minor()
                || revision.configSchemaVersion() != descriptor.configSchemaVersion()
                || !capabilities.equals(descriptor.capabilities())) {
            throw new ConnectionUnavailableException();
        }
    }

    private Set<AdapterCapability> parseCapabilities(String json) {
        JsonNode value = parse(json);
        if (!value.isArray()) {
            throw new ConnectionUnavailableException();
        }
        Set<AdapterCapability> capabilities = new LinkedHashSet<>();
        value.forEach(capability -> {
            if (!capability.isTextual()) {
                throw new ConnectionUnavailableException();
            }
            capabilities.add(AdapterCapability.valueOf(capability.textValue()));
        });
        return Set.copyOf(capabilities);
    }

    private JsonNode parseObject(String json) {
        JsonNode value = parse(json);
        if (!value.isObject()) {
            throw new ConnectionUnavailableException();
        }
        return value;
    }

    private JsonNode parse(String json) {
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            throw new ConnectionUnavailableException();
        }
        try {
            return objectMapper.readTree(json);
        } catch (java.io.IOException invalidJson) {
            throw new ConnectionUnavailableException();
        }
    }

    private static Set<String> fieldNames(JsonNode object) {
        Set<String> fields = new LinkedHashSet<>();
        object.fieldNames().forEachRemaining(fields::add);
        return Set.copyOf(fields);
    }

    private static String requiredText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual()) {
            throw new ConnectionUnavailableException();
        }
        return value.textValue();
    }

    private static AdapterContractVersion parseContract(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 2) {
            throw new ConnectionUnavailableException();
        }
        return new AdapterContractVersion(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1])
        );
    }
}
