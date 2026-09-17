package com.iflytek.skillhub.auth.federation.core;

/** Records redacted, tenant-correlated enterprise identity security events. */
@FunctionalInterface
public interface EnterpriseIdentitySecurityEventRecorder {

    void record(
            EnterpriseIdentitySecurityEventType type,
            String organizationId,
            String connectionId,
            String targetReference
    );
}
