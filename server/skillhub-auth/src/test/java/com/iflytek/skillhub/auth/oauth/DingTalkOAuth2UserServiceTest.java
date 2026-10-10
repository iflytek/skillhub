package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.slf4j.LoggerFactory;

class DingTalkOAuth2UserServiceTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(DingTalkOAuth2UserService.class);
    private ListAppender<ILoggingEvent> appender;

    @AfterEach
    void tearDown() {
        if (appender != null) {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void loadUser_sendsCustomTokenHeaderAndNormalizesAttributes() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.dingtalk.com/v1.0/contact/users/me"))
                // DingTalk reads the token from its own header, not Authorization: Bearer.
                .andExpect(header(DingTalkOAuth2Constants.ACCESS_TOKEN_HEADER, "token-123"))
                .andRespond(withSuccess(
                        """
                        {
                          "unionId": "un_123",
                          "openId": "op_456",
                          "nick": "张三",
                          "avatarUrl": "https://avatar.example/z.png",
                          "email": "zhangsan@corp.example",
                          "mobile": "13800000000",
                          "stateCode": "86"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));
        DingTalkOAuth2UserService service = new DingTalkOAuth2UserService(builder);

        OAuth2User user = service.loadUser(userRequest());

        assertThat(user.getName()).isEqualTo("un_123");
        assertThat(user.getAttributes())
                .containsEntry("unionId", "un_123")
                .containsEntry("nick", "张三")
                .containsEntry("email", "zhangsan@corp.example")
                // avatarUrl is aliased to the key the identity core reads.
                .containsEntry("avatar_url", "https://avatar.example/z.png");
        // Unused PII must not travel into the principal or claims.
        assertThat(user.getAttributes()).doesNotContainKeys("mobile", "stateCode", "avatarUrl");
        // openId must not survive as a usable subject candidate.
        assertThat(user.getAttributes()).doesNotContainKey("openId");
        server.verify();
    }

    @Test
    void loadUser_rejectsResponseWithoutUnionId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.dingtalk.com/v1.0/contact/users/me"))
                .andRespond(withSuccess(
                        """
                        {"openId": "op_456", "nick": "张三"}
                        """,
                        MediaType.APPLICATION_JSON
                ));
        DingTalkOAuth2UserService service = new DingTalkOAuth2UserService(builder);

        assertThatThrownBy(() -> service.loadUser(userRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("unionId");
        server.verify();
    }

    @Test
    void loadUser_rejectsOversizedResponseBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // 64 KB cap; pad a structurally valid payload past it so the size check fires, not the parser.
        String padding = "x".repeat(70 * 1024);
        server.expect(requestTo("https://api.dingtalk.com/v1.0/contact/users/me"))
                .andRespond(withSuccess(
                        "{\"unionId\":\"un_123\",\"nick\":\"" + padding + "\"}",
                        MediaType.APPLICATION_JSON
                ));
        DingTalkOAuth2UserService service = new DingTalkOAuth2UserService(builder);

        assertThatThrownBy(() -> service.loadUser(userRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode())
                        .isEqualTo("dingtalk_userinfo_error"));
        server.verify();
    }

    @Test
    void loadUser_rejectsNonSuccessfulHttpStatusWithoutExposingBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.dingtalk.com/v1.0/contact/users/me"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .body("access denied for token-123")
                        .contentType(MediaType.APPLICATION_JSON));
        DingTalkOAuth2UserService service = new DingTalkOAuth2UserService(builder);

        assertThatThrownBy(() -> service.loadUser(userRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> {
                    var error = ((OAuth2AuthenticationException) ex).getError();
                    assertThat(error.getErrorCode()).isEqualTo("dingtalk_userinfo_error");
                    assertThat(error.getDescription()).doesNotContain("token-123", "access denied");
                });
        server.verify();
    }

    @Test
    void loadUser_logsSafeProviderDiagnosticsWithoutUpstreamMessageOrToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.dingtalk.com/v1.0/contact/users/me"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .body("{\"Code\":\"Forbidden.AccessDenied.AccessTokenPermissionDenied\","
                                + "\"Data\":\"{\\\"AccessDeniedDetail\\\":{\\\"requiredScopes\\\":[\\\"Contact.User.Read\\\"]},"
                                + "\\\"RequestId\\\":\\\"req-123\\\"}\","
                                + "\"Message\":\"secret upstream message token-123\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        attachAppender();
        DingTalkOAuth2UserService service = new DingTalkOAuth2UserService(builder);

        assertThatThrownBy(() -> service.loadUser(userRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class);

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                        .contains("code=Forbidden.AccessDenied.AccessTokenPermissionDenied")
                        .contains("requiredScopes=Contact.User.Read")
                        .contains("requestId=req-123")
                        .doesNotContain("secret upstream message", "token-123"));
        server.verify();
    }

    private void attachAppender() {
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @Test
    void loadUser_errorDescriptionDoesNotEchoUpstreamTextOrToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.dingtalk.com/v1.0/contact/users/me"))
                .andRespond(withSuccess("not json at all: token-123", MediaType.APPLICATION_JSON));
        DingTalkOAuth2UserService service = new DingTalkOAuth2UserService(builder);

        assertThatThrownBy(() -> service.loadUser(userRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> {
                    String description = ((OAuth2AuthenticationException) ex).getError().getDescription();
                    assertThat(description).doesNotContain("token-123");
                });
        server.verify();
    }

    private OAuth2UserRequest userRequest() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("dingtalk")
                .clientId("dingoauth_test")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://login.dingtalk.com/oauth2/auth")
                .tokenUri("https://api.dingtalk.com/v1.0/oauth2/userAccessToken")
                .userInfoUri("https://api.dingtalk.com/v1.0/contact/users/me")
                .userNameAttributeName("unionId")
                .clientName("钉钉")
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
