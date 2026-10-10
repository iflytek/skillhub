package com.iflytek.skillhub.service.authoring.mcp;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One tool discovered from an MCP server via {@code tools/list}.
 */
public record McpTool(String name, String description, JsonNode inputSchema) {

    @Override
    public String toString() {
        return name;
    }
}
