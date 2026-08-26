package com.iflytek.skillhub.auth.oauth;

import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * Provider-specific claims extractor for DingTalk (钉钉).
 *
 * <p>Maps DingTalk's non-standard user info fields into normalized {@link OAuthClaims}
 * for downstream account provisioning and access policy evaluation.
 *
 * <p>Field mapping:
 * <ul>
 *   <li>subject → unionId, falling back to openId and userId</li>
 *   <li>email → optional real email returned by DingTalk</li>
 *   <li>emailVerified → false because this endpoint does not attest email ownership</li>
 *   <li>providerLogin → nick</li>
 * </ul>
 *
 * <p>unionId is preferred because it is stable across apps under the same developer.
 * The fallbacks preserve login availability when DingTalk omits that optional field.
 */
@Component
public class DingTalkClaimsExtractor implements OAuthClaimsExtractor {

    @Override
    public String getProvider() {
        return DingTalkOAuth2Constants.REGISTRATION_ID;
    }

    @Override
    public OAuthClaims extract(OAuth2UserRequest request, OAuth2User oAuth2User) {
        Map<String, Object> attrs = oAuth2User.getAttributes();

        String subject = resolveSubject(attrs);

        String email = stringValue(attrs.get("email"));
        String providerLogin = firstNonBlank(attrs, "nick", "name");
        if (providerLogin == null) {
            providerLogin = subject;
        }

        return new OAuthClaims(
                DingTalkOAuth2Constants.REGISTRATION_ID,
                subject,
                email,
                false,
                providerLogin,
                attrs
        );
    }

    String resolveSubject(Map<String, Object> attributes) {
        String subject = firstNonBlank(
                attributes,
                DingTalkOAuth2Constants.SUBJECT_CLAIM_NAMES.toArray(String[]::new));
        if (subject == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_subject",
                            "DingTalk response is missing unionId, openId, and userId", null));
        }
        return subject;
    }

    private static String firstNonBlank(Map<String, Object> attributes, String... keys) {
        for (String key : keys) {
            String value = stringValue(attributes.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value).trim();
        return stringValue.isEmpty() ? null : stringValue;
    }
}
