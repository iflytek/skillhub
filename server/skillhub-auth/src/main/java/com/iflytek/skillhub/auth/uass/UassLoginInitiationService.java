package com.iflytek.skillhub.auth.uass;

import java.net.URI;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Owns the browser-login initiation step for UASS: persist callback state,
 * derive the public callback URI, and ask the provider facade for the upstream
 * login URL.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.auth.uass", name = "enabled", havingValue = "true")
public class UassLoginInitiationService {

    private final UassClientFacade uassClientFacade;
    private final UassLoginStateService uassLoginStateService;
    private final UassProperties uassProperties;
    private final URI publicBaseUri;

    @Autowired
    public UassLoginInitiationService(UassClientFacade uassClientFacade,
                                      UassLoginStateService uassLoginStateService,
                                      UassProperties uassProperties,
                                      @Value("${skillhub.public.base-url:}") String publicBaseUrl) {
        this(
                uassClientFacade,
                uassLoginStateService,
                uassProperties,
                normalizePublicBaseUri(publicBaseUrl)
        );
    }

    UassLoginInitiationService(UassClientFacade uassClientFacade,
                               UassLoginStateService uassLoginStateService,
                               UassProperties uassProperties,
                               URI publicBaseUri) {
        this.uassClientFacade = Objects.requireNonNull(uassClientFacade, "uassClientFacade must not be null");
        this.uassLoginStateService = Objects.requireNonNull(uassLoginStateService, "uassLoginStateService must not be null");
        this.uassProperties = Objects.requireNonNull(uassProperties, "uassProperties must not be null");
        this.publicBaseUri = publicBaseUri;
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
        URI callbackBaseUri = publicBaseUri != null ? publicBaseUri : requestUri;
        return UriComponentsBuilder.fromUri(callbackBaseUri)
                .replacePath(uassProperties.getCallbackPath())
                .replaceQuery(null)
                .fragment(null)
                .build(true)
                .toUri();
    }

    private static URI normalizePublicBaseUri(String publicBaseUrl) {
        if (!StringUtils.hasText(publicBaseUrl)) {
            return null;
        }
        String normalized = publicBaseUrl.trim();
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return URI.create(normalized);
    }
}
