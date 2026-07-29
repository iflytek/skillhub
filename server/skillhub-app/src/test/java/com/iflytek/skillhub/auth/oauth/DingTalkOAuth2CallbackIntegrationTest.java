package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dingtalk"})
@TestPropertySource(properties = {
    "OAUTH2_DINGTALK_CLIENT_ID=test-dingtalk-client",
    "OAUTH2_DINGTALK_CLIENT_SECRET=test-dingtalk-secret",
    "spring.security.oauth2.client.registration.oidc.client-id=test-oidc-client",
    "spring.security.oauth2.client.registration.oidc.client-secret=test-oidc-secret",
    "spring.security.oauth2.client.registration.oidc.provider=oidc",
    "spring.security.oauth2.client.registration.oidc.authorization-grant-type=authorization_code",
    "spring.security.oauth2.client.registration.oidc.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
    "spring.security.oauth2.client.registration.oidc.scope=openid,profile,email",
    "spring.security.oauth2.client.provider.oidc.authorization-uri=https://idp.example.test/oauth2/authorize",
    "spring.security.oauth2.client.provider.oidc.token-uri=https://idp.example.test/oauth2/token",
    "spring.security.oauth2.client.provider.oidc.jwk-set-uri=https://idp.example.test/oauth2/jwks",
    "spring.security.oauth2.client.provider.oidc.user-info-uri=https://idp.example.test/userinfo",
    "spring.security.oauth2.client.provider.oidc.user-name-attribute=sub"
})
class DingTalkOAuth2CallbackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private DingTalkTokenResponseClient tokenResponseClient;

    @Autowired
    private DingTalkOAuth2UserService userService;

    @MockBean
    private OAuthLoginFlowService oauthLoginFlowService;

    private MockRestServiceServer tokenServer;
    private MockRestServiceServer userInfoServer;

    @BeforeEach
    void setUp() {
        RestTemplate tokenRestTemplate = (RestTemplate) ReflectionTestUtils.getField(
                tokenResponseClient, "restTemplate");
        RestTemplate userInfoRestTemplate = (RestTemplate) ReflectionTestUtils.getField(
                userService, "restTemplate");
        assertThat(tokenRestTemplate).isNotNull();
        assertThat(userInfoRestTemplate).isNotNull();
        tokenServer = MockRestServiceServer.bindTo(tokenRestTemplate).build();
        userInfoServer = MockRestServiceServer.bindTo(userInfoRestTemplate).build();
    }

    @Test
    void dingtalkProfileExposesProviderAndCompletesOAuth2Callback() throws Exception {
        assertThat(clientRegistrationRepository.findByRegistrationId("dingtalk")).isNotNull();
        mockMvc.perform(get("/api/v1/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='dingtalk')]").isNotEmpty());

        MvcResult authorizationResult = mockMvc.perform(get("/oauth2/authorization/dingtalk"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("scope=openid")))
                .andReturn();

        String authorizationLocation = authorizationResult.getResponse().getRedirectedUrl();
        assertThat(authorizationLocation).isNotNull();
        String encodedState = UriComponentsBuilder.fromUri(URI.create(authorizationLocation))
                .build()
                .getQueryParams()
                .getFirst("state");
        String state = UriUtils.decode(encodedState, StandardCharsets.UTF_8);
        assertThat(state).isNotBlank();
        MockHttpSession session = (MockHttpSession) authorizationResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        tokenServer.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/userAccessToken"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {"accessToken":"dingtalk-access-token","expireIn":7200}
                        """,
                        MediaType.APPLICATION_JSON));
        userInfoServer.expect(requestTo("https://api.dingtalk.com/v1.0/contact/users/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(DingTalkOAuth2Constants.ACCESS_TOKEN_HEADER, "dingtalk-access-token"))
                .andRespond(withSuccess(
                        """
                        {"openId":"stable-open-id","nick":"DingTalk User"}
                        """,
                        MediaType.APPLICATION_JSON));

        PlatformPrincipal principal = new PlatformPrincipal(
                "user-dingtalk", "DingTalk User", null, null, "dingtalk", Set.of("USER"));
        when(oauthLoginFlowService.authenticate(any(OAuthClaims.class))).thenReturn(principal);

        mockMvc.perform(get("/login/oauth2/code/dingtalk")
                        .param("code", "authorization-code")
                        .param("state", state)
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/dashboard"));

        assertThat(session.getAttribute("platformPrincipal")).isEqualTo(principal);
        tokenServer.verify();
        userInfoServer.verify();
    }

    @Test
    void standardOAuth2AndOidcAuthorizationRoutesRemainIntact() throws Exception {
        assertAuthorizationRedirectScopes("github", Set.of("read:user", "user:email"), false);
        assertAuthorizationRedirectScopes("gitlab", Set.of("read_user", "email"), false);
        assertAuthorizationRedirectScopes("oidc", Set.of("openid", "profile", "email"), true);
    }

    private void assertAuthorizationRedirectScopes(
            String registrationId,
            Set<String> expectedScopes,
            boolean expectsNonce) throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/{registrationId}", registrationId))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).isNotNull();
        var query = UriComponentsBuilder.fromUri(URI.create(location)).build().getQueryParams();
        String encodedScope = query.getFirst("scope");
        assertThat(encodedScope).isNotNull();
        assertThat(Arrays.asList(UriUtils.decode(encodedScope, StandardCharsets.UTF_8).split(" ")))
                .containsExactlyInAnyOrderElementsOf(expectedScopes);
        assertThat(query.containsKey("nonce")).isEqualTo(expectsNonce);
    }
}
