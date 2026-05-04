package com.iflytek.skillhub.auth.direct;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import org.junit.jupiter.api.Test;

class DirectAuthProviderTest {

    @Test
    void defaultDisplayNameReturnsProviderCode() {
        DirectAuthProvider provider = new DirectAuthProvider() {
            @Override
            public String providerCode() {
                return "test-provider";
            }

            @Override
            public PlatformPrincipal authenticate(DirectAuthRequest request) {
                return null;
            }
        };

        assertThat(provider.displayName()).isEqualTo("test-provider");
    }
}
