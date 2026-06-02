package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;

class DingTalkOAuth2UserServiceTest {

    private DingTalkOAuth2UserService service;
    private DingTalkClaimsExtractor claimsExtractor;
    private OAuthLoginFlowService oauthLoginFlowService;
    private MockRestServiceServer mockServer;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        claimsExtractor = new DingTalkClaimsExtractor();
        oauthLoginFlowService = mock(OAuthLoginFlowService.class);
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        service = new DingTalkOAuth2UserService(claimsExtractor, oauthLoginFlowService, restTemplate);
    }

    @Test
    void loadUser_fetchesUserInfoWithCustomHeaderAndReturnsOAuth2User() {
        // Mock DingTalk user info API response
        mockServer.expect(requestTo("https://api.dingtalk.com/v1.0/contact/users/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-acs-dingtalk-access-token", "test-access-token"))
                .andRespond(withSuccess(
                        """
                        {
                          "unionId": "union123",
                          "openId": "open456",
                          "nick": "测试用户",
                          "avatarUrl": "https://example.com/avatar.jpg"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        // Mock OAuthLoginFlowService to return a principal
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-union123", "测试用户", "union123@dingtalk.local",
                "https://example.com/avatar.jpg", "dingtalk", Set.of("USER")
        );
        when(oauthLoginFlowService.authenticate(any(OAuthClaims.class))).thenReturn(principal);

        OAuth2User oauth2User = service.loadUser(userRequest());

        assertThat(oauth2User.getName()).isEqualTo("user-union123");
        assertThat(oauth2User.getAttributes().get("unionId")).isEqualTo("union123");
        assertThat(oauth2User.getAttributes().get("platformPrincipal")).isEqualTo(principal);
        assertThat(oauth2User.getAttributes().get("providerLogin")).isEqualTo("user-union123");
        assertThat(oauth2User.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"))).isTrue();
        mockServer.verify();
    }

    @Test
    void loadUser_readsUserInfoUriFromClientRegistration() {
        // Use a custom userInfoUri to verify it's read from config, not hardcoded
        String customUri = "https://custom-api.example.com/v1.0/contact/users/me";

        mockServer.expect(requestTo(customUri))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-acs-dingtalk-access-token", "test-access-token"))
                .andRespond(withSuccess(
                        """
                        {
                          "unionId": "union789",
                          "openId": "open012",
                          "nick": "自定义用户"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        PlatformPrincipal principal = new PlatformPrincipal(
                "user-union789", "自定义用户", "union789@dingtalk.local",
                null, "dingtalk", Set.of("USER")
        );
        when(oauthLoginFlowService.authenticate(any(OAuthClaims.class))).thenReturn(principal);

        OAuth2User oauth2User = service.loadUser(userRequestWithCustomUri(customUri));

        assertThat(oauth2User.getName()).isEqualTo("user-union789");
        mockServer.verify();
    }

    private OAuth2UserRequest userRequest() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("dingtalk")
                .clientId("dingzgzf3b9k7jv74iq2")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid")
                .authorizationUri("https://login.dingtalk.com/oauth2/auth")
                .tokenUri("https://api.dingtalk.com/v1.0/oauth2/userAccessToken")
                .userInfoUri("https://api.dingtalk.com/v1.0/contact/users/me")
                .userNameAttributeName("unionId")
                .clientName("钉钉")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "test-access-token",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
        return new OAuth2UserRequest(registration, accessToken);
    }

    private OAuth2UserRequest userRequestWithCustomUri(String userInfoUri) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("dingtalk")
                .clientId("dingzgzf3b9k7jv74iq2")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid")
                .authorizationUri("https://login.dingtalk.com/oauth2/auth")
                .tokenUri("https://api.dingtalk.com/v1.0/oauth2/userAccessToken")
                .userInfoUri(userInfoUri)
                .userNameAttributeName("unionId")
                .clientName("钉钉")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "test-access-token",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
        return new OAuth2UserRequest(registration, accessToken);
    }
}