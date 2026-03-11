package com.iflytek.skillhub.auth.oauth;

import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.policy.AccessDecision;
import com.iflytek.skillhub.auth.policy.AccessPolicy;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.user.UserStatus;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final Map<String, OAuthClaimsExtractor> extractors;
    private final AccessPolicy accessPolicy;
    private final IdentityBindingService identityBindingService;

    public CustomOAuth2UserService(List<OAuthClaimsExtractor> extractorList,
                                    AccessPolicy accessPolicy,
                                    IdentityBindingService identityBindingService) {
        this.extractors = extractorList.stream()
            .collect(Collectors.toMap(OAuthClaimsExtractor::getProvider, Function.identity()));
        this.accessPolicy = accessPolicy;
        this.identityBindingService = identityBindingService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = delegate.loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();

        OAuthClaimsExtractor extractor = extractors.get(registrationId);
        if (extractor == null) {
            throw new OAuth2AuthenticationException(
                new OAuth2Error("unsupported_provider", "Unsupported: " + registrationId, null));
        }

        OAuthClaims claims = extractor.extract(oAuth2User);
        AccessDecision decision = accessPolicy.evaluate(claims);

        UserStatus initialStatus = switch (decision) {
            case ALLOW -> UserStatus.ACTIVE;
            case PENDING_APPROVAL -> UserStatus.PENDING;
            case DENY -> throw new OAuth2AuthenticationException(
                new OAuth2Error("access_denied", "Access denied by policy", null));
        };

        PlatformPrincipal principal = identityBindingService.bindOrCreate(claims, initialStatus);

        var attrs = new HashMap<>(oAuth2User.getAttributes());
        attrs.put("platformPrincipal", principal);

        return new DefaultOAuth2User(oAuth2User.getAuthorities(), attrs, "login");
    }
}
