package com.iflytek.skillhub.service.authoring.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.AuthoringProperties;
import com.iflytek.skillhub.domain.authoring.service.AuthoringSecurityPolicy;
import com.iflytek.skillhub.service.authoring.TestingAuthoringSecurityPolicy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Connect-time enforcement in McpClientFactory: blocked endpoints and the
 * disabled stdio transport fail before any connection or process is created,
 * envRefs are filtered through the policy, and docker execution mode wraps
 * stdio commands in the hardened container profile.
 */
class McpClientFactoryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AuthoringProperties properties = new AuthoringProperties();

    private McpClientFactory factory(AuthoringSecurityPolicy policy) {
        return new McpClientFactory(MAPPER, properties, policy);
    }

    /** Allows everything except env refs (for the envRefs filter test). */
    private final AuthoringSecurityPolicy pathOnlyEnvRefs = new AuthoringSecurityPolicy() {
        @Override
        public String endpointRejection(String url, EndpointUse use) {
            return null;
        }

        @Override
        public boolean stdioTransportAllowed() {
            return true;
        }

        @Override
        public boolean envRefAllowed(String name) {
            return "PATH".equals(name);
        }
    };

    @Test
    void httpEndpointRejectedByPolicyNeverConnects() {
        AuthoringSecurityPolicy blocking = new AuthoringSecurityPolicy() {
            @Override
            public String endpointRejection(String url, EndpointUse use) {
                return "blocked by test policy";
            }

            @Override
            public boolean stdioTransportAllowed() {
                return true;
            }

            @Override
            public boolean envRefAllowed(String name) {
                return true;
            }
        };
        assertThatThrownBy(() -> factory(blocking).create(Map.of(
                "name", "evil", "transport", "http", "endpoint", "http://169.254.169.254/mcp")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("endpoint rejected");
    }

    @Test
    void stdioIsRefusedWhenDisabled() {
        AuthoringSecurityPolicy stdioDisabled = new AuthoringSecurityPolicy() {
            @Override
            public String endpointRejection(String url, EndpointUse use) {
                return null;
            }

            @Override
            public boolean stdioTransportAllowed() {
                return false;
            }

            @Override
            public boolean envRefAllowed(String name) {
                return true;
            }
        };
        assertThatThrownBy(() -> factory(stdioDisabled).create(
                Map.of("name", "local", "transport", "stdio", "command", "sleep 30")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("stdio transport is disabled");
    }

    @Test
    void envRefsNotInAllowlistNeverReachTheSpawnedProcess() {
        // HOME exists in every environment, so dropping it is observable: only
        // the allowlisted PATH survives the filter
        Map<String, Object> server = Map.of(
                "name", "envtest", "transport", "stdio", "command", "sleep 30",
                "envRefs", List.of("PATH", "HOME"));
        Map<String, String> environment = factory(pathOnlyEnvRefs).environmentOf(server);
        assertThat(environment).containsKey("PATH");
        assertThat(environment).doesNotContainKey("HOME");
    }

    @Test
    void dockerModeWrapsStdioCommandInHardenedContainer() {
        properties.getMcp().setStdioEnabled(true);
        properties.getLocalScript().setExecutionMode(
                AuthoringProperties.ScriptExecutionMode.DOCKER);
        properties.getMcp().getDocker().setImage("alpine:3.20");

        List<String> wrapped = factory(TestingAuthoringSecurityPolicy.permissive())
                .dockerWrappedCommand("skillhub-mcp-test-abcd1234",
                        List.of("python3", "-m", "fake_server"),
                        Map.of("WEATHER_API_KEY", "secret-value"));

        assertThat(wrapped.get(0)).isEqualTo("docker");
        assertThat(wrapped).containsSubsequence("run", "--rm", "--name",
                "skillhub-mcp-test-abcd1234");
        assertThat(wrapped).containsSubsequence("--network", "none");
        assertThat(wrapped).containsSubsequence("--memory", "256m");
        assertThat(wrapped).containsSubsequence("--cpus", "1.0");
        assertThat(wrapped).containsSubsequence("--pids-limit", "128");
        assertThat(wrapped).contains("--read-only");
        assertThat(wrapped).containsSubsequence("--cap-drop", "ALL");
        assertThat(wrapped).contains("no-new-privileges");
        // environment travels as -e flags, not through the parent environment
        assertThat(wrapped).containsSubsequence("-e", "WEATHER_API_KEY=secret-value");
        // the image runs the declared command
        int image = wrapped.indexOf("alpine:3.20");
        assertThat(image).isPositive();
        assertThat(wrapped.subList(image + 1, wrapped.size()))
                .containsExactly("python3", "-m", "fake_server");
    }

    @Test
    void containerNamesAreSanitizedAndUnique() {
        String first = McpClientFactory.containerName("my server!");
        String second = McpClientFactory.containerName("my server!");
        assertThat(first).startsWith("skillhub-mcp-my-server-");
        assertThat(first).isNotEqualTo(second);
        assertThat(McpClientFactory.containerName(null)).startsWith("skillhub-mcp-unknown-");
    }
}
