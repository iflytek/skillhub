package com.iflytek.skillhub.service.authoring.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void redirectsAreNeverFollowed() throws Exception {
        // a public URL answering 302 towards a link-local target is the classic
        // SSRF bypass: the client must surface the redirect as an error, not follow it
        AtomicInteger requests = new AtomicInteger();
        HttpServer redirector = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        redirector.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().set("Location", "http://169.254.169.254/mcp");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        redirector.start();
        try (HttpMcpClient client = new HttpMcpClient("redirector",
                "http://127.0.0.1:" + redirector.getAddress().getPort() + "/mcp",
                httpClient, objectMapper, Map.of())) {
            assertThatThrownBy(() -> client.listTools(Duration.ofSeconds(5)))
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("302");
        } finally {
            redirector.stop(0);
        }
        assertThat(requests.get())
                .as("the redirect target must never be requested")
                .isEqualTo(1);
    }

    @Test
    void oversizedResponseBodyIsRejected() throws Exception {
        // 3 MiB body against a 2 MiB cap: a hostile endpoint must not be able to
        // buffer an unbounded response in server memory
        HttpServer flood = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        flood.createContext("/", exchange -> {
            byte[] bytes = new byte[3 * 1024 * 1024];
            java.util.Arrays.fill(bytes, (byte) 'x');
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
            exchange.close();
        });
        flood.start();
        try (HttpMcpClient client = new HttpMcpClient("flood",
                "http://127.0.0.1:" + flood.getAddress().getPort() + "/mcp",
                httpClient, objectMapper, Map.of())) {
            assertThatThrownBy(() -> client.listTools(Duration.ofSeconds(10)))
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("exceeds");
        } finally {
            flood.stop(0);
        }
    }

    @Test
    void readBodyCappedAllowsBodiesUnderTheLimit() throws Exception {
        byte[] small = "hello".getBytes(StandardCharsets.UTF_8);
        assertThat(HttpMcpClient.readBodyCapped(new ByteArrayInputStream(small), 10))
                .isEqualTo("hello");
        assertThatThrownBy(() -> HttpMcpClient.readBodyCapped(
                new ByteArrayInputStream(small), 3))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("exceeds");
    }
}
