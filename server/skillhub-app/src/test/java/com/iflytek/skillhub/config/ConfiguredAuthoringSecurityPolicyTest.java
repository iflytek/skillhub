package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.authoring.service.AuthoringSecurityPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the SSRF classification of user-supplied endpoints: link-local (cloud
 * metadata) is always blocked, private/loopback ranges follow the per-surface
 * flags, and public addresses pass.
 */
class ConfiguredAuthoringSecurityPolicyTest {

    private final AuthoringProperties properties = new AuthoringProperties();
    private final ConfiguredAuthoringSecurityPolicy policy =
            new ConfiguredAuthoringSecurityPolicy(properties);

    @Test
    void publicHttpAndHttpsEndpointsAreAllowed() {
        // literal public IPs keep the test hermetic: hostname resolution varies
        // with the local network (VPNs and DNS64 may answer with private ranges)
        assertThat(policy.endpointRejection("https://1.1.1.1/v1",
                AuthoringSecurityPolicy.EndpointUse.LLM_API)).isNull();
        assertThat(policy.endpointRejection("http://8.8.8.8/mcp",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).isNull();
    }

    @Test
    void nonHttpSchemesAndHostlessUrlsAreRejected() {
        assertThat(policy.endpointRejection("file:///etc/passwd",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).contains("http(s)");
        assertThat(policy.endpointRejection("ftp://example.com/x",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).contains("http(s)");
        assertThat(policy.endpointRejection("http://",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).isNotNull();
        assertThat(policy.endpointRejection("http:///no-host-here",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).contains("no host");
        assertThat(policy.endpointRejection("not a url at all",
                AuthoringSecurityPolicy.EndpointUse.LLM_API)).isNotNull();
    }

    @Test
    void cloudMetadataAndLinkLocalAreAlwaysBlockedEvenWhenPrivateIsAllowed() {
        properties.getMcp().setAllowPrivateEndpoints(true);
        properties.getOpenAiCompatible().setAllowPrivateEndpoints(true);
        assertThat(policy.endpointRejection("http://169.254.169.254/latest/meta-data/",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).contains("link-local");
        assertThat(policy.endpointRejection("http://169.254.0.1/mcp",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).contains("link-local");
        assertThat(policy.endpointRejection("http://[fe80::1]/mcp",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).isNotNull();
        // the LLM surface carries the server API key — same absolute block
        assertThat(policy.endpointRejection("http://169.254.169.254/v1",
                AuthoringSecurityPolicy.EndpointUse.LLM_API)).contains("link-local");
    }

    @Test
    void unspecifiedAndMulticastAreAlwaysBlocked() {
        assertThat(policy.endpointRejection("http://0.0.0.0/mcp",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).isNotNull();
        assertThat(policy.endpointRejection("http://224.0.0.1/mcp",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).isNotNull();
    }

    @Test
    void loopbackIsBlockedByDefaultAndAllowedWithTheFlag() {
        assertThat(policy.endpointRejection("http://127.0.0.1:8080/mcp",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).contains("private/loopback");
        assertThat(policy.endpointRejection("http://localhost:8080/mcp",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).isNotNull();
        assertThat(policy.endpointRejection("http://[::1]/mcp",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).isNotNull();

        properties.getMcp().setAllowPrivateEndpoints(true);
        assertThat(policy.endpointRejection("http://127.0.0.1:8080/mcp",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).isNull();
        // the LLM surface has its own flag and stays blocked
        assertThat(policy.endpointRejection("http://127.0.0.1:8080/v1",
                AuthoringSecurityPolicy.EndpointUse.LLM_API)).isNotNull();
        properties.getOpenAiCompatible().setAllowPrivateEndpoints(true);
        assertThat(policy.endpointRejection("http://127.0.0.1:8080/v1",
                AuthoringSecurityPolicy.EndpointUse.LLM_API)).isNull();
    }

    @Test
    void privateV4V6AndCgnatRangesAreBlockedByDefault() {
        assertThat(policy.endpointRejection("http://10.1.2.3/mcp",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).isNotNull();
        assertThat(policy.endpointRejection("http://172.16.0.9/mcp",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).isNotNull();
        assertThat(policy.endpointRejection("http://192.168.1.1/mcp",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).isNotNull();
        // 172.32.x.x is public (just outside the RFC1918 block)
        assertThat(policy.endpointRejection("http://172.32.0.1/mcp",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).isNull();
        assertThat(policy.endpointRejection("http://100.64.0.1/mcp",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).isNotNull();
        // 100.128.x.x is public (just outside CGNAT)
        assertThat(policy.endpointRejection("http://100.128.0.1/mcp",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).isNull();
        assertThat(policy.endpointRejection("http://[fd12::1]/mcp",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER)).isNotNull();
    }

    @Test
    void unresolvableOrSyntheticHostsNeverPassSilently() {
        // a hostname that resolves nowhere is rejected for that reason; on
        // networks whose DNS answers every query with a synthetic private
        // range (some VPNs), the address classification rejects it instead —
        // either way the endpoint must not pass silently
        String rejection = policy.endpointRejection("http://this-host-does-not-exist.invalid/mcp",
                AuthoringSecurityPolicy.EndpointUse.MCP_SERVER);
        assertThat(rejection).isNotNull();
    }

    @Test
    void stdioAndEnvRefsFollowConfiguration() {
        assertThat(policy.stdioTransportAllowed()).isFalse();
        properties.getMcp().setStdioEnabled(true);
        assertThat(policy.stdioTransportAllowed()).isTrue();

        assertThat(policy.envRefAllowed("ANY_VAR")).isFalse();
        properties.getMcp().setEnvAllowlist(java.util.List.of("WEATHER_API_KEY"));
        assertThat(policy.envRefAllowed("WEATHER_API_KEY")).isTrue();
        assertThat(policy.envRefAllowed("PATH")).isFalse();
    }
}
