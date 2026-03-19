package com.iflytek.skillhub.auth.policy;

import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import java.util.Set;

/**
 * Access policy that allows login only when the OAuth email belongs to an approved domain.
 */
public class EmailDomainAccessPolicy implements AccessPolicy {
    private final Set<String> allowedDomains;

    public EmailDomainAccessPolicy(Set<String> allowedDomains) {
        this.allowedDomains = allowedDomains;
    }

    @Override
    public AccessDecision evaluate(OAuthClaims claims) {
        if (claims.email() == null) return AccessDecision.DENY;
        String domain = claims.email().substring(claims.email().indexOf('@') + 1);
        return allowedDomains.contains(domain.toLowerCase())
            ? AccessDecision.ALLOW : AccessDecision.DENY;
    }
}
