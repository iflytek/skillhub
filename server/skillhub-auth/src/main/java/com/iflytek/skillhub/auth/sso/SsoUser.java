package com.iflytek.skillhub.auth.sso;

/**
 * Carrier for user-info returned by the SSO ticket-validation endpoint.
 *
 * @param account  unique domain account / username
 * @param id       employee or user identifier
 * @param name     display name
 */
public record SsoUser(String account, String id, String name) {
}
