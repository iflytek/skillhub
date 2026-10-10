package com.iflytek.skillhub.auth.federation.core;

/**
 * Post-verification seam that turns protocol-specific trusted facts into the only assertion shape
 * accepted by the identity decision module.
 *
 * <p>This does not replace interaction-specific authentication interfaces. Implementations must
 * not write platform state or forward credentials, tokens, or unverified claims. Invalid input is
 * normalized to {@link AuthenticationAdapterException} rather than leaking provider exceptions.
 */
@FunctionalInterface
public interface VerifiedAuthenticationFactsAdapter<F> {

    IdentityAssertion toAssertion(F verifiedFacts);
}
