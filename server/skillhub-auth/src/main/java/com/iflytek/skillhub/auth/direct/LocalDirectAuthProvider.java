package com.iflytek.skillhub.auth.direct;

import com.iflytek.skillhub.auth.local.LocalAuthProperties;
import com.iflytek.skillhub.auth.local.LocalAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import org.springframework.stereotype.Component;

/**
 * Direct-auth provider that delegates username and password verification to the local auth flow.
 */
@Component
public class LocalDirectAuthProvider implements DirectAuthProvider {

    private final LocalAuthService localAuthService;
    private final LocalAuthProperties localAuthProperties;

    public LocalDirectAuthProvider(LocalAuthService localAuthService,
                                   LocalAuthProperties localAuthProperties) {
        this.localAuthService = localAuthService;
        this.localAuthProperties = localAuthProperties;
    }

    @Override
    public String providerCode() {
        return "local";
    }

    @Override
    public String displayName() {
        return "Local Account";
    }

    @Override
    public boolean isAvailable() {
        return localAuthProperties.isEnabled();
    }

    @Override
    public PlatformPrincipal authenticate(DirectAuthRequest request) {
        return localAuthService.login(request.username(), request.password());
    }
}
