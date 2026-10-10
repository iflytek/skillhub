package com.iflytek.skillhub.auth.connection.core;

/** Resolves only validated, active and runtime-compatible login connection snapshots. */
@FunctionalInterface
public interface EnterpriseConnectionRegistry {

    LoginConnectionRuntimeSnapshot<?> requireActive(ConnectionHandle handle);
}
