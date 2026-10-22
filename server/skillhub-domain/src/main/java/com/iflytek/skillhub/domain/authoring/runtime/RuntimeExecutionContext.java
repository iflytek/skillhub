package com.iflytek.skillhub.domain.authoring.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Everything a runtime adapter needs to execute one validation run: the isolated
 * working directory containing the draft files, the adapter-specific configuration,
 * the tool allowlist, the declared MCP servers, and the cancellation signal.
 *
 * @param runId            validation run identifier, for logging
 * @param workingDirectory isolated directory holding the draft's files (read-write)
 * @param adapterConfig    adapter-specific configuration map from the runtime binding
 * @param toolAllowlist    allowed tool identifiers; empty means unrestricted
 * @param mcpServers       MCP server declarations from the runtime binding (may be empty)
 * @param cancellation     cooperative cancel signal
 */
public record RuntimeExecutionContext(
        Long runId,
        Path workingDirectory,
        Map<String, Object> adapterConfig,
        List<String> toolAllowlist,
        List<Map<String, Object>> mcpServers,
        TaskCancellation cancellation
) {

    /** Convenience constructor for runtimes that do not consume MCP servers. */
    public RuntimeExecutionContext(Long runId, Path workingDirectory, Map<String, Object> adapterConfig,
                                   List<String> toolAllowlist, TaskCancellation cancellation) {
        this(runId, workingDirectory, adapterConfig, toolAllowlist, List.of(), cancellation);
    }

    public Map<String, Object> safeAdapterConfig() {
        return adapterConfig == null ? Map.of() : adapterConfig;
    }

    public List<String> safeToolAllowlist() {
        return toolAllowlist == null ? List.of() : List.copyOf(toolAllowlist);
    }

    public List<Map<String, Object>> safeMcpServers() {
        return mcpServers == null ? List.of() : List.copyOf(mcpServers);
    }

    public String configString(String key) {
        Object value = safeAdapterConfig().get(key);
        return value == null ? null : value.toString();
    }
}
