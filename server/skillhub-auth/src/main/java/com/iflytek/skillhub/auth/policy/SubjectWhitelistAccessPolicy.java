package com.iflytek.skillhub.auth.policy;

import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import java.util.Set;

public class SubjectWhitelistAccessPolicy implements AccessPolicy {
    private final Set<String> whitelistedSubjects;

    public SubjectWhitelistAccessPolicy(Set<String> whitelistedSubjects) {
        this.whitelistedSubjects = whitelistedSubjects;
    }

    @Override
    public AccessDecision evaluate(OAuthClaims claims) {
        String key = claims.provider() + ":" + claims.subject();
        return whitelistedSubjects.contains(key)
            ? AccessDecision.ALLOW : AccessDecision.DENY;
    }
}
