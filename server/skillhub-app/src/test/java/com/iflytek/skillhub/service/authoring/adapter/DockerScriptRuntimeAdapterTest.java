package com.iflytek.skillhub.service.authoring.adapter;

import com.iflytek.skillhub.config.AuthoringProperties;
import com.iflytek.skillhub.domain.authoring.runtime.RuntimeEventSink;
import com.iflytek.skillhub.domain.authoring.runtime.RuntimeExecutionContext;
import com.iflytek.skillhub.domain.authoring.runtime.TaskResult;
import com.iflytek.skillhub.domain.authoring.spec.ValidationTaskSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes real behavior tasks through the docker backend and proves the
 * isolation profile inside the container: no network interfaces beyond loopback,
 * read-only root filesystem, and the workspace mounted writable. Skipped
 * silently when no docker daemon is reachable.
 */
class DockerScriptRuntimeAdapterTest {

    @TempDir
    Path workspace;

    private final AuthoringProperties properties = new AuthoringProperties();

    @BeforeAll
    static void requireDockerWithImage() {
        DockerScriptCommandBuilder probe = new DockerScriptCommandBuilder(new AuthoringProperties());
        Assumptions.assumeTrue(probe.available(), "docker daemon not reachable");
        try {
            Process pull = new ProcessBuilder("docker", "image", "inspect", "alpine:3.20").start();
            if (pull.waitFor() != 0) {
                new ProcessBuilder("docker", "pull", "alpine:3.20").start().waitFor();
            }
        } catch (Exception exception) {
            Assumptions.assumeTrue(false, "alpine image unavailable: " + exception.getMessage());
        }
    }

    private LocalScriptRuntimeAdapter dockerAdapter() {
        properties.getLocalScript().setExecutionMode(AuthoringProperties.ScriptExecutionMode.DOCKER);
        return new LocalScriptRuntimeAdapter(properties, List.of(
                new InlineScriptCommandBuilder(), new DockerScriptCommandBuilder(properties)));
    }

    private record RecordingSink(List<String> logLines) implements RuntimeEventSink {
        RecordingSink() {
            this(new ArrayList<>());
        }

        @Override
        public void agentMessage(String taskName, String content) {
        }

        @Override
        public void toolCall(String taskName, String tool, String argumentsJson) {
        }

        @Override
        public void toolResult(String taskName, String tool, String summaryJson) {
        }

        @Override
        public void log(String taskName, String stream, String line) {
            logLines.add(line);
        }
    }

    private TaskResult runScript(String script) throws Exception {
        Files.createDirectories(workspace.resolve("scripts"));
        Files.writeString(workspace.resolve("scripts/task.sh"), script);
        ValidationTaskSpec task = new ValidationTaskSpec("probe", "isolation probe",
                com.iflytek.skillhub.domain.authoring.spec.TaskType.SCRIPT,
                "scripts/task.sh", List.of(), null, 60_000, List.of());
        RuntimeExecutionContext context = new RuntimeExecutionContext(
                99L, workspace, Map.of("interpreter", "sh"), List.of(), () -> false);
        return dockerAdapter().execute(context, task, new RecordingSink());
    }

    @Test
    void runsScriptAndCapturesOutput() throws Exception {
        TaskResult result = runScript("echo hello from docker\n");
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("hello from docker");
    }

    @Test
    void containerHasNoNetworkRoutes() throws Exception {
        // with --network none the routing table is empty (header line only),
        // so nothing beyond loopback is reachable
        TaskResult result = runScript("tail -n +2 /proc/net/route | wc -l | tr -d ' '\n");
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout().trim()).isEqualTo("0");
    }

    @Test
    void rootFilesystemIsReadOnly() throws Exception {
        TaskResult result = runScript(
                "if touch /etc/forbidden 2>/dev/null; then echo writable; else echo readonly; fi\n");
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("readonly").doesNotContain("writable");
    }

    @Test
    void workspaceIsWritableForArtifacts() throws Exception {
        TaskResult result = runScript("mkdir -p artifacts && echo data > artifacts/out.txt && cat artifacts/out.txt\n");
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("data");
        assertThat(result.artifacts().read("artifacts/out.txt")).isPresent();
        assertThat(new String(result.artifacts().read("artifacts/out.txt").orElseThrow()))
                .contains("data");
    }
}
