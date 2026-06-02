package com.iflytek.skillhub.auth.oauth;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;

/**
 * OAuth2UserService for DingTalk — handles DingTalk's non-standard user info
 * endpoint which uses a custom header {@code x-acs-dingtalk-access-token}
 * instead of the standard {@code Authorization: Bearer} header.
 *
 * <p>After fetching user info, this service delegates to
 * {@link OAuthLoginFlowService#authenticate(OAuthClaims)} for access policy
 * evaluation and identity binding, consistent with the standard OAuth2 flow.
 */
@Component
public class DingTalkOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final Logger log = LoggerFactory.getLogger(DingTalkOAuth2UserService.class);

    private final RestTemplate restTemplate;
    private final DingTalkClaimsExtractor claimsExtractor;
    private final OAuthLoginFlowService oauthLoginFlowService;

    @Autowired
    public DingTalkOAuth2UserService(DingTalkClaimsExtractor claimsExtractor,
                                      OAuthLoginFlowService oauthLoginFlowService) {
        this.restTemplate = buildRestTemplate();
        this.claimsExtractor = claimsExtractor;
        this.oauthLoginFlowService = oauthLoginFlowService;
    }

    /** Package-visible constructor for unit testing with a mock RestTemplate. */
    DingTalkOAuth2UserService(DingTalkClaimsExtractor claimsExtractor,
                               OAuthLoginFlowService oauthLoginFlowService,
                               RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.claimsExtractor = claimsExtractor;
        this.oauthLoginFlowService = oauthLoginFlowService;
    }

    private static RestTemplate buildRestTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        String accessToken = userRequest.getAccessToken().getTokenValue();
        String userInfoUri = userRequest.getClientRegistration().getProviderDetails()
                .getUserInfoEndpoint().getUri();

        // Fetch user info using DingTalk's custom header
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-acs-dingtalk-access-token", accessToken);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                userInfoUri,
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

        // Extract claims — use unionId as the name attribute (cross-app unique identity)
        OAuthClaims claims = claimsExtractor.extract(userRequest, new DefaultOAuth2User(
                java.util.Collections.emptyList(), userAttributes, "unionId"));

        log.info("DingTalk OAuth2 login: subject={}, providerLogin={}", claims.subject(), claims.providerLogin());

        // Delegate to OAuthLoginFlowService for access policy evaluation and identity binding
        PlatformPrincipal principal = oauthLoginFlowService.authenticate(claims);

        // Build OAuth2User with principal and authorities, consistent with CustomOAuth2UserService
        userAttributes.put("platformPrincipal", principal);
        userAttributes.put("providerLogin", principal.userId());

        var authorities = new LinkedHashSet<GrantedAuthority>();
        principal.platformRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .forEach(authorities::add);

        return new DefaultOAuth2User(
                authorities,
                userAttributes,
                "providerLogin"
        );
    }
}