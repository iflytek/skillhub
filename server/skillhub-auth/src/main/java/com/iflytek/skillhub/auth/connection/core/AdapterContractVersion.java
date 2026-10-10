package com.iflytek.skillhub.auth.connection.core;

/** Explicit adapter contract version; only major changes may break persisted revisions. */
public record AdapterContractVersion(int major, int minor) {

    public AdapterContractVersion {
        if (major < 1) {
            throw new IllegalArgumentException("adapter contract major version must be positive");
        }
        if (minor < 0) {
            throw new IllegalArgumentException("adapter contract minor version must not be negative");
        }
    }
}
