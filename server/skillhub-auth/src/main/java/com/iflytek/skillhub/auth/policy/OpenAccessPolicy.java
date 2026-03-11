package com.iflytek.skillhub.auth.policy;

import com.iflytek.skillhub.auth.oauth.OAuthClaims;

public class OpenAccessPolicy implements AccessPolicy {
    @Override
    public AccessDecision evaluate(OAuthClaims claims) {
        return AccessDecision.ALLOW;
    }
}
