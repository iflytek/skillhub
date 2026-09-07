package com.iflytek.skillhub.auth.connection.core;

/**
 * Browser interaction family implemented by a login adapter.
 *
 * <p>Only the redirect family has an executable contract in the first slice. Credential and passive
 * assertion families are reserved names whose dedicated minimal contracts are deferred until a real
 * integration requires them.</p>
 */
public enum InteractionModel {
    REDIRECT,
    CREDENTIAL,
    PASSIVE_ASSERTION
}
