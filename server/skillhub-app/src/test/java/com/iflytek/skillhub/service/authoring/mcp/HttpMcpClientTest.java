package com.iflytek.skillhub.service.authoring.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises HttpMcpClient against a real local HTTP server implementing the MCP
 * streamable-HTTP protocol, including the session-id contract and SSE-framed
 * responses.
 */
class HttpMcpClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private TestingMcpHttpServer mcpServer;

    @BeforeEach
    void startServer() throws Exception {
        mcpServer = new TestingMcpHttpServer(List.of("get_forecast", "lookup_city"));
    }

    @AfterEach
    void stopServer() {
        if (mcpServer != null) {
            mcpServer.close();
        }
    }

    private HttpMcpClient client() {
        return new HttpMcpClient("weather", mcpServer.endpoint(), httpClient, objectMapper, Map.of());
    }

    @Test
    void initializesListsToolsAndCallsTools() throws Exception {
        try (HttpMcpClient client = client()) {
            List<McpTool> tools = client.listTools(Duration.ofSeconds(5));
            assertThat(tools).extracting(McpTool::name)
                    .containsExactly("get_forecast", "lookup_city");
            assertThat(tools.get(0).description()).isEqualTo("test tool get_forecast");

            String output = client.callTool("get_forecast",
                    Map.of("city", "Paris"), Duration.ofSeconds(5));
            assertThat(output).isEqualTo("result of get_forecast: {\"city\":\"Paris\"}");
            assertThat(mcpServer.toolCalls).hasSize(1);
        }
        assertThat(mcpServer.initializeCount.get()).isEqualTo(1);
    }

    @Test
    void sessionHeaderIsReusedAfterInitialize() throws Exception {
        // the fake server answers tools/list with 400 unless the Mcp-Session-Id
        // captured during initialize is replayed — so a successful listing proves it
        try (HttpMcpClient client = client()) {
            assertThat(client.listTools(Duration.ofSeconds(5))).hasSize(2);
        }
        assertThat(mcpServer.sawRequestWithoutSessionId)
                .as("tools/list must carry the session id from initialize")
                .isFalse();
    }

    @Test
    void parsesSseFramedResponses() throws Exception {
        try (TestingMcpHttpServer sseServer =
                     new TestingMcpHttpServer(List.of("echo"), true);
             HttpMcpClient client = new HttpMcpClient("sse", sseServer.endpoint(),
                     httpClient, objectMapper, Map.of())) {
            List<McpTool> tools = client.listTools(Duration.ofSeconds(5));
            assertThat(tools).extracting(McpTool::name).containsExactly("echo");
        }
    }

    @Test
    void toolErrorResultBecomesException() throws Exception {
        try (HttpMcpClient client = client()) {
            client.listTools(Duration.ofSeconds(5));
            assertThatThrownBy(() -> client.callTool("boom", Map.of(), Duration.ofSeconds(5)))
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("boom");
        }
    }

    @Test
    void unreachableServerFailsFastWithClearMessage() {
        try (HttpMcpClient client = new HttpMcpClient("dead",
                "http://127.0.0.1:9/mcp", httpClient, objectMapper, Map.of())) {
            assertThatThrownBy(() -> client.listTools(Duration.ofSeconds(5)))
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("dead");
        }
    }
}
