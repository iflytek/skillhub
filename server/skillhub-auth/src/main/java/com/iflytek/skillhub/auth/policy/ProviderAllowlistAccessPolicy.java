package com.iflytek.skillhub.auth.policy;

import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import java.util.Set;

/**
 * Access policy that limits login to explicitly allowed OAuth providers.
 */
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
