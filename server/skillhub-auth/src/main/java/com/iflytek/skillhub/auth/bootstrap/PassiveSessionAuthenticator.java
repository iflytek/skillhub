package com.iflytek.skillhub.auth.bootstrap;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Extension point for establishing a SkillHub session from an external passive session,
 * such as an SSO cookie already present on the request.
 */
public interface PassiveSessionAuthenticator {

    String providerCode();

    default String displayName() {
        return providerCode();
    }

    Optional<PlatformPrincipal> authenticate(HttpServletRequest request);
}
