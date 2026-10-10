package com.iflytek.skillhub.auth.connection.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Validated catalog of built-in descriptors keyed by stable adapter key, contract and schema version. */
public final class AdapterDescriptorRegistry {

    private static final Set<Integer> SUPPORTED_CONTRACT_MAJORS = Set.of(1);
    private static final Set<AdapterCapability> LOGIN_CAPABILITIES = Set.of(
            AdapterCapability.IDENTITY_ASSERTION,
            AdapterCapability.VERIFIED_EMAIL_ASSERTION,
            AdapterCapability.PROFILE_ATTRIBUTE_ASSERTION
    );
    private static final Set<AdapterCapability> DIRECTORY_CAPABILITIES = Set.of(
            AdapterCapability.DIRECTORY_USERS,
            AdapterCapability.DIRECTORY_GROUPS,
            AdapterCapability.INCREMENTAL_RECONCILIATION,
            AdapterCapability.DEPROVISIONING
    );

    private final Map<RegistrationKey, AdapterDescriptor> descriptorsByKey;
    private final List<AdapterDescriptor> descriptors;

    public AdapterDescriptorRegistry(Collection<AdapterDescriptor> descriptors) {
        Objects.requireNonNull(descriptors, "descriptors");
        List<AdapterDescriptor> ordered = new ArrayList<>(descriptors);
        ordered.forEach(descriptor -> Objects.requireNonNull(descriptor, "descriptor"));
        ordered.sort(Comparator
                .comparing((AdapterDescriptor descriptor) -> descriptor.adapterKey().value())
                .thenComparingInt(descriptor -> descriptor.contractVersion().major())
                .thenComparingInt(descriptor -> descriptor.contractVersion().minor())
                .thenComparingInt(AdapterDescriptor::configSchemaVersion));

        Map<RegistrationKey, AdapterDescriptor> validated = new LinkedHashMap<>();
        for (AdapterDescriptor descriptor : ordered) {
            validate(descriptor);
            RegistrationKey key = RegistrationKey.from(descriptor);
            if (validated.putIfAbsent(key, descriptor) != null) {
                throw failure(
                        AdapterRegistryFailureReason.DUPLICATE_REGISTRATION,
                        descriptor,
                        "duplicate adapter key, contract version and config schema version"
                );
            }
        }
        this.descriptorsByKey = Map.copyOf(validated);
        this.descriptors = List.copyOf(ordered);
    }

    public AdapterDescriptor require(
            AdapterKey adapterKey,
            AdapterContractVersion contractVersion,
            int configSchemaVersion
    ) {
        Objects.requireNonNull(adapterKey, "adapterKey");
        Objects.requireNonNull(contractVersion, "contractVersion");
        if (configSchemaVersion < 1) {
            throw new IllegalArgumentException("config schema version must be positive");
        }
        AdapterDescriptor descriptor = descriptorsByKey.get(new RegistrationKey(
                adapterKey,
                contractVersion,
                configSchemaVersion
        ));
        if (descriptor == null) {
            boolean sameContract = descriptors.stream().anyMatch(candidate ->
                    candidate.adapterKey().equals(adapterKey)
                            && candidate.contractVersion().equals(contractVersion));
            if (sameContract) {
                throw new AdapterRegistryException(
                        AdapterRegistryFailureReason.UNSUPPORTED_CONFIG_SCHEMA_VERSION,
                        "Adapter is not registered for the requested config schema version"
                );
            }
            boolean sameKey = descriptors.stream().anyMatch(candidate ->
                    candidate.adapterKey().equals(adapterKey));
            if (sameKey) {
                throw new AdapterRegistryException(
                        AdapterRegistryFailureReason.UNSUPPORTED_CONTRACT_VERSION,
                        "Adapter is not registered for the requested contract version"
                );
            }
            throw new AdapterRegistryException(
                    AdapterRegistryFailureReason.ADAPTER_NOT_REGISTERED,
                    "Adapter is not registered for the requested key"
            );
        }
        return descriptor;
    }

    public List<AdapterDescriptor> descriptors() {
        return descriptors;
    }

    private static void validate(AdapterDescriptor descriptor) {
        if (!SUPPORTED_CONTRACT_MAJORS.contains(descriptor.contractVersion().major())) {
            throw failure(
                    AdapterRegistryFailureReason.UNSUPPORTED_CONTRACT_VERSION,
                    descriptor,
                    "unsupported adapter contract major"
            );
        }
        switch (descriptor.connectionKind()) {
            case LOGIN -> validateLogin(descriptor);
            case DIRECTORY -> validateDirectory(descriptor);
        }
    }

    private static void validateLogin(AdapterDescriptor descriptor) {
        if (descriptor.interactionModel().isEmpty()) {
            throw failure(
                    AdapterRegistryFailureReason.INVALID_INTERACTION_MODEL,
                    descriptor,
                    "login adapter must declare an interaction model"
            );
        }
        if (!LOGIN_CAPABILITIES.containsAll(descriptor.capabilities())) {
            throw failure(
                    AdapterRegistryFailureReason.INVALID_CAPABILITY_SET,
                    descriptor,
                    "login adapter declares a non-login capability"
            );
        }
        if (!descriptor.capabilities().contains(AdapterCapability.IDENTITY_ASSERTION)) {
            throw failure(
                    AdapterRegistryFailureReason.MISSING_REQUIRED_CAPABILITY,
                    descriptor,
                    "login adapter must emit an identity assertion"
            );
        }
    }

    private static void validateDirectory(AdapterDescriptor descriptor) {
        if (descriptor.interactionModel().isPresent()) {
            throw failure(
                    AdapterRegistryFailureReason.INVALID_INTERACTION_MODEL,
                    descriptor,
                    "directory adapter must not declare a login interaction model"
            );
        }
        if (!DIRECTORY_CAPABILITIES.containsAll(descriptor.capabilities())) {
            throw failure(
                    AdapterRegistryFailureReason.INVALID_CAPABILITY_SET,
                    descriptor,
                    "directory adapter declares a non-directory capability"
            );
        }
        if (!descriptor.capabilities().contains(AdapterCapability.DIRECTORY_USERS)) {
            throw failure(
                    AdapterRegistryFailureReason.MISSING_REQUIRED_CAPABILITY,
                    descriptor,
                    "directory capabilities require directory user provisioning"
            );
        }
    }

    private static AdapterRegistryException failure(
            AdapterRegistryFailureReason reason,
            AdapterDescriptor descriptor,
            String detail
    ) {
        return new AdapterRegistryException(
                reason,
                detail + ": " + descriptor.adapterKey().value()
        );
    }

    private record RegistrationKey(
            AdapterKey adapterKey,
            AdapterContractVersion contractVersion,
            int configSchemaVersion
    ) {

        private static RegistrationKey from(AdapterDescriptor descriptor) {
            return new RegistrationKey(
                    descriptor.adapterKey(),
                    descriptor.contractVersion(),
                    descriptor.configSchemaVersion()
            );
        }
    }
}
