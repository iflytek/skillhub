package com.iflytek.skillhub.auth.oauth;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

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
        headers.set(DingTalkOAuth2Constants.ACCESS_TOKEN_HEADER, accessToken);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response;
        try {
            response = restTemplate.exchange(
                    userInfoUri,
                    HttpMethod.GET,
                    requestEntity,
                    new ParameterizedTypeReference<>() {
                    }
            );
        } catch (RestClientResponseException e) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("user_info_request_failed",
                            "DingTalk user-info request failed with HTTP " + e.getStatusCode().value(), null));
        } catch (RestClientException e) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("user_info_request_failed",
                            "DingTalk user-info request failed", null));
        }

        Map<String, Object> attributes = response.getBody() != null ? response.getBody() : Map.of();

        Map<String, Object> userAttributes = new HashMap<>(attributes);
        if (attributes.get("avatarUrl") != null) {
            userAttributes.putIfAbsent("avatar_url", attributes.get("avatarUrl"));
        }

        String subject = claimsExtractor.resolveSubject(userAttributes);
        userAttributes.put(DingTalkOAuth2Constants.SUBJECT_ATTRIBUTE, subject);

        OAuthClaims claims = claimsExtractor.extract(userRequest, new DefaultOAuth2User(
                java.util.Collections.emptyList(), userAttributes, DingTalkOAuth2Constants.SUBJECT_ATTRIBUTE));

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
