package com.iflytek.skillhub.auth.connection.secret;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

/** Short-lived, explicitly erasable Secret bytes with no raw accessor. */
public final class SecretMaterial implements AutoCloseable {

    private byte[] bytes;

    private SecretMaterial(byte[] bytes) {
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Secret material must not be empty");
        }
        this.bytes = bytes;
    }

    public static SecretMaterial copyOf(byte[] source) {
        Objects.requireNonNull(source, "source");
        return new SecretMaterial(source.clone());
    }

    public synchronized <T> T use(Function<byte[], T> operation) {
        Objects.requireNonNull(operation, "operation");
        if (bytes == null) {
            throw new IllegalStateException("Secret material is closed");
        }
        byte[] workingCopy = bytes.clone();
        try {
            return operation.apply(workingCopy);
        } finally {
            Arrays.fill(workingCopy, (byte) 0);
        }
    }

    @Override
    public synchronized void close() {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
            bytes = null;
        }
    }

    @Override
    public String toString() {
        return "SecretMaterial[<redacted>]";
    }
}
