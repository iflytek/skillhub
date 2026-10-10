package com.iflytek.skillhub.auth.federation.core;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/** Browser transaction inputs used to begin redirect authentication. */
public record RedirectStartRequest(
        String browserTransactionId,
        URI callbackUri,
        Optional<String> returnTarget
) {

    public RedirectStartRequest {
        browserTransactionId = RedirectRequestValidation.requireTransactionId(browserTransactionId);
        callbackUri = RedirectRequestValidation.requireAbsoluteUri(callbackUri, "callback URI");
        Objects.requireNonNull(returnTarget, "return target must not be null");
        returnTarget = returnTarget.map(RedirectStartRequest::requireSiteRelativeTarget);
    }

    private static String requireSiteRelativeTarget(String value) {
        Objects.requireNonNull(value, "return target must not be null");
        if (!value.startsWith("/") || value.startsWith("//") || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("return target must be a site-relative path");
        }
        return value;
    }
}
