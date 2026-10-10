package com.iflytek.skillhub.auth.oauth;

import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * Provider-specific claims extractor for Feishu (Lark) OAuth users. Attributes are already
 * unwrapped from the Feishu response envelope by {@link FeishuOAuth2UserService}.
 *
 * <p>Like the GitHub and GitLab extractors, this class logs nothing: the subject, display name
 * and email it handles are exactly the values that must stay out of the logs.
 */
@Component
public class FeishuClaimsExtractor implements OAuthClaimsExtractor {

    @Override
    public String getProvider() {
        return FeishuOAuth2UserService.PROVIDER;
    }

    @Override
    public OAuthClaims extract(OAuth2UserRequest request, OAuth2User oAuth2User) {
        Map<String, Object> attrs = oAuth2User.getAttributes();

        // open_id is the stable primary subject: unique per user within one Feishu app, and it is
        // what Feishu guarantees to keep across logins. union_id stays in extra rather than acting
        // as a fallback -- a subject that can silently change identity between logins would bind
        // the same person to two platform accounts. Promoting union_id later needs an explicit
        // alias migration, not a fallback here.
        String subject = requireText(attrs.get("open_id"), "open_id");

        String email = (String) attrs.get("enterprise_email");
        if (email == null) {
            email = (String) attrs.get("email");
        }
        // Feishu emails are imported by the organization admin and not verified with the user
        // in real time, so they carry no verification signal; keep emailVerified false.
        boolean emailVerified = false;

        // name -> en_name and stop, matching the GitHub and GitLab extractors. Falling back to the
        // subject would write it into UserAccount.displayName and into UserActivatedEvent, pushing
        // the external subject somewhere event consumers may log it.
        String username = (String) attrs.get("name");
        if (username == null || username.isBlank()) {
            username = (String) attrs.get("en_name");
        }

        return new OAuthClaims(
            FeishuOAuth2UserService.PROVIDER,
            subject,
            email,
            emailVerified,
            username,
            attrs
        );
    }

    private static String requireText(Object value, String attribute) {
        String text = value == null ? null : String.valueOf(value).trim();
        if (text == null || text.isEmpty()) {
            throw new OAuth2AuthenticationException(
                new OAuth2Error("missing_subject", "Feishu user info is missing " + attribute, null)
            );
        }
        return text;
    }
}
