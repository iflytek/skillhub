package com.iflytek.skillhub.auth.oauth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Provider-specific claims extractor that enriches GitHub OAuth users with their primary verified
 * email when necessary.
 */
@Component
public class GitHubClaimsExtractor implements OAuthClaimsExtractor {

    private static final String DEFAULT_API_BASE_URL = "https://api.github.com";

    private final RestClient restClient;

    public GitHubClaimsExtractor(RestClient.Builder restClientBuilder) {
        this(restClientBuilder, DEFAULT_API_BASE_URL);
    }

    @Autowired
    public GitHubClaimsExtractor(RestClient.Builder restClientBuilder,
                                 @org.springframework.beans.factory.annotation.Value("${skillhub.auth.github.api-base-url:https://api.github.com}") String apiBaseUrl) {
        this.restClient = restClientBuilder
            .baseUrl(apiBaseUrl)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public String getProvider() { return "github"; }

    @Override
    public OAuthClaims extract(OAuth2UserRequest request, OAuth2User oAuth2User) {
        Map<String, Object> attrs = oAuth2User.getAttributes();
        GitHubEmail primaryEmail = loadPrimaryEmail(request);
        String email = primaryEmail != null ? primaryEmail.email() : (String) attrs.get("email");
        boolean emailVerified = primaryEmail != null && primaryEmail.verified();

        return new OAuthClaims(
            "github",
            String.valueOf(attrs.get("id")),
            email,
            emailVerified,
            (String) attrs.get("login"),
            attrs
        );
    }

    private GitHubEmail loadPrimaryEmail(OAuth2UserRequest request) {
        List<GitHubEmail> emails;
        try {
            emails = restClient.get()
                .uri("/user/emails")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + request.getAccessToken().getTokenValue())
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<GitHubEmail>>() {});
        } catch (RestClientException exception) {
            // A provider lookup failure must never turn an unverified profile email into a trusted email.
            return null;
        }

        if (emails == null || emails.isEmpty()) {
            return null;
        }

        return emails.stream()
            .filter(GitHubEmail::verified)
            .sorted(Comparator.comparing(GitHubEmail::primary).reversed())
            .findFirst()
            .orElse(null);
    }

    private record GitHubEmail(String email, boolean primary, boolean verified) {}
}
