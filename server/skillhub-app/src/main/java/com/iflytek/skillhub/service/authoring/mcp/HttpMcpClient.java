package com.iflytek.skillhub.service.authoring.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP client for the streamable-HTTP transport (also used for legacy SSE
 * endpoints that accept plain JSON-RPC POSTs). Each request is one JSON-RPC
 * POST; responses may arrive as a bare JSON object or as a text/event-stream
 * body whose data lines carry the JSON-RPC message.
 */
public class HttpMcpClient implements McpClient {

    public static final String PROTOCOL_VERSION = "2024-11-05";
    public static final String CLIENT_NAME = "skillhub-authoring";

    private final String serverName;
    private final String endpoint;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, String> extraHeaders;
    private int nextId = 1;
    private String sessionId;

    public HttpMcpClient(String serverName, String endpoint, HttpClient httpClient,
                         ObjectMapper objectMapper, Map<String, String> extraHeaders) {
        this.serverName = serverName;
        this.endpoint = endpoint;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.extraHeaders = Map.copyOf(extraHeaders);
    }

    @Override
    public String serverName() {
        return serverName;
    }

    @Override
    public List<McpTool> listTools(Duration timeout) throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.set("capabilities", objectMapper.createObjectNode());
        ObjectNode clientInfo = params.putObject("clientInfo");
        clientInfo.put("name", CLIENT_NAME);
        clientInfo.put("version", "1.0");
        // the server may assign a session id here; request() captures it for later calls
        request("initialize", params, timeout);
        // notify the server the handshake is complete; the response is irrelevant
        notification("notifications/initialized", timeout);

        JsonNode tools = request("tools/list", objectMapper.createObjectNode(), timeout);
        List<McpTool> discovered = new ArrayList<>();
        for (JsonNode tool : tools.path("tools")) {
            discovered.add(new McpTool(
                    tool.path("name").asText(""),
                    tool.path("description").asText(""),
                    tool.path("inputSchema")));
        }
        return discovered;
    }

    @Override
    public String callTool(String toolName, Map<String, Object> arguments, Duration timeout)
            throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", objectMapper.valueToTree(arguments == null ? Map.of() : arguments));
        JsonNode result = request("tools/call", params, timeout);
        if (result.path("isError").asBoolean(false)) {
            throw new IOException("MCP tool '" + toolName + "' returned an error result on server '"
                    + serverName + "'");
        }
        StringBuilder content = new StringBuilder();
        for (JsonNode part : result.path("content")) {
            if ("text".equals(part.path("type").asText())) {
                if (content.length() > 0) {
                    content.append('\n');
                }
                content.append(part.path("text").asText(""));
            }
        }
        return content.toString();
    }

    @Override
    public void close() {
        // HTTP transport is stateless from this client's point of view
    }

    private JsonNode request(String method, ObjectNode params, Duration timeout) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", nextId++);
        request.put("method", method);
        request.set("params", params);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(request.toString(), StandardCharsets.UTF_8));
        extraHeaders.forEach(builder::header);
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new IOException("MCP server '" + serverName + "' request failed ("
                    + method + "): " + exception, exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("MCP server '" + serverName + "' returned HTTP "
                    + response.statusCode() + " for " + method);
        }
        String sessionHeader = response.headers().firstValue("Mcp-Session-Id").orElse(null);
        if (sessionHeader != null) {
            sessionId = sessionHeader;
        }
        JsonNode message = parseMessage(response.body(), request.path("id").asInt());
        if (message.has("error")) {
            throw new IOException("MCP server '" + serverName + "' error on " + method + ": "
                    + message.path("error").path("message").asText());
        }
        return message.path("result");
    }

    private void notification(String method, Duration timeout) {
        try {
            ObjectNode notification = objectMapper.createObjectNode();
            notification.put("jsonrpc", "2.0");
            notification.put("method", method);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(notification.toString(),
                            StandardCharsets.UTF_8));
            extraHeaders.forEach(builder::header);
            if (sessionId != null) {
                builder.header("Mcp-Session-Id", sessionId);
            }
            httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // notifications are best-effort; servers that reject them still work
        }
    }

    /** Accepts either a bare JSON-RPC response or an SSE body with data lines. */
    private JsonNode parseMessage(String body, int requestId) throws IOException {
        if (body == null || body.isBlank()) {
            throw new IOException("MCP server '" + serverName + "' returned an empty body");
        }
        String text = body.trim();
        if (text.startsWith("{")) {
            return objectMapper.readTree(text);
        }
        for (String line : body.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) {
                continue;
            }
            String payload = trimmed.substring("data:".length()).trim();
            if (payload.isEmpty()) {
                continue;
            }
            JsonNode node = objectMapper.readTree(payload);
            if (node.path("id").asInt(-1) == requestId) {
                return node;
            }
        }
        throw new IOException("MCP server '" + serverName
                + "' response did not contain a result for request " + requestId);
    }

    /** Helper for tests and callers that need the OpenAI-style tool schema. */
    public static ObjectNode toFunctionSchema(ObjectMapper objectMapper, McpTool tool) {
        return toFunctionSchema(objectMapper, tool, tool.name());
    }

    /** Same, under the function name the caller exposes to the model. */
    public static ObjectNode toFunctionSchema(ObjectMapper objectMapper, McpTool tool,
                                              String functionName) {
        ObjectNode function = objectMapper.createObjectNode();
        function.put("name", functionName);
        function.put("description", tool.description() == null ? "" : tool.description());
        function.set("parameters", tool.inputSchema() == null || tool.inputSchema().isNull()
                ? objectMapper.createObjectNode()
                : tool.inputSchema());
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("type", "function");
        wrapper.set("function", function);
        return wrapper;
    }

    /** Helper: builds the messages-array tool role entry for chat completions. */
    public static ObjectNode toolResponseMessage(ObjectMapper objectMapper, String toolCallId,
                                                 String content) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", "tool");
        message.put("tool_call_id", toolCallId);
        message.put("content", content);
        return message;
    }

    /** Helper: attaches tool call requests to an assistant message. */
    public static void attachToolCalls(ObjectMapper objectMapper, ObjectNode assistantMessage,
                                       JsonNode toolCalls) {
        ArrayNode calls = assistantMessage.putArray("tool_calls");
        for (JsonNode call : toolCalls) {
            calls.add(call.deepCopy());
        }
    }
}
