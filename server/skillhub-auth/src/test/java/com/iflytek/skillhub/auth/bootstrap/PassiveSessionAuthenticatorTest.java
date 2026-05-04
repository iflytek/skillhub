package com.iflytek.skillhub.auth.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PassiveSessionAuthenticatorTest {

    @Test
    void displayName_defaultsToProviderCode() {
        PassiveSessionAuthenticator authenticator = new PassiveSessionAuthenticator() {
            @Override
            public String providerCode() {
                return "test-sso";
            }

            @Override
            public Optional<com.iflytek.skillhub.auth.rbac.PlatformPrincipal> authenticate(HttpServletRequest request) {
                return Optional.empty();
            }
        };

        assertThat(authenticator.displayName()).isEqualTo("test-sso");
    }
}
