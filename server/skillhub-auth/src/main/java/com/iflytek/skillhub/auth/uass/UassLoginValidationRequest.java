package com.iflytek.skillhub.auth.uass;

import java.net.URI;

/**
 * Input passed to a {@link UassGateway} when validating a callback from UASS.
 */
public record UassLoginValidationRequest(String loginCode, String state, URI callbackUri) {
}
