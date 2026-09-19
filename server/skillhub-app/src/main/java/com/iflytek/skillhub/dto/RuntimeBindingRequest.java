package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * Request body for the per-draft runtime binding: which agent runtime executes
 * behavior tasks, its adapter configuration, the tool allowlist, and MCP server
 * declarations. Credentials must be referenced by name, never inlined.
 */
public record RuntimeBindingRequest(
        @NotBlank(message = "{error.badRequest}") String agentType,
        Map<String, Object> config,
        List<String> toolAllowlist,
        List<Map<String, Object>> mcpServers
) {
}
