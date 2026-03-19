package com.iflytek.skillhub.auth.oauth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * Provider-specific claims extractor that enriches GitHub OAuth users with their primary verified
 * email when necessary.
 */
@Component
public class GitHubClaimsExtractor implements OAuthClaimsExtractor {

    private final RestClient restClient = RestClient.builder()
        .baseUrl("https://api.github.com")
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();

    @Override
    public String getProvider() { return "github"; }

    @Override
    public OAuthClaims extract(OAuth2UserRequest request, OAuth2User oAuth2User) {
        Map<String, Object> attrs = oAuth2User.getAttributes();
        GitHubEmail primaryEmail = loadPrimaryEmail(request);
        String email = primaryEmail != null ? primaryEmail.email() : (String) attrs.get("email");
        boolean emailVerified = primaryEmail != null
            ? primaryEmail.verified()
            : attrs.get("email") != null;

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
        List<GitHubEmail> emails = restClient.get()
            .uri("/user/emails")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + request.getAccessToken().getTokenValue())
            .retrieve()
            .body(new org.springframework.core.ParameterizedTypeReference<List<GitHubEmail>>() {});

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
