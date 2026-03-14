package com.iflytek.skillhub.auth.direct;

import com.iflytek.skillhub.auth.local.LocalAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import org.springframework.stereotype.Component;

@Component
public class LocalDirectAuthProvider implements DirectAuthProvider {

    private final LocalAuthService localAuthService;

    public LocalDirectAuthProvider(LocalAuthService localAuthService) {
        this.localAuthService = localAuthService;
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
    public PlatformPrincipal authenticate(DirectAuthRequest request) {
        return localAuthService.login(request.username(), request.password());
    }
}
