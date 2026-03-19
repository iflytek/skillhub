package com.iflytek.skillhub.auth.policy;

import com.iflytek.skillhub.auth.oauth.OAuthClaims;

/**
 * Access policy that accepts all OAuth-authenticated users.
 */
public class OpenAccessPolicy implements AccessPolicy {
    @Override
    public AccessDecision evaluate(OAuthClaims claims) {
        return AccessDecision.ALLOW;
    }
}
