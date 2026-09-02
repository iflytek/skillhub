package com.iflytek.skillhub.auth.session;

/**
 * Removes a session record that cannot be deserialized by the current application version.
 */
@FunctionalInterface
public interface CorruptSessionRemover {

    void remove(String sessionId);
}
