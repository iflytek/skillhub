package com.iflytek.skillhub.auth.connection.core;

/** Control-plane port that publishes validated immutable revisions to the data plane. */
public interface LoginConnectionActivationControl {

    <C extends LoginConnectionRuntimeConfig> LoginConnectionRuntimeSnapshot<C> activate(
            LoginConnectionRevision<C> revision
    );

    <C extends LoginConnectionRuntimeConfig> LoginConnectionRuntimeSnapshot<C> rollback(
            LoginConnectionRevision<C> revision
    );
}
