package com.iflytek.skillhub.domain.event;

/**
 * Published when an account becomes usable — local registration, the first login through an
 * external identity provider, or an administrator approving or re-enabling an account.
 *
 * <p>Listeners must be idempotent: re-enabling a previously disabled account publishes the event
 * again.
 *
 * @param username the name the authentication path knows the user by, or {@code null}
 */
public record UserActivatedEvent(String userId, String username, String email) {
}
