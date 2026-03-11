package com.iflytek.skillhub.auth.policy;

import com.iflytek.skillhub.auth.oauth.OAuthClaims;

public interface AccessPolicy {
    AccessDecision evaluate(OAuthClaims claims);
}
