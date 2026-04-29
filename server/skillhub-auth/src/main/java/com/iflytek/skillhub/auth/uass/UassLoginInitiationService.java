package com.iflytek.skillhub.auth.uass;

import java.net.URI;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Owns the browser-login initiation step for UASS: persist callback state,
 * derive the public callback URI, and ask the provider facade for the upstream
 * login URL.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.auth.uass", name = "enabled", havingValue = "true")
@ConditionalOnBean(UassClientFacade.class)
public class UassLoginInitiationService {

    private final UassClientFacade uassClientFacade;
    private final UassLoginStateService uassLoginStateService;
    private final UassProperties uassProperties;

    public UassLoginInitiationService(UassClientFacade uassClientFacade,
                                      UassLoginStateService uassLoginStateService,
                                      UassProperties uassProperties) {
        this.uassClientFacade = Objects.requireNonNull(uassClientFacade, "uassClientFacade must not be null");
        this.uassLoginStateService = Objects.requireNonNull(uassLoginStateService, "uassLoginStateService must not be null");
        this.uassProperties = Objects.requireNonNull(uassProperties, "uassProperties must not be null");
    }

    public String buildLoginUrl(String returnTo, URI requestUri) {
        Objects.requireNonNull(requestUri, "requestUri must not be null");
        String state = uassLoginStateService.startLogin(returnTo, null);
        try {
            return uassClientFacade.buildLoginUrl(state, resolveCallbackUri(requestUri));
        } catch (RuntimeException exception) {
            uassLoginStateService.clearFailedCallback(state);
            throw exception;
        }
    }

    private URI resolveCallbackUri(URI requestUri) {
        return UriComponentsBuilder.fromUri(requestUri)
                .replacePath(uassProperties.getCallbackPath())
                .replaceQuery(null)
                .fragment(null)
                .build(true)
                .toUri();
    }
}
