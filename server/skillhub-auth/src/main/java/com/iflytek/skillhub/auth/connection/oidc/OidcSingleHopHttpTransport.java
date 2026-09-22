package com.iflytek.skillhub.auth.connection.oidc;

import java.net.URI;
import java.time.Duration;

/** HTTP seam that performs exactly one request and never follows redirects. */
@FunctionalInterface
public interface OidcSingleHopHttpTransport {

    OidcHttpResponse get(URI uri, Duration timeout) throws Exception;
}
