package com.iflytek.skillhub.service.authoring.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iflytek.skillhub.config.AuthoringProperties;
import com.iflytek.skillhub.domain.authoring.runtime.RuntimeEventSink;
import com.iflytek.skillhub.domain.authoring.runtime.RuntimeExecutionContext;
import com.iflytek.skillhub.domain.authoring.runtime.TaskResult;
import com.iflytek.skillhub.domain.authoring.spec.TaskType;
import com.iflytek.skillhub.domain.authoring.spec.ValidationTaskSpec;
import com.iflytek.skillhub.service.authoring.mcp.McpClientFactory;
import com.iflytek.skillhub.service.authoring.mcp.TestingMcpHttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the full prompt-task agent loop against a fake OpenAI-compatible
 * endpoint and a fake MCP server: tools are discovered over MCP, exposed to the
 * model, executed for real when called, and every step lands in the trace sink.
 * Also pins the toolFilters / toolAllowlist restrictions on what gets exposed.
 */
class OpenAiCompatibleRuntimeAdapterToolLoopTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path workspace;

    private TestingMcpHttpServer mcpServer;
    private FakeOpenAiEndpoint openAi;
    private final List<TraceEvent> trace = new ArrayList<>();
    private OpenAiCompatibleRuntimeAdapter adapter;
    private AuthoringProperties properties;

    private record TraceEvent(String kind, String tool, String payload) {
    }

    /** Records toolCall/toolResult/agentMessage/log in order. */
    private final RuntimeEventSink sink = new RuntimeEventSink() {
        @Override
        public void agentMessage(String taskName, String content) {
            trace.add(new TraceEvent("agentMessage", null, content));
        }

        @Override
        public void toolCall(String taskName, String tool, String argumentsJson) {
            trace.add(new TraceEvent("toolCall", tool, argumentsJson));
        }

        @Override
        public void toolResult(String taskName, String tool, String summaryJson) {
            trace.add(new TraceEvent("toolResult", tool, summaryJson));
        }

        @Override
        public void log(String taskName, String stream, String line) {
            trace.add(new TraceEvent("log", stream, line));
        }
    };

    @BeforeEach
    void start() throws Exception {
        mcpServer = new TestingMcpHttpServer(List.of("get_forecast", "lookup_city"));
        openAi = new FakeOpenAiEndpoint();
        properties = new AuthoringProperties();
        properties.getOpenAiCompatible().setEnabled(true);
        adapter = new OpenAiCompatibleRuntimeAdapter(properties, MAPPER, new McpClientFactory(MAPPER));
        Files.writeString(workspace.resolve("SKILL.md"), "---\nname: test\n---\nUse the weather tools.\n");
        trace.clear();
    }

    @AfterEach
    void stop() {
        if (mcpServer != null) {
            mcpServer.close();
        }
        if (openAi != null) {
            openAi.close();
        }
    }

    private Map<String, Object> weatherServer() {
        return Map.of("name", "weather", "transport", "http", "endpoint", mcpServer.endpoint());
    }

    private TaskResult run(List<Map<String, Object>> mcpServers, List<String> allowlist) throws Exception {
        ValidationTaskSpec task = new ValidationTaskSpec("ask", "asks the weather",
                TaskType.PROMPT, null, List.of(), "What is the weather in Paris?", 30_000, List.of());
        RuntimeExecutionContext context = new RuntimeExecutionContext(99L, workspace,
                Map.of("endpoint", openAi.baseUrl() + "/", "model", "test-model"),
                allowlist, mcpServers, () -> false);
        return adapter.execute(context, task, sink);
    }

    private static ObjectNode assistantMessage(String content) {
        ObjectNode message = MAPPER.createObjectNode();
        message.put("role", "assistant");
        message.put("content", content);
        return message;
    }

    private static ObjectNode toolCallMessage(String functionName, String arguments) {
        ObjectNode message = MAPPER.createObjectNode();
        message.put("role", "assistant");
        message.put("content", "");
        ObjectNode call = message.putArray("tool_calls").addObject();
        call.put("id", "call-1");
        call.put("type", "function");
        call.putObject("function").put("name", functionName).put("arguments", arguments);
        return message;
    }

    @Test
    void executesMcpToolAndTracesEveryStep() throws Exception {
        openAi.script(List.of(
                toolCallMessage("weather__get_forecast", "{\"city\":\"Paris\"}"),
                assistantMessage("The forecast for Paris is sunny.")));

        TaskResult result = run(List.of(weatherServer()), List.of());

        assertThat(result.response()).isEqualTo("The forecast for Paris is sunny.");
        assertThat(result.toolCallCount()).isEqualTo(1);

        // the real MCP server saw the call with the model's arguments
        assertThat(mcpServer.toolCalls).hasSize(1);
        assertThat(mcpServer.toolCalls.get(0).getKey()).isEqualTo("get_forecast");
        assertThat(mcpServer.toolCalls.get(0).getValue().path("city").asText()).isEqualTo("Paris");

        // two chat rounds; the second carries the tool reply back to the model
        assertThat(openAi.requests).hasSize(2);
        assertThat(exposedFunctionNames(0)).containsExactlyInAnyOrder(
                "weather__get_forecast", "weather__lookup_city");
        JsonNode secondMessages = openAi.requests.get(1).path("messages");
        assertThat(secondMessages).anySatisfy(message -> {
            assertThat(message.path("role").asText()).isEqualTo("tool");
            assertThat(message.path("tool_call_id").asText()).isEqualTo("call-1");
            assertThat(message.path("content").asText()).contains("result of get_forecast");
        });

        // the trace shows both chat rounds, the tool invocation, and the result
        assertThat(trace).extracting(TraceEvent::kind)
                .containsExactly("toolCall", "toolCall", "toolResult", "toolCall", "agentMessage");
        assertThat(trace.get(1).tool()).isEqualTo("mcp:weather/get_forecast");
        assertThat(trace.get(1).payload()).contains("Paris");
        assertThat(trace.get(2).tool()).isEqualTo("mcp:weather/get_forecast");
        assertThat(trace.get(2).payload()).contains("\"ok\":true");
    }

    @Test
    void toolAllowlistRestrictsExposedTools() throws Exception {
        openAi.script(List.of(assistantMessage("I only have the lookup tool.")));

        run(List.of(weatherServer()), List.of("lookup_city"));

        assertThat(openAi.requests).hasSize(1);
        assertThat(exposedFunctionNames(0)).containsExactly("weather__lookup_city");
    }

    @Test
    void serverToolFiltersRestrictExposedTools() throws Exception {
        openAi.script(List.of(assistantMessage("done")));

        Map<String, Object> filtered = new java.util.HashMap<>(weatherServer());
        filtered.put("toolFilters", List.of("get_forecast"));
        run(List.of(filtered), List.of());

        assertThat(exposedFunctionNames(0)).containsExactly("weather__get_forecast");
    }

    @Test
    void withoutMcpServersNoToolsAreSentAndASingleRoundRuns() throws Exception {
        openAi.script(List.of(assistantMessage("plain answer")));

        TaskResult result = run(List.of(), List.of());

        assertThat(result.response()).isEqualTo("plain answer");
        assertThat(result.toolCallCount()).isZero();
        assertThat(openAi.requests).hasSize(1);
        assertThat(openAi.requests.get(0).has("tools")).isFalse();
    }

    @Test
    void roundLimitCapsTheToolLoop() throws Exception {
        properties.getOpenAiCompatible().setMaxToolRounds(2);
        openAi.script(List.of(
                toolCallMessage("weather__get_forecast", "{\"city\":\"Paris\"}"),
                toolCallMessage("weather__get_forecast", "{\"city\":\"Rome\"}"),
                toolCallMessage("weather__get_forecast", "{\"city\":\"Oslo\"}")));

        TaskResult result = run(List.of(weatherServer()), List.of());

        assertThat(result.toolCallCount()).isEqualTo(2);
        assertThat(openAi.requests).hasSize(2);
        assertThat(trace).anySatisfy(event -> {
            assertThat(event.kind()).isEqualTo("log");
            assertThat(event.payload()).contains("round limit");
        });
    }

    private List<String> exposedFunctionNames(int requestIndex) {
        List<String> names = new ArrayList<>();
        for (JsonNode tool : openAi.requests.get(requestIndex).path("tools")) {
            names.add(tool.path("function").path("name").asText());
        }
        return names;
    }

    // ---------------------------------------------------------------- fake endpoint

    /** Serves scripted chat-completion responses and records every request body. */
    private static final class FakeOpenAiEndpoint implements AutoCloseable {

        private final HttpServer server;
        private final List<ObjectNode> requests = new CopyOnWriteArrayList<>();
        private final List<ObjectNode> scriptedResponses = new CopyOnWriteArrayList<>();

        FakeOpenAiEndpoint() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        }

        void script(List<ObjectNode> assistantMessages) {
            scriptedResponses.addAll(assistantMessages);
        }

        private void handle(HttpExchange exchange) throws IOException {
            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                requests.add((ObjectNode) MAPPER.readTree(body));
                ObjectNode message = scriptedResponses.isEmpty()
                        ? assistantMessage("no script left") : scriptedResponses.remove(0);
                ObjectNode choice = MAPPER.createObjectNode();
                choice.put("index", 0);
                choice.set("message", message);
                choice.put("finish_reason", "stop");
                ObjectNode response = MAPPER.createObjectNode();
                response.put("id", "chatcmpl-test");
                response.putArray("choices").add(choice);
                byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            } finally {
                exchange.close();
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
