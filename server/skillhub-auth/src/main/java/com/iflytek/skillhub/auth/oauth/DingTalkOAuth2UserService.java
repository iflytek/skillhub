package com.iflytek.skillhub.auth.oauth;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.user.UserStatus;

/**
 * OAuth2UserService for DingTalk — handles DingTalk's non-standard user info
 * endpoint which uses a custom header {@code x-acs-dingtalk-access-token}
 * instead of the standard {@code Authorization: Bearer} header.
 */
@Component
public class DingTalkOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final Logger log = LoggerFactory.getLogger(DingTalkOAuth2UserService.class);

    private final RestTemplate restTemplate;
    private final IdentityBindingService identityBindingService;
    private final DingTalkClaimsExtractor claimsExtractor;

    public DingTalkOAuth2UserService(IdentityBindingService identityBindingService,
                                      DingTalkClaimsExtractor claimsExtractor) {
        this.restTemplate = new RestTemplate();
        this.identityBindingService = identityBindingService;
        this.claimsExtractor = claimsExtractor;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        String accessToken = userRequest.getAccessToken().getTokenValue();

        // Fetch user info using DingTalk's custom header
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-acs-dingtalk-access-token", accessToken);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.dingtalk.com/v1.0/contact/users/me",
                HttpMethod.GET,
                requestEntity,
                Map.class
        );

        Map<String, Object> attributes = response.getBody() != null ? response.getBody() : Map.of();

        // Map DingTalk response to standard attributes
        Map<String, Object> userAttributes = new HashMap<>(attributes);
        userAttributes.putIfAbsent("openId", attributes.get("openId"));
        userAttributes.putIfAbsent("nickName", attributes.get("nick"));
        userAttributes.putIfAbsent("avatarUrl", attributes.get("avatarUrl"));

        // Extract claims and create PlatformPrincipal
        OAuthClaims claims = claimsExtractor.extract(userRequest, new DefaultOAuth2User(
                java.util.Collections.emptyList(), userAttributes, "openId"));

        log.info("DingTalk OAuth2 login: subject={}, providerLogin={}", claims.subject(), claims.providerLogin());

        // Bind or create user account
        PlatformPrincipal principal = identityBindingService.bindOrCreate(claims, UserStatus.ACTIVE);

        // Put platformPrincipal in attributes for OAuth2LoginSuccessHandler
        userAttributes.put("platformPrincipal", principal);

        return new DefaultOAuth2User(
                java.util.Collections.emptyList(),
                userAttributes,
                "openId"
        );
    }
}