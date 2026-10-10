package com.iflytek.skillhub.auth.session;

/**
 * Removes a session record that cannot be deserialized by the current application version.
 * The input is the server-side session id already resolved by Spring Session.
 */
@FunctionalInterface
public interface CorruptSessionRemover {

    void remove(String sessionId);
}
