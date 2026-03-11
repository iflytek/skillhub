package com.iflytek.skillhub.auth.oauth;

import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class GitHubClaimsExtractor implements OAuthClaimsExtractor {
    @Override
    public String getProvider() { return "github"; }

    @Override
    public OAuthClaims extract(OAuth2User oAuth2User) {
        Map<String, Object> attrs = oAuth2User.getAttributes();
        return new OAuthClaims(
            "github",
            String.valueOf(attrs.get("id")),
            (String) attrs.get("email"),
            attrs.get("email") != null,
            (String) attrs.get("login"),
            attrs
        );
    }
}
