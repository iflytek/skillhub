package com.iflytek.skillhub.service.authoring.mcp;

import com.iflytek.skillhub.domain.authoring.validation.FindingDraft;
import com.iflytek.skillhub.domain.authoring.validation.ValidationLayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * CONFIG-layer liveness probe for declared MCP servers: connects, completes the
 * initialize handshake, lists tools, and reports unreachable servers and
 * toolFilters that reference tools the server does not expose. This is what turns
 * an MCP declaration from "syntactically valid" into "verified working before
 * the behavior layer relies on it".
 */
@Component
public class McpProbeService {

    private static final Set<String> KNOWN_TRANSPORTS = Set.of("http", "sse", "stdio");

    private final McpClientFactory clientFactory;

    public McpProbeService(McpClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    /** One server's probe outcome: either the discovered tool names or the failure. */
    public record ServerProbe(String server, boolean connected, List<String> tools, String error) {
        static ServerProbe connected(String server, List<String> tools) {
            return new ServerProbe(server, true, List.copyOf(tools), null);
        }

        static ServerProbe failed(String server, String error) {
            return new ServerProbe(server, false, List.of(), error);
        }
    }

    /** All probes plus the findings the orchestrator should record. */
    public record ProbeReport(List<ServerProbe> servers, List<FindingDraft> findings) {
    }

    public ProbeReport probe(List<Map<String, Object>> mcpServers, java.time.Duration timeout) {
        List<ServerProbe> probes = new ArrayList<>();
        List<FindingDraft> findings = new ArrayList<>();
        for (Map<String, Object> server : mcpServers) {
            Object name = server.get("name");
            Object transport = server.get("transport");
            if (name == null || transport == null || !KNOWN_TRANSPORTS.contains(transport.toString())) {
                continue; // already reported by RuntimeBindingValidator
            }
            probes.add(probeOne(server, name.toString(), timeout, findings));
        }
        return new ProbeReport(List.copyOf(probes), List.copyOf(findings));
    }

    private ServerProbe probeOne(Map<String, Object> server, String name,
                                 java.time.Duration timeout, List<FindingDraft> findings) {
        try (McpClient client = clientFactory.create(server)) {
            List<McpTool> tools = client.listTools(timeout);
            List<String> toolNames = tools.stream().map(McpTool::name).toList();
            checkToolFilters(server, name, toolNames, findings);
            return ServerProbe.connected(name, toolNames);
        } catch (Exception exception) {
            String message = String.valueOf(exception.getMessage());
            findings.add(FindingDraft.error(ValidationLayer.CONFIG, "MCP_CONNECT_FAILED",
                    "MCP server '" + name + "' cannot be reached: "
                            + (message.length() > 300 ? message.substring(0, 300) + "…" : message)));
            return ServerProbe.failed(name, message);
        }
    }

    private void checkToolFilters(Map<String, Object> server, String name, List<String> toolNames,
                                  List<FindingDraft> findings) {
        if (!(server.get("toolFilters") instanceof List<?> filters) || filters.isEmpty()) {
            return;
        }
        Set<String> discovered = Set.copyOf(toolNames);
        List<String> unknown = filters.stream()
                .map(String::valueOf)
                .filter(filter -> !discovered.contains(filter))
                .collect(Collectors.toList());
        if (!unknown.isEmpty()) {
            findings.add(FindingDraft.warning(ValidationLayer.CONFIG, "MCP_TOOL_FILTER_UNKNOWN",
                    "runtime binding", "MCP server '" + name + "' toolFilters reference unknown "
                    + "tool(s): " + String.join(", ", unknown) + "; discovered tools: "
                    + (toolNames.isEmpty() ? "(none)" : String.join(", ", toolNames))));
        }
    }
}
