package com.iflytek.skillhub.auth.uass;

import java.net.URI;

/**
 * Input passed to a {@link UassGateway} when building the upstream login URL.
 */
public record UassLoginUrlRequest(String state, URI callbackUri) {
}
