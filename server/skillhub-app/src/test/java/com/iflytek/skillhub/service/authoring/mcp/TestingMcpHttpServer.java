package com.iflytek.skillhub.service.authoring.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal MCP streamable-HTTP server for tests. Implements the initialize /
 * notifications/initialized / tools/list / tools/call exchange the real client
 * performs, can answer in plain JSON or SSE framing, enforces the session id on
 * post-initialize requests, and records every tools/call it received. The tool
 * named "boom" always answers with an error result.
 */
public class TestingMcpHttpServer implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpServer server;
    private final List<String> toolNames;
    private final boolean sseMode;

    /** Every tools/call: tool name -> argument map, in arrival order. */
    public final List<Map.Entry<String, JsonNode>> toolCalls = new CopyOnWriteArrayList<>();
    /** Whether a post-initialize request arrived without the session id header. */
    public volatile boolean sawRequestWithoutSessionId;
    /** How many initialize requests were handled. */
    public final AtomicInteger initializeCount = new AtomicInteger();

    public TestingMcpHttpServer(List<String> toolNames) throws IOException {
        this(toolNames, false);
    }

    public TestingMcpHttpServer(List<String> toolNames, boolean sseMode) throws IOException {
        this.toolNames = List.copyOf(toolNames);
        this.sseMode = sseMode;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode request = MAPPER.readTree(body);
            String method = request.path("method").asText("");
            if ("initialize".equals(method)) {
                initializeCount.incrementAndGet();
                respond(exchange, result(exchange, request, initializeResult()), "test-session-1");
            } else if ("notifications/initialized".equals(method)) {
                exchange.sendResponseHeaders(202, -1);
            } else if ("tools/list".equals(method)) {
                if (exchange.getRequestHeaders().getFirst("Mcp-Session-Id") == null) {
                    sawRequestWithoutSessionId = true;
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                respond(exchange, result(exchange, request, toolsListResult()), null);
            } else if ("tools/call".equals(method)) {
                if (exchange.getRequestHeaders().getFirst("Mcp-Session-Id") == null) {
                    sawRequestWithoutSessionId = true;
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                String tool = request.path("params").path("name").asText();
                toolCalls.add(Map.entry(tool, request.path("params").path("arguments")));
                respond(exchange, result(exchange, request, toolCallResult(tool,
                        request.path("params").path("arguments"))), null);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        } finally {
            exchange.close();
        }
    }

    private ObjectNode initializeResult() {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        result.set("capabilities", MAPPER.createObjectNode());
        result.putObject("serverInfo").put("name", "testing-mcp").put("version", "1.0");
        return result;
    }

    private ObjectNode toolsListResult() {
        ObjectNode result = MAPPER.createObjectNode();
        ArrayNode tools = result.putArray("tools");
        for (String name : toolNames) {
            ObjectNode tool = tools.addObject();
            tool.put("name", name);
            tool.put("description", "test tool " + name);
            tool.putObject("inputSchema").put("type", "object");
        }
        return result;
    }

    private ObjectNode toolCallResult(String tool, JsonNode arguments) {
        ObjectNode result = MAPPER.createObjectNode();
        if ("boom".equals(tool)) {
            result.put("isError", true);
            result.putArray("content").addObject().put("type", "text").put("text", "tool exploded");
            return result;
        }
        result.putArray("content").addObject().put("type", "text")
                .put("text", "result of " + tool + ": " + arguments);
        return result;
    }

    private ObjectNode result(HttpExchange exchange, JsonNode request, ObjectNode payload) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", request.path("id").asInt());
        response.set("result", payload);
        return response;
    }

    private void respond(HttpExchange exchange, ObjectNode response, String sessionId)
            throws IOException {
        byte[] bytes;
        String contentType;
        if (sseMode) {
            bytes = ("event: message\ndata: " + response + "\n\n").getBytes(StandardCharsets.UTF_8);
            contentType = "text/event-stream";
        } else {
            bytes = response.toString().getBytes(StandardCharsets.UTF_8);
            contentType = "application/json";
        }
        if (sessionId != null) {
            exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
        }
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
