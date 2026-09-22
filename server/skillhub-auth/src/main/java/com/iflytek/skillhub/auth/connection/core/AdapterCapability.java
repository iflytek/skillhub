package com.iflytek.skillhub.auth.connection.core;

/** Platform-understood behavior that an adapter may safely advertise. */
public enum AdapterCapability {
    IDENTITY_ASSERTION,
    VERIFIED_EMAIL_ASSERTION,
    PROFILE_ATTRIBUTE_ASSERTION,
    DIRECTORY_USERS,
    DIRECTORY_GROUPS,
    INCREMENTAL_RECONCILIATION,
    DEPROVISIONING
}
