package com.iflytek.skillhub.auth.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Provider-specific claims extractor that enriches GitLab OAuth users with their
 * verified email information.
 *
 * <p>GitLab OAuth2 user info endpoint returns user profile data. This extractor
 * fetches additional email information from GitLab API when needed.
 */
@Component
public class GitLabClaimsExtractor implements OAuthClaimsExtractor {

    private static final Logger log = LoggerFactory.getLogger(GitLabClaimsExtractor.class);

    private final RestClient restClient;

    public GitLabClaimsExtractor(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public String getProvider() {
        return "gitlab";
    }

    @Override
    public OAuthClaims extract(OAuth2UserRequest request, OAuth2User oAuth2User) {
        Map<String, Object> attrs = oAuth2User.getAttributes();
        log.debug("Extracting GitLab OAuth claims for user attributes: {}", attrs.keySet());

        // GitLab returns email directly in user info
        String email = (String) attrs.get("email");

        boolean emailVerified = isConfirmed(attrs.get("confirmed_at"));

        log.debug("Initial email from GitLab: {}, verified: {}", email, emailVerified);

        // If email is not verified or not present, try to fetch from emails API
        if (email == null || !emailVerified) {
            log.debug("Email not verified or missing, attempting to fetch from GitLab emails API");
            GitLabEmail primaryEmail = loadPrimaryEmail(request);
            if (primaryEmail != null) {
                email = primaryEmail.email();
                emailVerified = true;
                log.debug("Found verified email from GitLab API: {}", email);
            } else {
                log.debug("No verified email found from GitLab emails API");
            }
        }

        // GitLab uses "username" for login name
        String username = (String) attrs.get("username");
        if (username == null) {
            username = (String) attrs.get("login");
        }

        String subject = String.valueOf(attrs.get("id"));
        log.info("GitLab OAuth claims extracted - subject: {}, username: {}, email: {}, emailVerified: {}",
                subject, username, email, emailVerified);

        return new OAuthClaims(
            "gitlab",
            subject,
            email,
            emailVerified,
            username,
            attrs
        );
    }

    private GitLabEmail loadPrimaryEmail(OAuth2UserRequest request) {
        String baseUrl = getGitLabApiBaseUrl(request);
        log.debug("Loading primary email from GitLab API base URL: {}", baseUrl);

        try {
            List<GitLabEmail> emails = restClient.get()
                .uri(baseUrl + "/user/emails")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + request.getAccessToken().getTokenValue())
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<GitLabEmail>>() {});

            if (emails == null || emails.isEmpty()) {
                log.debug("No emails returned from GitLab emails API");
                return null;
            }

            log.debug("Retrieved {} emails from GitLab API", emails.size());

            // Return the primary verified email
            return emails.stream()
                .filter(GitLabEmail::confirmed)
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            log.warn("Failed to fetch emails from GitLab API: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Determines the GitLab API base URL from the provider configuration.
     * The user-info-uri is configured as ${OAUTH2_GITLAB_BASE_URI}/api/v4/user,
     * so we simply remove the /user suffix to get the API base URL.
     */
    private String getGitLabApiBaseUrl(OAuth2UserRequest request) {
        String userInfoUri = request.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUri();
        log.debug("GitLab user info URI: {}", userInfoUri);
        // user-info-uri format: ${OAUTH2_GITLAB_BASE_URI}/api/v4/user
        // Remove /user suffix to get API base URL
        String baseUrl = userInfoUri.substring(0, userInfoUri.length() - "/user".length());
        log.debug("GitLab API base URL: {}", baseUrl);
        return baseUrl;
    }

    /**
     * GitLab marks a confirmed email by populating confirmed_at.
     */
    private record GitLabEmail(String email, @JsonProperty("confirmed_at") String confirmedAt) {
        boolean confirmed() {
            return confirmedAt != null && !confirmedAt.isBlank();
        }
    }

    private boolean isConfirmed(Object confirmedAt) {
        return confirmedAt instanceof String value && !value.isBlank();
    }
}
