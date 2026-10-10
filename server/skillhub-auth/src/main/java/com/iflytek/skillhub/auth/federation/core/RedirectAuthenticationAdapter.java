package com.iflytek.skillhub.auth.federation.core;

import com.iflytek.skillhub.auth.connection.core.AdapterDescriptor;
import com.iflytek.skillhub.auth.connection.core.LoginConnectionRuntimeConfig;
import com.iflytek.skillhub.auth.connection.core.LoginConnectionRuntimeSnapshot;

/** Two-phase contract for redirect-based authentication adapters. */
public interface RedirectAuthenticationAdapter<C extends LoginConnectionRuntimeConfig> {

    AdapterDescriptor descriptor();

    Class<C> configType();

    RedirectStartResult start(LoginConnectionRuntimeSnapshot<C> connection, RedirectStartRequest request);

    IdentityAssertion complete(LoginConnectionRuntimeSnapshot<C> connection, RedirectCompleteRequest request);
}
