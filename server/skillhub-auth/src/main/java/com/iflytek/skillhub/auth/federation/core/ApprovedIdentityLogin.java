package com.iflytek.skillhub.auth.federation.core;

/** Proof that every state required for principal creation passed the final login guard. */
public final class ApprovedIdentityLogin {

    private final IdentityLoginGuardContext context;

    ApprovedIdentityLogin(IdentityLoginGuardContext context) {
        this.context = context;
    }

    public IdentityLoginGuardContext context() {
        return context;
    }
}
