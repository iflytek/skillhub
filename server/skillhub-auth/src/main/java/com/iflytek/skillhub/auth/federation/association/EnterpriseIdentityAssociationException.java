package com.iflytek.skillhub.auth.federation.association;

import java.util.Objects;

/** Safe boundary failure that never includes a subject, email, token or internal identifier. */
public class EnterpriseIdentityAssociationException extends RuntimeException {

    private final EnterpriseIdentityAssociationFailure failure;

    public EnterpriseIdentityAssociationException(EnterpriseIdentityAssociationFailure failure) {
        this(failure, null);
    }

    public EnterpriseIdentityAssociationException(
            EnterpriseIdentityAssociationFailure failure,
            Throwable cause
    ) {
        super("Enterprise identity could not be associated", cause);
        this.failure = Objects.requireNonNull(failure, "failure must not be null");
    }

    public EnterpriseIdentityAssociationFailure getFailure() {
        return failure;
    }
}
