package com.iflytek.skillhub.service.authoring.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.AuthoringProperties;
import com.iflytek.skillhub.domain.authoring.validation.FindingDraft;
import com.iflytek.skillhub.domain.authoring.validation.FindingSeverity;
import com.iflytek.skillhub.service.authoring.TestingAuthoringSecurityPolicy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * McpProbeService turns MCP declarations into verified facts: reachable servers
 * with their discovered tools, and findings for unreachable servers or filters
 * naming tools the server does not expose.
 */
class McpProbeServiceTest {

    private final McpProbeService probeService = new McpProbeService(new McpClientFactory(
            new ObjectMapper(), new AuthoringProperties(),
            TestingAuthoringSecurityPolicy.permissive()));

    private TestingMcpHttpServer mcpServer;

    @BeforeEach
    void startServer() throws Exception {
        mcpServer = new TestingMcpHttpServer(List.of("get_forecast", "lookup_city"));
    }

    @AfterEach
    void stopServer() {
        if (mcpServer != null) {
            mcpServer.close();
        }
    }

    private Map<String, Object> server(Map<String, Object> overrides) {
        Map<String, Object> server = new java.util.HashMap<>();
        server.put("name", "weather");
        server.put("transport", "http");
        server.put("endpoint", mcpServer.endpoint());
        server.putAll(overrides);
        return server;
    }

    @Test
    void reachableServerReportsDiscoveredToolsWithoutFindings() {
        McpProbeService.ProbeReport report = probeService.probe(
                List.of(server(Map.of())), Duration.ofSeconds(5));
        assertThat(report.servers()).hasSize(1);
        assertThat(report.servers().get(0).connected()).isTrue();
        assertThat(report.servers().get(0).tools())
                .containsExactly("get_forecast", "lookup_city");
        assertThat(report.findings()).isEmpty();
    }

    @Test
    void unreachableServerProducesConnectFailedError() {
        Map<String, Object> dead = Map.of(
                "name", "dead", "transport", "http", "endpoint", "http://127.0.0.1:9/mcp");
        McpProbeService.ProbeReport report = probeService.probe(List.of(dead), Duration.ofSeconds(5));
        assertThat(report.servers().get(0).connected()).isFalse();
        assertThat(report.findings()).hasSize(1);
        FindingDraft finding = report.findings().get(0);
        assertThat(finding.ruleCode()).isEqualTo("MCP_CONNECT_FAILED");
        assertThat(finding.severity()).isEqualTo(FindingSeverity.ERROR);
        assertThat(finding.message()).contains("dead");
    }

    @Test
    void unknownToolFilterProducesWarningButStaysConnected() {
        McpProbeService.ProbeReport report = probeService.probe(
                List.of(server(Map.of("toolFilters", List.of("get_forecast", "does_not_exist")))),
                Duration.ofSeconds(5));
        assertThat(report.servers().get(0).connected()).isTrue();
        assertThat(report.findings()).hasSize(1);
        FindingDraft finding = report.findings().get(0);
        assertThat(finding.ruleCode()).isEqualTo("MCP_TOOL_FILTER_UNKNOWN");
        assertThat(finding.severity()).isEqualTo(FindingSeverity.WARNING);
        assertThat(finding.message()).contains("does_not_exist").contains("get_forecast");
    }

    @Test
    void malformedDeclarationsAreSkippedNotProbed() {
        // name/transport problems are already reported by RuntimeBindingValidator;
        // the probe must not duplicate them or crash
        McpProbeService.ProbeReport report = probeService.probe(
                List.of(Map.of("name", "weird", "transport", "carrier-pigeon")),
                Duration.ofSeconds(5));
        assertThat(report.servers()).isEmpty();
        assertThat(report.findings()).isEmpty();
    }
}
