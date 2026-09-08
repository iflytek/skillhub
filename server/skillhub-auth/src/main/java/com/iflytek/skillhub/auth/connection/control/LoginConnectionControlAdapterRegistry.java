package com.iflytek.skillhub.auth.connection.control;

import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Closed runtime registry: database values select only reviewed, dependency-injected code. */
public final class LoginConnectionControlAdapterRegistry {

    private final Map<AdapterKey, LoginConnectionControlAdapter> adapters;

    public LoginConnectionControlAdapterRegistry(List<LoginConnectionControlAdapter> adapters) {
        Objects.requireNonNull(adapters, "adapters");
        Map<AdapterKey, LoginConnectionControlAdapter> indexed = new LinkedHashMap<>();
        for (LoginConnectionControlAdapter adapter : adapters) {
            LoginConnectionControlAdapter required = Objects.requireNonNull(adapter, "adapter");
            if (indexed.putIfAbsent(required.adapterKey(), required) != null) {
                throw new IllegalStateException(
                        "Duplicate login connection control Adapter: "
                                + required.adapterKey().value()
                );
            }
        }
        this.adapters = Map.copyOf(indexed);
    }

    public LoginConnectionControlAdapter require(AdapterKey adapterKey) {
        LoginConnectionControlAdapter adapter = adapters.get(
                Objects.requireNonNull(adapterKey, "adapterKey")
        );
        if (adapter == null) {
            throw new DomainBadRequestException("error.loginConnection.adapter.unsupported");
        }
        return adapter;
    }
}
