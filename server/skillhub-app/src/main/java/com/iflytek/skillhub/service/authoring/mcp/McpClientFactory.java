package com.iflytek.skillhub.service.authoring.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds {@link McpClient} instances from the runtime binding's MCP server
 * declarations. Transport "http"/"sse" yield an HTTP JSON-RPC client; "stdio"
 * spawns the declared command with only the envRefs-named variables from the
 * server process environment (the only way credentials reach an MCP server).
 */
@Component
public class McpClientFactory {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public McpClientFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** @param server declaration map: name, transport, endpoint|command, toolFilters, envRefs */
    public McpClient create(Map<String, Object> server) throws IOException {
        String name = String.valueOf(server.get("name"));
        String transport = String.valueOf(server.get("transport"));
        if ("http".equals(transport) || "sse".equals(transport)) {
            return new HttpMcpClient(name, String.valueOf(server.get("endpoint")),
                    httpClient, objectMapper, Map.of());
        }
        if ("stdio".equals(transport)) {
            return new StdioMcpClient(name, commandOf(server), environmentOf(server), objectMapper);
        }
        throw new IllegalArgumentException(
                "MCP server '" + name + "' has unsupported transport: " + transport);
    }

    /** Splits the command on whitespace; no shell is involved. */
    private static List<String> commandOf(Map<String, Object> server) {
        String command = String.valueOf(server.get("command"));
        return List.of(command.trim().split("\\s+"));
    }

    /** Only envRefs-named variables from the server environment are passed through. */
    private static Map<String, String> environmentOf(Map<String, Object> server) {
        Map<String, String> environment = new HashMap<>();
        if (server.get("envRefs") instanceof List<?> refs) {
            for (Object ref : refs) {
                String value = System.getenv(String.valueOf(ref));
                if (value != null) {
                    environment.put(String.valueOf(ref), value);
                }
            }
        }
        return environment;
    }
}
