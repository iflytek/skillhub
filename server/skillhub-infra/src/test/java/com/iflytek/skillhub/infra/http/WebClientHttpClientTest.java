package com.iflytek.skillhub.infra.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WebClientHttpClientTest {

    private HttpServer server;
    private AtomicReference<String> requestBody;
    private AtomicReference<String> requestHeader;

    @BeforeEach
    void setUp() throws IOException {
        requestBody = new AtomicReference<>();
        requestHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/json", this::handleJson);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void postJson_sendsHeadersAndDecodesResponse() {
        WebClientHttpClient client = new WebClientHttpClient(WebClient.builder().build());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) client.post(
                endpoint(),
                Map.of("message", "hello"),
                new org.springframework.http.HttpHeaders() {{ set("X-Test", "header-value"); }},
                Map.class
        );

        assertThat(response).containsEntry("ok", true);
        assertThat(requestHeader.get()).isEqualTo("header-value");
        assertThat(requestBody.get()).contains("\"message\":\"hello\"");
    }

    @Test
    void postJson_withoutHeaders_remainsSupported() {
        WebClientHttpClient client = new WebClientHttpClient(WebClient.builder().build());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) client.post(endpoint(), Map.of("message", "legacy"), Map.class);

        assertThat(response).containsEntry("ok", true);
        assertThat(requestHeader.get()).isNull();
    }

    private String endpoint() {
        return "http://localhost:" + server.getAddress().getPort() + "/json";
    }

    private void handleJson(HttpExchange exchange) throws IOException {
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        requestHeader.set(exchange.getRequestHeaders().getFirst("X-Test"));
        byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }
}
