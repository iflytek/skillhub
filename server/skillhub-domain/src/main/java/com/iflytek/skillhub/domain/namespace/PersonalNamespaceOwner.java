package com.iflytek.skillhub.domain.namespace;

/**
 * The account a personal namespace is being created for.
 *
 * <p>{@code username} is whatever the authentication path calls a user name — the local login name,
 * or the provider login for an external identity. It is absent for accounts that have neither.
 */
public record PersonalNamespaceOwner(String userId, String username, String email) {
}
