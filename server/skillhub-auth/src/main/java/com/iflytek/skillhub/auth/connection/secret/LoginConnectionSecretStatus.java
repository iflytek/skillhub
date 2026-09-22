package com.iflytek.skillhub.auth.connection.secret;

/** Runtime eligibility of one immutable encrypted Secret version. */
public enum LoginConnectionSecretStatus {
    CURRENT,
    RETIRING,
    REVOKED
}
