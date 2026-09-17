package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;

/** Immutable platform policy attached to one Login Connection revision. */
public record IdentityCorrelationPolicySettings(
        boolean verifiedEmailCorrelationEnabled,
        boolean jitProvisioningEnabled
) implements IdentityCorrelationPolicy {

    private static final Assurance VERIFIED_EMAIL_ASSURANCE =
            new Assurance("email_verified");

    public IdentityCorrelationPolicySettings {
        if (jitProvisioningEnabled && !verifiedEmailCorrelationEnabled) {
            throw new IllegalArgumentException(
                    "JIT provisioning requires verified-email candidate evaluation"
            );
        }
    }

    public static IdentityCorrelationPolicySettings disabled() {
        return new IdentityCorrelationPolicySettings(false, false);
    }

    public static IdentityCorrelationPolicySettings verifiedEmailOnly() {
        return new IdentityCorrelationPolicySettings(true, false);
    }

    public static IdentityCorrelationPolicySettings verifiedEmailWithJit() {
        return new IdentityCorrelationPolicySettings(true, true);
    }

    @Override
    public boolean allowsVerifiedEmailCorrelation(IdentityAssertion assertion) {
        return verifiedEmailCorrelationEnabled && hasTrustedVerifiedEmail(assertion);
    }

    @Override
    public boolean allowsJitProvisioning(IdentityAssertion assertion) {
        return jitProvisioningEnabled && hasTrustedVerifiedEmail(assertion);
    }

    private static boolean hasTrustedVerifiedEmail(IdentityAssertion assertion) {
        IdentityAssertion verifiedAssertion = Objects.requireNonNull(assertion, "assertion");
        return verifiedAssertion.email().isPresent()
                && verifiedAssertion.assurance().contains(VERIFIED_EMAIL_ASSURANCE);
    }
}
