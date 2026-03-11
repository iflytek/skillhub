package com.iflytek.skillhub.auth.policy;

import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import java.util.Set;

public class ProviderAllowlistAccessPolicy implements AccessPolicy {
    private final Set<String> allowedProviders;

    public ProviderAllowlistAccessPolicy(Set<String> allowedProviders) {
        this.allowedProviders = allowedProviders;
    }

    @Override
    public AccessDecision evaluate(OAuthClaims claims) {
        return allowedProviders.contains(claims.provider())
            ? AccessDecision.ALLOW : AccessDecision.DENY;
    }
}
