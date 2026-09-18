package com.iflytek.skillhub.auth.oauth;

import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * Provider-specific claims extractor for DingTalk (钉钉). Attributes are already fetched by
 * {@link DingTalkOAuth2UserService}, which reads them from DingTalk's non-standard user info
 * endpoint.
 *
 * <p>Like the GitHub and Feishu extractors, this class logs nothing: the subject, display name and
 * email it handles are exactly the values that must stay out of the logs.
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

        String subject = requireText(
                attrs.get(DingTalkOAuth2Constants.SUBJECT_CLAIM_NAME),
                DingTalkOAuth2Constants.SUBJECT_CLAIM_NAME
        );

        String email = text(attrs.get("email"));
        // DingTalk's user info endpoint returns the email recorded by the organization admin and
        // does not attest that the user controls it, so it carries no verification signal and
        // cannot be used to join an existing account.
        boolean emailVerified = false;

        // nick -> name and stop. Falling back to the subject would write it into
        // UserAccount.displayName and into UserActivatedEvent, pushing the external subject
        // somewhere event consumers may log it.
        String providerLogin = text(attrs.get("nick"));
        if (providerLogin == null) {
            providerLogin = text(attrs.get("name"));
        }

        return new OAuthClaims(
                DingTalkOAuth2Constants.REGISTRATION_ID,
                subject,
                email,
                emailVerified,
                providerLogin,
                attrs
        );
    }

    private static String requireText(Object value, String attribute) {
        String text = text(value);
        if (text == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_subject", "DingTalk user info is missing " + attribute, null)
            );
        }
        return text;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
