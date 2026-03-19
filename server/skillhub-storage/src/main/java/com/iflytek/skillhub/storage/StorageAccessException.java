package com.iflytek.skillhub.storage;

/**
 * Wraps provider-specific storage failures with normalized operation and object-key context.
 */
public class StorageAccessException extends RuntimeException {

    private final String operation;
    private final String key;

    public StorageAccessException(String operation, String key, Throwable cause) {
        super("Storage operation failed: " + operation + " [" + key + "]", cause);
        this.operation = operation;
        this.key = key;
    }

    public String getOperation() {
        return operation;
    }

    public String getKey() {
        return key;
    }
}
