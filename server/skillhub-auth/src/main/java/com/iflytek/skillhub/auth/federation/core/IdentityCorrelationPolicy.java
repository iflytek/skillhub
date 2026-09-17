package com.iflytek.skillhub.auth.federation.core;

/** Organization policy decisions that are intentionally external to the correlation algorithm. */
public interface IdentityCorrelationPolicy {

    boolean allowsVerifiedEmailCorrelation(IdentityAssertion assertion);

    boolean allowsJitProvisioning(IdentityAssertion assertion);
}
