package com.iflytek.skillhub.auth.federation.core;

import java.net.URI;

/** Browser transaction and callback response passed to a redirect adapter for protocol verification. */
public record RedirectCompleteRequest(String browserTransactionId, URI callbackResponseUri) {

    public RedirectCompleteRequest {
        browserTransactionId = RedirectRequestValidation.requireTransactionId(browserTransactionId);
        callbackResponseUri = RedirectRequestValidation.requireAbsoluteUri(callbackResponseUri, "callback response URI");
    }
}
