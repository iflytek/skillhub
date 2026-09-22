package com.iflytek.skillhub.auth.federation.adapter;

import com.iflytek.skillhub.auth.connection.core.AdapterDescriptor;
import com.iflytek.skillhub.auth.connection.core.AdapterDescriptorRegistry;
import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.core.AdapterRegistryException;
import com.iflytek.skillhub.auth.connection.core.AdapterRegistryFailureReason;
import com.iflytek.skillhub.auth.connection.core.ConnectionKind;
import com.iflytek.skillhub.auth.connection.core.InteractionModel;
import com.iflytek.skillhub.auth.federation.core.RedirectAuthenticationAdapter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Registry of reviewed redirect adapter instances assembled by the application build. */
public final class BuiltInRedirectAuthenticationAdapterRegistry {

    private final AdapterDescriptorRegistry descriptors;
    private final Map<RegistrationKey, RedirectAuthenticationAdapter<?>> adapters;

    public BuiltInRedirectAuthenticationAdapterRegistry(
            Collection<? extends RedirectAuthenticationAdapter<?>> adapters
    ) {
        Objects.requireNonNull(adapters, "adapters");
        this.descriptors = new AdapterDescriptorRegistry(
                adapters.stream().map(RedirectAuthenticationAdapter::descriptor).toList()
        );
        Map<RegistrationKey, RedirectAuthenticationAdapter<?>> validated = new LinkedHashMap<>();
        for (RedirectAuthenticationAdapter<?> adapter : adapters) {
            Objects.requireNonNull(adapter, "adapter");
            AdapterDescriptor descriptor = adapter.descriptor();
            if (descriptor.connectionKind() != ConnectionKind.LOGIN
                    || descriptor.interactionModel().orElse(null) != InteractionModel.REDIRECT) {
                throw new AdapterRegistryException(
                        AdapterRegistryFailureReason.IMPLEMENTATION_MISMATCH,
                        "Redirect adapter descriptor does not declare a redirect login interaction"
                );
            }
            Objects.requireNonNull(adapter.configType(), "adapter configType");
            validated.put(RegistrationKey.from(descriptor), adapter);
        }
        this.adapters = Map.copyOf(validated);
    }

    public RedirectAuthenticationAdapter<?> require(AdapterKey adapterKey, int contractMajor) {
        descriptors.require(adapterKey, contractMajor);
        return adapters.get(new RegistrationKey(adapterKey, contractMajor));
    }

    private record RegistrationKey(AdapterKey adapterKey, int contractMajor) {

        private static RegistrationKey from(AdapterDescriptor descriptor) {
            return new RegistrationKey(
                    descriptor.adapterKey(),
                    descriptor.contractVersion().major()
            );
        }
    }
}
