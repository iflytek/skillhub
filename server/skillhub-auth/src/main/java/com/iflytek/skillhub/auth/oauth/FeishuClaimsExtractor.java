package com.iflytek.skillhub.auth.oauth;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * Provider-specific claims extractor for Feishu (Lark) OAuth users. Attributes are already
 * unwrapped from the Feishu response envelope by {@link FeishuOAuth2UserService}.
 */
@Component
public class FeishuClaimsExtractor implements OAuthClaimsExtractor {

    private static final Logger log = LoggerFactory.getLogger(FeishuClaimsExtractor.class);

    @Override
    public String getProvider() {
        return FeishuOAuth2UserService.PROVIDER;
    }

    @Override
    public OAuthClaims extract(OAuth2UserRequest request, OAuth2User oAuth2User) {
        Map<String, Object> attrs = oAuth2User.getAttributes();

        // open_id is unique within the Feishu app; union_id is kept in extra for potential
        // cross-app identity migration later.
        String subject = String.valueOf(attrs.get("open_id"));

        String email = (String) attrs.get("enterprise_email");
        if (email == null) {
            email = (String) attrs.get("email");
        }
        boolean emailVerified = email != null;

        String username = (String) attrs.get("name");
        if (username == null || username.isBlank()) {
            username = (String) attrs.get("en_name");
        }
        if (username == null || username.isBlank()) {
            username = "feishu-" + subject;
        }

        log.info("Feishu OAuth claims extracted - subject: {}, username: {}, email present: {}",
                subject, username, email != null);

        return new OAuthClaims(
            FeishuOAuth2UserService.PROVIDER,
            subject,
            email,
            emailVerified,
            username,
            attrs
        );
    }
}
