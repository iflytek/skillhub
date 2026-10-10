package com.iflytek.skillhub.auth.connection.core;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Persistable adapter identity and platform capabilities, without implementation class names. */
public record AdapterDescriptor(
        AdapterKey adapterKey,
        AdapterContractVersion contractVersion,
        ConnectionKind connectionKind,
        int configSchemaVersion,
        Optional<InteractionModel> interactionModel,
        Set<AdapterCapability> capabilities
) {

    public AdapterDescriptor {
        Objects.requireNonNull(adapterKey, "adapterKey");
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(connectionKind, "connectionKind");
        if (configSchemaVersion < 1) {
            throw new IllegalArgumentException("config schema version must be positive");
        }
        interactionModel = Objects.requireNonNull(interactionModel, "interactionModel");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
    }
}
