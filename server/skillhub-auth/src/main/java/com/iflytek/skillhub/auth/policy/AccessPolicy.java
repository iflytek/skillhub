package com.iflytek.skillhub.auth.policy;

import com.iflytek.skillhub.auth.oauth.OAuthClaims;

/**
 * Policy contract for deciding whether externally authenticated users may enter the platform.
 */
public interface AccessPolicy {
    AccessDecision evaluate(OAuthClaims claims);
}
