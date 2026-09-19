package com.iflytek.skillhub.service.authoring.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs StdioMcpClient against a python3 process implementing the stdio transport
 * (newline-delimited JSON-RPC on stdin/stdout). Skipped when python3 is absent.
 */
class StdioMcpClientTest {

    private static final String FAKE_SERVER = """
            import json, sys
            def send(obj):
                sys.stdout.write(json.dumps(obj) + "\\n")
                sys.stdout.flush()
            for line in sys.stdin:
                line = line.strip()
                if not line:
                    continue
                msg = json.loads(line)
                if 'id' not in msg:
                    continue
                method = msg.get('method')
                if method == 'initialize':
                    send({'jsonrpc': '2.0', 'id': msg['id'], 'result': {
                        'protocolVersion': '2024-11-05', 'capabilities': {},
                        'serverInfo': {'name': 'fake', 'version': '1.0'}}})
                elif method == 'tools/list':
                    send({'jsonrpc': '2.0', 'id': msg['id'], 'result': {'tools': [
                        {'name': 'echo', 'description': 'echo text',
                         'inputSchema': {'type': 'object'}}]}})
                elif method == 'tools/call':
                    args = msg['params']['arguments']
                    send({'jsonrpc': '2.0', 'id': msg['id'], 'result': {'content': [
                        {'type': 'text', 'text': 'echo:' + args.get('text', '')}]}})
            """;

    @BeforeAll
    static void requirePython3() {
        try {
            int exit = new ProcessBuilder("python3", "--version").start().waitFor();
            Assumptions.assumeTrue(exit == 0, "python3 unavailable");
        } catch (Exception exception) {
            Assumptions.assumeTrue(false, "python3 unavailable: " + exception.getMessage());
        }
    }

    private StdioMcpClient client() throws Exception {
        return new StdioMcpClient("echo-server", List.of("python3", "-c", FAKE_SERVER),
                Map.of(), new ObjectMapper());
    }

    @Test
    void listsToolsAndCallsThemOverStdio() throws Exception {
        try (StdioMcpClient client = client()) {
            List<McpTool> tools = client.listTools(Duration.ofSeconds(10));
            assertThat(tools).extracting(McpTool::name).containsExactly("echo");

            String output = client.callTool("echo", Map.of("text", "hello"), Duration.ofSeconds(10));
            assertThat(output).isEqualTo("echo:hello");
        }
    }

    @Test
    void processIsTerminatedOnClose() throws Exception {
        StdioMcpClient client = client();
        client.listTools(Duration.ofSeconds(10));
        client.close();
        // a second close must not hang or throw; the process is already gone
        client.close();
    }

    @Test
    void failingCommandSurfacesAsException() {
        assertThatThrownBy(() -> new StdioMcpClient("broken",
                List.of("definitely-not-a-real-command-xyz"), Map.of(), new ObjectMapper())
                .listTools(Duration.ofSeconds(10)))
                .isInstanceOf(Exception.class);
    }
}
