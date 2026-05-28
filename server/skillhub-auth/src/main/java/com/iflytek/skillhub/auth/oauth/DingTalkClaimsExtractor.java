package com.iflytek.skillhub.auth.oauth;

import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Provider-specific claims extractor for DingTalk (钉钉).
 *
 * <p>Maps DingTalk's non-standard user info fields into normalized {@link OAuthClaims}
 * for downstream account provisioning and access policy evaluation.
 *
 * <p>Field mapping:
 * <ul>
 *   <li>subject → openId</li>
 *   <li>email → unionId@dingtalk.local (synthetic, DingTalk users may not have email)</li>
 *   <li>emailVerified → true (synthetic)</li>
 *   <li>providerLogin → nick</li>
 * </ul>
 */
@Component
public class DingTalkClaimsExtractor implements OAuthClaimsExtractor {

    @Override
    public String getProvider() {
        return "dingtalk";
    }

    @Override
    public OAuthClaims extract(OAuth2UserRequest request, OAuth2User oAuth2User) {
        Map<String, Object> attrs = oAuth2User.getAttributes();

        String openId = (String) attrs.get("openId");
        String unionId = (String) attrs.get("unionId");
        String nick = (String) attrs.get("nick");

        // DingTalk users may not have email; synthesize one for downstream compatibility
        String syntheticEmail = (unionId != null && !unionId.isEmpty())
                ? unionId + "@dingtalk.local"
                : (openId != null ? openId + "@dingtalk.local" : null);

        return new OAuthClaims(
                "dingtalk",
                openId,
                syntheticEmail,
                true,
                nick,
                attrs
        );
    }
}