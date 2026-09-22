package com.iflytek.skillhub.auth.connection.control;

/** Control-plane lifecycle state for an authentication connection. */
public enum LoginConnectionStatus {
    DRAFT,
    ACTIVE,
    SUSPENDED,
    DISABLED
}
