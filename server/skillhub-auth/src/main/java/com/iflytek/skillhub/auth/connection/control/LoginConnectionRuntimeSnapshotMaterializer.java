package com.iflytek.skillhub.auth.connection.control;

import com.iflytek.skillhub.auth.connection.core.LoginConnectionRuntimeSnapshot;

/** Adapter edge that converts a stored non-secret revision into its typed runtime snapshot. */
@FunctionalInterface
public interface LoginConnectionRuntimeSnapshotMaterializer {

    LoginConnectionRuntimeSnapshot<?> materialize(LoginConnectionRevisionSnapshot revision);
}
