package com.iflytek.skillhub.auth.federation.core;

import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.core.LoginConnectionRuntimeConfig;
import com.iflytek.skillhub.auth.connection.core.LoginConnectionRuntimeSnapshot;

/** Two-phase contract for redirect-based authentication adapters. */
public interface RedirectAuthenticationAdapter<C extends LoginConnectionRuntimeConfig> {

    AdapterKey adapterKey();

    Class<C> configType();

    RedirectStartResult start(LoginConnectionRuntimeSnapshot<C> connection, RedirectStartRequest request);

    IdentityAssertion complete(LoginConnectionRuntimeSnapshot<C> connection, RedirectCompleteRequest request);
}
