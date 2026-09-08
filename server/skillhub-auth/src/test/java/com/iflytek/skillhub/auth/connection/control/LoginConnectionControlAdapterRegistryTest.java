package com.iflytek.skillhub.auth.connection.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.secret.SecretPurpose;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LoginConnectionControlAdapterRegistryTest {

    @Test
    void resolvesAReviewedBuiltInAdapterWithoutProviderBranching() {
        LoginConnectionControlAdapter oidc = new StubAdapter("oidc");
        LoginConnectionControlAdapterRegistry registry =
                new LoginConnectionControlAdapterRegistry(List.of(oidc));

        assertThat(registry.require(new AdapterKey("oidc"))).isSameAs(oidc);
    }

    @Test
    void rejectsDuplicateAdapterKeysAndUnknownRuntimeValues() {
        assertThatThrownBy(() -> new LoginConnectionControlAdapterRegistry(List.of(
                new StubAdapter("oidc"),
                new StubAdapter("oidc")
        ))).isInstanceOf(IllegalStateException.class);

        LoginConnectionControlAdapterRegistry registry =
                new LoginConnectionControlAdapterRegistry(List.of(new StubAdapter("oidc")));
        assertThatThrownBy(() -> registry.require(new AdapterKey("database-class-name")))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class)
                .hasMessage("error.loginConnection.adapter.unsupported");
    }

    private record StubAdapter(AdapterKey adapterKey) implements LoginConnectionControlAdapter {

        StubAdapter(String adapterKey) {
            this(new AdapterKey(adapterKey));
        }

        @Override
        public Optional<SecretPurpose> secretPurpose() {
            return Optional.empty();
        }

        @Override
        public PreparedLoginConnectionRevision prepare(
                LoginConnectionConfigurationInput configuration
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LoginConnectionTestProbe testProbe() {
            throw new UnsupportedOperationException();
        }
    }
}
