package com.iflytek.skillhub.auth.session;

/**
 * Removes a session record that cannot be deserialized by the current application version.
 * The input is the encoded client cookie value because repository lookup failed before the
 * decoded requested-session id could be returned.
 */
@FunctionalInterface
public interface CorruptSessionRemover {

    void remove(String sessionCookieValue);
}
