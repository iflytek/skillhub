package com.iflytek.skillhub.auth.federation.adapter;

import com.iflytek.skillhub.auth.connection.core.AdapterCapability;
import com.iflytek.skillhub.auth.connection.core.AdapterContractVersion;
import com.iflytek.skillhub.auth.connection.core.AdapterDescriptor;
import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.core.AdapterRegistryException;
import com.iflytek.skillhub.auth.connection.core.AdapterRegistryFailureReason;
import com.iflytek.skillhub.auth.connection.core.ConnectionKind;
import com.iflytek.skillhub.auth.connection.core.InteractionModel;
import com.iflytek.skillhub.auth.connection.core.LoginConnectionRuntimeConfig;
import com.iflytek.skillhub.auth.connection.core.LoginConnectionRuntimeSnapshot;
import com.iflytek.skillhub.auth.federation.core.IdentityAssertion;
import com.iflytek.skillhub.auth.federation.core.RedirectAuthenticationAdapter;
import com.iflytek.skillhub.auth.federation.core.RedirectCompleteRequest;
import com.iflytek.skillhub.auth.federation.core.RedirectStartRequest;
import com.iflytek.skillhub.auth.federation.core.RedirectStartResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuiltInRedirectAuthenticationAdapterRegistryTest {

    @Test
    void require_returnsCompileTimeRegisteredAdapterByStableKeyAndMajorVersion() {
        TestRedirectAdapter adapter = new TestRedirectAdapter(descriptor(InteractionModel.REDIRECT));
        BuiltInRedirectAuthenticationAdapterRegistry registry =
                new BuiltInRedirectAuthenticationAdapterRegistry(List.of(adapter));

        assertThat(registry.require(new AdapterKey("test-redirect"), 1)).isSameAs(adapter);
    }

    @Test
    void constructor_rejectsDescriptorWhoseInteractionDoesNotMatchRedirectInterface() {
        TestRedirectAdapter adapter = new TestRedirectAdapter(descriptor(InteractionModel.CREDENTIAL));

        assertThatThrownBy(() -> new BuiltInRedirectAuthenticationAdapterRegistry(List.of(adapter)))
                .isInstanceOfSatisfying(
                        AdapterRegistryException.class,
                        failure -> assertThat(failure.reason())
                                .isEqualTo(AdapterRegistryFailureReason.IMPLEMENTATION_MISMATCH)
                );
    }

    private static AdapterDescriptor descriptor(InteractionModel interactionModel) {
        return new AdapterDescriptor(
                new AdapterKey("test-redirect"),
                new AdapterContractVersion(1, 0),
                ConnectionKind.LOGIN,
                1,
                Optional.of(interactionModel),
                Set.of(AdapterCapability.IDENTITY_ASSERTION)
        );
    }

    private record TestConfig() implements LoginConnectionRuntimeConfig {
    }

    private record TestRedirectAdapter(AdapterDescriptor descriptor)
            implements RedirectAuthenticationAdapter<TestConfig> {

        @Override
        public Class<TestConfig> configType() {
            return TestConfig.class;
        }

        @Override
        public RedirectStartResult start(
                LoginConnectionRuntimeSnapshot<TestConfig> connection,
                RedirectStartRequest request
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IdentityAssertion complete(
                LoginConnectionRuntimeSnapshot<TestConfig> connection,
                RedirectCompleteRequest request
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
