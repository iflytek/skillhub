package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FeishuOAuth2UserServiceTest {

    @Test
    void loadUser_unwrapsFeishuEnvelopeIntoFlatAttributes() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v1/user_info"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-123"))
                .andRespond(withSuccess(
                        """
                        {
                          "code": 0,
                          "msg": "success",
                          "data": {
                            "open_id": "ou_123",
                            "union_id": "on_456",
                            "name": "张三",
                            "avatar_url": "https://avatar.example/zhangsan.png",
                            "enterprise_email": "zhangsan@corp.example",
                            "email": "zhangsan@personal.example"
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));
        FeishuOAuth2UserService service = new FeishuOAuth2UserService(restClientBuilder);

        OAuth2User user = service.loadUser(userRequest());

        assertThat(user.getName()).isEqualTo("ou_123");
        assertThat(user.getAttributes())
                .containsEntry("open_id", "ou_123")
                .containsEntry("union_id", "on_456")
                .containsEntry("name", "张三")
                .containsEntry("avatar_url", "https://avatar.example/zhangsan.png")
                .containsEntry("enterprise_email", "zhangsan@corp.example")
                .doesNotContainKey("code")
                .doesNotContainKey("data");
        server.verify();
    }

    @Test
    void loadUser_throwsWhenFeishuReportsErrorCode() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v1/user_info"))
                .andRespond(withSuccess(
                        """
                        {"code": 99991663, "msg": "invalid access token"}
                        """,
                        MediaType.APPLICATION_JSON
                ));
        FeishuOAuth2UserService service = new FeishuOAuth2UserService(restClientBuilder);

        assertThatThrownBy(() -> service.loadUser(userRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode())
                        .isEqualTo("feishu_userinfo_error"));
        server.verify();
    }

    private OAuth2UserRequest userRequest() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("feishu")
                .clientId("cli_test123")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://open.feishu.cn/open-apis/authen/v1/authorize")
                .tokenUri("https://open.feishu.cn/open-apis/authen/v2/oauth/token")
                .userInfoUri("https://open.feishu.cn/open-apis/authen/v1/user_info")
                .userNameAttributeName("open_id")
                .clientName("飞书")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "token-123",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
        return new OAuth2UserRequest(registration, accessToken);
    }
}
