package com.iflytek.skillhub.auth.oauth;

import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
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
 *   <li>subject → unionId (unique across all apps under the same developer account)</li>
 *   <li>email → unionId@dingtalk.local (synthetic, DingTalk users may not have email)</li>
 *   <li>emailVerified → true (synthetic)</li>
 *   <li>providerLogin → nick</li>
 * </ul>
 *
 * <p>Note: unionId is used instead of openId because openId is only unique within
 * a single DingTalk application. If a user logs in through different DingTalk apps
 * under the same developer account, openId would differ, causing duplicate accounts.
 * unionId remains stable across all apps under the same developer.
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

        String unionId = (String) attrs.get("unionId");
        String openId = (String) attrs.get("openId");
        String nick = (String) attrs.get("nick");

        // unionId is required — it is the cross-app stable identity for DingTalk users
        if (unionId == null || unionId.isEmpty()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_union_id",
                            "DingTalk response missing required unionId field. "
                                    + "Ensure the 'openid' scope is configured and the DingTalk app "
                                    + "has the Contact.User.Read permission.", null));
        }

        // DingTalk users may not have email; synthesize one for downstream compatibility
        String syntheticEmail = unionId + "@dingtalk.local";

        return new OAuthClaims(
                "dingtalk",
                unionId,  // Use unionId (cross-app unique) instead of openId (single-app only)
                syntheticEmail,
                true,
                nick,
                attrs
        );
    }
}