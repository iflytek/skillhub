package com.iflytek.skillhub.auth.federation.core;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationAdapterFailureReasonTest {

    @Test
    void taxonomyHasStablePrivacySafeReasonsAndRetrySemantics() {
        assertThat(AuthenticationAdapterFailureReason.values()).containsExactly(
                AuthenticationAdapterFailureReason.INVALID_ASSERTION,
                AuthenticationAdapterFailureReason.AUTHENTICATION_DENIED,
                AuthenticationAdapterFailureReason.UPSTREAM_UNAVAILABLE,
                AuthenticationAdapterFailureReason.CONNECTION_MISCONFIGURED
        );
        assertThat(Map.of(
                AuthenticationAdapterFailureReason.INVALID_ASSERTION, false,
                AuthenticationAdapterFailureReason.AUTHENTICATION_DENIED, false,
                AuthenticationAdapterFailureReason.UPSTREAM_UNAVAILABLE, true,
                AuthenticationAdapterFailureReason.CONNECTION_MISCONFIGURED, false
        )).allSatisfy((reason, retryable) -> {
            AuthenticationAdapterException failure = new AuthenticationAdapterException(reason);

            assertThat(failure.reason()).isEqualTo(reason);
            assertThat(failure.retryable()).isEqualTo(retryable);
            assertThat(failure.getMessage()).isEqualTo(reason.publicMessage());
            assertThat(failure.getCause()).isNull();
        });
    }
}
