package com.iflytek.skillhub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Exercises the browser-facing Feishu OAuth flow against a local protocol-compatible provider.
 * The mock intentionally implements the authorization redirect, JSON token exchange, and wrapped
 * user-info response rather than mocking Spring Security internals.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeishuOAuthBrowserCallbackIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpServer PROVIDER_SERVER = startProviderServer();
    private static final String PROVIDER_BASE_URI = "http://127.0.0.1:" + PROVIDER_SERVER.getAddress().getPort();
    private static final AtomicReference<String> TOKEN_REQUEST_CONTENT_TYPE = new AtomicReference<>();
    private static final AtomicReference<String> TOKEN_REQUEST_BODY = new AtomicReference<>();
    private static final AtomicReference<String> USERINFO_AUTHORIZATION = new AtomicReference<>();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GlobalNamespaceMembershipService globalNamespaceMembershipService;

    @BeforeAll
    static void startProvider() {
        PROVIDER_SERVER.start();
    }

    @AfterAll
    static void stopProvider() {
        PROVIDER_SERVER.stop(0);
    }

    @DynamicPropertySource
    static void feishuProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.client.registration.feishu.client-id",
                () -> "mock-feishu-client");
        registry.add("spring.security.oauth2.client.registration.feishu.client-secret",
                () -> "mock-feishu-secret");
        registry.add("spring.security.oauth2.client.provider.feishu.authorization-uri",
                () -> PROVIDER_BASE_URI + "/authorize");
        registry.add("spring.security.oauth2.client.provider.feishu.token-uri",
                () -> PROVIDER_BASE_URI + "/oauth/v3/token");
        registry.add("spring.security.oauth2.client.provider.feishu.user-info-uri",
                () -> PROVIDER_BASE_URI + "/open-apis/authen/v1/user_info");
        registry.add("spring.security.oauth2.client.provider.feishu.user-name-attribute",
                () -> "open_id");
    }

    @Test
    void browserAuthorizationCallbackExchangesJsonTokenLoadsUserAndCreatesSession() throws Exception {
        TOKEN_REQUEST_CONTENT_TYPE.set(null);
        TOKEN_REQUEST_BODY.set(null);
        USERINFO_AUTHORIZATION.set(null);

        MvcResult authorization = mockMvc.perform(get("/oauth2/authorization/feishu")
                        .param("returnTo", "/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        URI providerAuthorization = URI.create(authorization.getResponse().getHeader("Location"));
        assertThat(providerAuthorization.getPath()).isEqualTo("/authorize");
        Map<String, String> authorizationParameters = queryParameters(providerAuthorization.getRawQuery());
        assertThat(authorizationParameters.get("client_id")).isEqualTo("mock-feishu-client");
        assertThat(authorizationParameters.get("redirect_uri"))
                .isEqualTo("http://localhost/login/oauth2/code/feishu");
        assertThat(authorizationParameters.get("state")).isNotBlank();

        HttpResponse<Void> providerAuthorizationResponse = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(providerAuthorization).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(providerAuthorizationResponse.statusCode()).isEqualTo(302);
        URI callback = URI.create(providerAuthorizationResponse.headers().firstValue("Location").orElseThrow());
        assertThat(queryParameters(callback.getRawQuery()))
                .containsEntry("code", "mock-authorization-code")
                .containsEntry("state", authorizationParameters.get("state"));

        MockHttpSession session = (MockHttpSession) authorization.getRequest().getSession(false);
        MvcResult callbackResult = mockMvc.perform(get(callback.getPath() + "?" + callback.getRawQuery())
                        .session(session))
                .andExpect(redirectedUrl("/dashboard"))
                .andReturn();

        assertThat(TOKEN_REQUEST_CONTENT_TYPE).hasValue("application/json;charset=utf-8");
        JsonNode tokenRequest = OBJECT_MAPPER.readTree(TOKEN_REQUEST_BODY.get());
        assertThat(tokenRequest.path("grant_type").asText()).isEqualTo("authorization_code");
        assertThat(tokenRequest.path("client_id").asText()).isEqualTo("mock-feishu-client");
        assertThat(tokenRequest.path("client_secret").asText()).isEqualTo("mock-feishu-secret");
        assertThat(tokenRequest.path("code").asText()).isEqualTo("mock-authorization-code");
        assertThat(USERINFO_AUTHORIZATION).hasValue("Bearer mock-access-token");
        assertThat(callbackResult.getRequest().getSession(false)).isSameAs(session);
    }

    private static HttpServer startProviderServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/authorize", FeishuOAuthBrowserCallbackIntegrationTest::authorize);
            server.createContext("/oauth/v3/token", FeishuOAuthBrowserCallbackIntegrationTest::token);
            server.createContext("/open-apis/authen/v1/user_info", FeishuOAuthBrowserCallbackIntegrationTest::userInfo);
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void authorize(HttpExchange exchange) throws IOException {
        Map<String, String> parameters = queryParameters(exchange.getRequestURI().getRawQuery());
        URI redirect = URI.create(parameters.get("redirect_uri"));
        String separator = redirect.getRawQuery() == null ? "?" : "&";
        URI callback = URI.create(redirect + separator + "code=mock-authorization-code&state="
                + parameters.get("state"));
        redirect(exchange, callback.toString());
    }

    private static void token(HttpExchange exchange) throws IOException {
        TOKEN_REQUEST_CONTENT_TYPE.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        TOKEN_REQUEST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        respond(exchange, 200, """
                {"code":0,"access_token":"mock-access-token","token_type":"Bearer",\n"expires_in":3600,"scope":"contact:user.base:readonly"}
                """.replace("\n", ""));
    }

    private static void userInfo(HttpExchange exchange) throws IOException {
        USERINFO_AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
        respond(exchange, 200, """
                {"code":0,"msg":"ok","data":{"open_id":"mock-open-id","name":"Mock Feishu User","email":"mock@example.com"}}
                """);
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static Map<String, String> queryParameters(String rawQuery) {
        Map<String, String> parameters = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return parameters;
        }
        for (String pair : rawQuery.split("&")) {
            String[] keyValue = pair.split("=", 2);
            parameters.put(urlDecode(keyValue[0]), keyValue.length == 2 ? urlDecode(keyValue[1]) : "");
        }
        return parameters;
    }

    private static String urlDecode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
