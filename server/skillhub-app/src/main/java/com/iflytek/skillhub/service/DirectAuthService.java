package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.direct.DirectAuthProvider;
import com.iflytek.skillhub.auth.direct.DirectAuthRequest;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.config.DirectAuthProperties;
import com.iflytek.skillhub.exception.BadRequestException;
import com.iflytek.skillhub.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * Dispatches direct-login requests to a configured provider and then binds the
 * resulting principal to the current HTTP session.
 */
@Service
public class DirectAuthService {

    private final DirectAuthProperties properties;
    private final Map<String, DirectAuthProvider> providersByCode;
    private final SessionBootstrapService sessionBootstrapService;

    public DirectAuthService(DirectAuthProperties properties,
                             List<DirectAuthProvider> providers,
                             SessionBootstrapService sessionBootstrapService) {
        this.properties = properties;
        this.providersByCode = providers.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                DirectAuthProvider::providerCode,
                Function.identity()
            ));
        this.sessionBootstrapService = sessionBootstrapService;
    }

    public PlatformPrincipal authenticate(String providerCode,
                                          String username,
                                          String password,
                                          HttpServletRequest request) {
        if (!properties.isEnabled()) {
            throw new ForbiddenException("error.auth.direct.disabled");
        }

        DirectAuthProvider provider = providersByCode.get(providerCode);
        if (provider == null) {
            throw new BadRequestException("error.auth.direct.providerUnsupported", providerCode);
        }

        PlatformPrincipal principal = provider.authenticate(new DirectAuthRequest(username, password));
        sessionBootstrapService.establishSession(principal, request);
        return principal;
    }
}
