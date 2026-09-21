package com.iflytek.skillhub.service.authoring.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.AuthoringProperties;
import com.iflytek.skillhub.domain.authoring.service.AuthoringSecurityPolicy;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds {@link McpClient} instances from the runtime binding's MCP server
 * declarations. Transport "http"/"sse" yield an HTTP JSON-RPC client; "stdio"
 * spawns the declared command with only the envRefs-named variables from the
 * server process environment (the only way credentials reach an MCP server).
 *
 * <p>Every declaration is re-checked here against the
 * {@link AuthoringSecurityPolicy} even though the binding validator already
 * rejected unsafe saves: this is the connect-time enforcement point that also
 * covers bindings persisted by older deployments. In docker execution mode,
 * stdio servers run inside the same locked-down container profile as
 * validation scripts — no network, resource caps, read-only rootfs — and the
 * container is force-removed when the client closes.
 */
@Component
public class McpClientFactory {

    private final ObjectMapper objectMapper;
    private final AuthoringProperties properties;
    private final AuthoringSecurityPolicy securityPolicy;
    private final HttpClient httpClient;

    public McpClientFactory(ObjectMapper objectMapper, AuthoringProperties properties,
                            AuthoringSecurityPolicy securityPolicy) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.securityPolicy = securityPolicy;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                // redirects must never be followed: a public URL answering 302
                // towards a link-local target is the classic SSRF bypass
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** @param server declaration map: name, transport, endpoint|command, toolFilters, envRefs */
    public McpClient create(Map<String, Object> server) throws IOException {
        String name = String.valueOf(server.get("name"));
        String transport = String.valueOf(server.get("transport"));
        if ("http".equals(transport) || "sse".equals(transport)) {
            String endpoint = String.valueOf(server.get("endpoint"));
            String rejection = securityPolicy.endpointRejection(
                    endpoint, AuthoringSecurityPolicy.EndpointUse.MCP_SERVER);
            if (rejection != null) {
                throw new IOException("MCP server '" + name + "' endpoint rejected: " + rejection);
            }
            return new HttpMcpClient(name, endpoint, httpClient, objectMapper, Map.of());
        }
        if ("stdio".equals(transport)) {
            if (!securityPolicy.stdioTransportAllowed()) {
                throw new IOException("MCP server '" + name
                        + "': stdio transport is disabled (skillhub.authoring.mcp.stdio-enabled)");
            }
            List<String> command = commandOf(server);
            Map<String, String> environment = environmentOf(server);
            if (properties.getLocalScript().getExecutionMode()
                    == AuthoringProperties.ScriptExecutionMode.DOCKER) {
                String container = containerName(name);
                return new StdioMcpClient(name, dockerWrappedCommand(container, command, environment),
                        Map.of(), objectMapper, List.of("docker", "rm", "-f", container), true);
            }
            return new StdioMcpClient(name, command, environment, objectMapper);
        }
        throw new IllegalArgumentException(
                "MCP server '" + name + "' has unsupported transport: " + transport);
    }

    /**
     * The docker argv that isolates a stdio MCP server: throwaway container,
     * no network, resource caps, read-only rootfs, all capabilities dropped.
     * Environment variables travel as explicit {@code -e} flags so the docker
     * CLI can keep its own environment (needed to reach the daemon).
     */
    List<String> dockerWrappedCommand(String container, List<String> command,
                                      Map<String, String> environment) {
        AuthoringProperties.Mcp.Docker docker = properties.getMcp().getDocker();
        List<String> wrapped = new ArrayList<>();
        wrapped.add("docker");
        wrapped.add("run");
        wrapped.add("--rm");
        wrapped.add("--name");
        wrapped.add(container);
        wrapped.add("--network");
        wrapped.add("none");
        wrapped.add("--memory");
        wrapped.add(docker.getMemory());
        wrapped.add("--cpus");
        wrapped.add(docker.getCpus());
        wrapped.add("--pids-limit");
        wrapped.add(String.valueOf(docker.getPidsLimit()));
        wrapped.add("--read-only");
        wrapped.add("--tmpfs");
        wrapped.add("/tmp:rw,size=" + docker.getTmpfsSize());
        wrapped.add("--cap-drop");
        wrapped.add("ALL");
        wrapped.add("--security-opt");
        wrapped.add("no-new-privileges");
        wrapped.add("--log-driver");
        wrapped.add("none");
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            wrapped.add("-e");
            wrapped.add(entry.getKey() + "=" + entry.getValue());
        }
        wrapped.add(docker.getImage());
        wrapped.addAll(command);
        return List.copyOf(wrapped);
    }

    /** Container names allow [a-zA-Z0-9][a-zA-Z0-9_.-]+ and must be unique per client. */
    static String containerName(String serverName) {
        String sanitized = serverName == null ? "unknown" : serverName.replaceAll("[^a-zA-Z0-9_.-]", "-");
        if (sanitized.isEmpty() || !Character.isLetterOrDigit(sanitized.charAt(0))) {
            sanitized = "m" + sanitized;
        }
        return "skillhub-mcp-" + sanitized + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Splits the command on whitespace; no shell is involved. */
    private static List<String> commandOf(Map<String, Object> server) {
        String command = String.valueOf(server.get("command"));
        return List.of(command.trim().split("\\s+"));
    }

    /**
     * Only envRefs-named variables from the server environment are passed
     * through, and only when the security policy allowlists them.
     */
    Map<String, String> environmentOf(Map<String, Object> server) {
        Map<String, String> environment = new HashMap<>();
        if (server.get("envRefs") instanceof List<?> refs) {
            for (Object ref : refs) {
                String name = String.valueOf(ref);
                if (!securityPolicy.envRefAllowed(name)) {
                    continue;
                }
                String value = System.getenv(name);
                if (value != null) {
                    environment.put(name, value);
                }
            }
        }
        return environment;
    }
}
