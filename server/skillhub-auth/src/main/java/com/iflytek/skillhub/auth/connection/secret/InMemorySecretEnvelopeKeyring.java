package com.iflytek.skillhub.auth.connection.secret;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Process-local keyring useful for composition and tests; callers receive disposable key copies. */
public final class InMemorySecretEnvelopeKeyring implements SecretEnvelopeKeyring, AutoCloseable {

    private final String activeKeyId;
    private final Map<String, byte[]> keys;
    private boolean closed;

    public InMemorySecretEnvelopeKeyring(SecretEnvelopeKey activeKey, SecretEnvelopeKey... oldKeys) {
        Objects.requireNonNull(activeKey, "activeKey");
        Objects.requireNonNull(oldKeys, "oldKeys");
        this.activeKeyId = activeKey.keyId();
        this.keys = new LinkedHashMap<>();
        putCopy(activeKey);
        for (SecretEnvelopeKey oldKey : oldKeys) {
            putCopy(Objects.requireNonNull(oldKey, "oldKey"));
        }
    }

    @Override
    public synchronized SecretEnvelopeKey activeKey() {
        return copy(activeKeyId).orElseThrow(() -> new IllegalStateException("Secret keyring is closed"));
    }

    @Override
    public synchronized Optional<SecretEnvelopeKey> findById(String keyId) {
        Objects.requireNonNull(keyId, "keyId");
        return copy(keyId);
    }

    private Optional<SecretEnvelopeKey> copy(String keyId) {
        if (closed) {
            return Optional.empty();
        }
        byte[] bytes = keys.get(keyId);
        return bytes == null
                ? Optional.empty()
                : Optional.of(new SecretEnvelopeKey(keyId, bytes));
    }

    private void putCopy(SecretEnvelopeKey key) {
        byte[] copy = key.copyKeyBytes();
        byte[] previous = keys.putIfAbsent(key.keyId(), copy);
        if (previous != null) {
            Arrays.fill(copy, (byte) 0);
            throw new IllegalArgumentException("Duplicate Secret envelope key id");
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            keys.values().forEach(bytes -> Arrays.fill(bytes, (byte) 0));
            keys.clear();
            closed = true;
        }
    }

    @Override
    public String toString() {
        return "InMemorySecretEnvelopeKeyring[<redacted>]";
    }
}
