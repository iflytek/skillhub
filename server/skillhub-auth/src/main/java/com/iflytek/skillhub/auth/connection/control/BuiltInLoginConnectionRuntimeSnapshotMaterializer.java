package com.iflytek.skillhub.auth.connection.control;

import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.core.ConnectionUnavailableException;
import com.iflytek.skillhub.auth.connection.core.LoginConnectionRuntimeSnapshot;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Dispatches persisted revisions to reviewed, contract-versioned Adapter decoders. */
public final class BuiltInLoginConnectionRuntimeSnapshotMaterializer
        implements LoginConnectionRuntimeSnapshotMaterializer {

    private final Map<DecoderKey, LoginConnectionRuntimeSnapshotDecoder> decoders;

    public BuiltInLoginConnectionRuntimeSnapshotMaterializer(
            Collection<? extends LoginConnectionRuntimeSnapshotDecoder> decoders
    ) {
        Objects.requireNonNull(decoders, "decoders");
        Map<DecoderKey, LoginConnectionRuntimeSnapshotDecoder> indexed = new LinkedHashMap<>();
        for (LoginConnectionRuntimeSnapshotDecoder decoder : decoders) {
            Objects.requireNonNull(decoder, "decoder");
            if (decoder.contractMajor() < 1) {
                throw new IllegalArgumentException("Decoder contract major must be positive");
            }
            DecoderKey key = new DecoderKey(decoder.adapterKey(), decoder.contractMajor());
            if (indexed.putIfAbsent(key, decoder) != null) {
                throw new IllegalArgumentException("Duplicate Login Connection runtime decoder");
            }
        }
        this.decoders = Map.copyOf(indexed);
    }

    @Override
    public LoginConnectionRuntimeSnapshot<?> materialize(
            LoginConnectionRevisionSnapshot revision
    ) {
        Objects.requireNonNull(revision, "revision");
        LoginConnectionRuntimeSnapshotDecoder decoder = decoders.get(
                new DecoderKey(revision.adapterKey(), parseContractMajor(revision.adapterContractVersion()))
        );
        if (decoder == null) {
            throw new ConnectionUnavailableException();
        }
        return decoder.decode(revision);
    }

    private static int parseContractMajor(String contractVersion) {
        int separator = contractVersion.indexOf('.');
        if (separator < 1 || separator != contractVersion.lastIndexOf('.')) {
            throw new ConnectionUnavailableException();
        }
        try {
            return Integer.parseInt(contractVersion.substring(0, separator));
        } catch (NumberFormatException invalid) {
            throw new ConnectionUnavailableException();
        }
    }

    private record DecoderKey(AdapterKey adapterKey, int contractMajor) {
    }
}
