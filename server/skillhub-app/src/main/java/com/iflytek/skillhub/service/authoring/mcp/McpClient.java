package com.iflytek.skillhub.service.authoring.mcp;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * A live connection to one MCP server declared in a runtime binding. Implementations
 * speak the MCP JSON-RPC protocol (initialize, tools/list, tools/call) over their
 * transport and are closed after use.
 */
public interface McpClient extends AutoCloseable {

    /** Display name from the binding declaration, used in events and findings. */
    String serverName();

    /** Performs the initialize handshake and a tools/list; returns the offered tools. */
    List<McpTool> listTools(Duration timeout) throws Exception;

    /**
     * Executes one tool call and returns its text content. Implementations must
     * surface MCP-level errors (isError or JSON-RPC error) as exceptions.
     */
    String callTool(String toolName, Map<String, Object> arguments, Duration timeout)
            throws Exception;

    @Override
    void close();
}
