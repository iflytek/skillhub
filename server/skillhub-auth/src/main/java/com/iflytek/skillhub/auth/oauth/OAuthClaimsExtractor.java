package com.iflytek.skillhub.auth.oauth;

import org.springframework.security.oauth2.core.user.OAuth2User;

public interface OAuthClaimsExtractor {
    String getProvider();
    OAuthClaims extract(OAuth2User oAuth2User);
}
