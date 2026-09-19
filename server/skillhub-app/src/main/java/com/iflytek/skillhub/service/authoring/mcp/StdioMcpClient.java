package com.iflytek.skillhub.service.authoring.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MCP client for the stdio transport: the declared command is spawned as a
 * subprocess and JSON-RPC messages are exchanged as newline-delimited JSON on
 * its stdin/stdout. Only environment variables named in {@code envRefs} are
 * passed through; the process is destroyed on close.
 */
public class StdioMcpClient implements McpClient {

    private final String serverName;
    private final Process process;
    private final BufferedWriter stdin;
    private final BufferedReader stdout;
    private final ObjectMapper objectMapper;
    private int nextId = 1;

    public StdioMcpClient(String serverName, List<String> command, Map<String, String> environment,
                          ObjectMapper objectMapper) throws IOException {
        this.serverName = serverName;
        this.objectMapper = objectMapper;
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().clear();
        processBuilder.environment().putAll(environment);
        processBuilder.redirectErrorStream(false);
        this.process = processBuilder.start();
        this.stdin = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.stdout = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public String serverName() {
        return serverName;
    }

    @Override
    public List<McpTool> listTools(Duration timeout) throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", HttpMcpClient.PROTOCOL_VERSION);
        params.set("capabilities", objectMapper.createObjectNode());
        ObjectNode clientInfo = params.putObject("clientInfo");
        clientInfo.put("name", HttpMcpClient.CLIENT_NAME);
        clientInfo.put("version", "1.0");
        request("initialize", params, timeout);
        notification("notifications/initialized");

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
        process.destroy();
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException ignored) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private JsonNode request(String method, ObjectNode params, Duration timeout) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", nextId++);
        request.put("method", method);
        request.set("params", params);
        stdin.write(request.toString());
        stdin.write('\n');
        stdin.flush();
        return readResponse(request.path("id").asInt(), timeout);
    }

    private void notification(String method) throws IOException {
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        stdin.write(notification.toString());
        stdin.write('\n');
        stdin.flush();
    }

    /** Reads stdout lines until the response with the matching id arrives. */
    private JsonNode readResponse(int requestId, Duration timeout) throws IOException {
        // readLine() blocks, so a silently hanging server needs a watchdog: kill the
        // process after the timeout and readLine unblocks with end-of-stream.
        Thread watchdog = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(timeout.toMillis());
                process.destroyForcibly();
            } catch (InterruptedException ignored) {
                // the response arrived in time
            }
        });
        try {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                String line = stdout.readLine();
                if (line == null) {
                    throw new IOException("MCP server '" + serverName + "' (stdio) closed its output");
                }
                String trimmed = line.trim();
                if (!trimmed.startsWith("{")) {
                    continue;
                }
                JsonNode node = objectMapper.readTree(trimmed);
                if (node.path("id").asInt(-1) != requestId) {
                    continue; // notification or log output from the server
                }
                if (node.has("error")) {
                    throw new IOException("MCP server '" + serverName + "' error: "
                            + node.path("error").path("message").asText());
                }
                return node.path("result");
            }
            throw new IOException("MCP server '" + serverName + "' (stdio) timed out after " + timeout);
        } finally {
            watchdog.interrupt();
        }
    }
}
