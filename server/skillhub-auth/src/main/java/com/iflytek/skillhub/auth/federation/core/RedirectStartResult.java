package com.iflytek.skillhub.auth.federation.core;

import java.net.URI;

/** Browser redirect produced by an authentication adapter after start validation. */
public record RedirectStartResult(URI authorizationUri) {

    public RedirectStartResult {
        authorizationUri = RedirectRequestValidation.requireAbsoluteUri(authorizationUri, "authorization URI");
    }
}
