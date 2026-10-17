package com.iflytek.skillhub.service.authoring.adapter;

import com.iflytek.skillhub.config.AuthoringProperties;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the docker command line: every isolation flag must be present, the
 * workspace mounted exactly once, and the script referenced by its
 * container-relative path.
 */
class DockerScriptCommandBuilderTest {

    private final AuthoringProperties properties = new AuthoringProperties();
    private final DockerScriptCommandBuilder builder = new DockerScriptCommandBuilder(properties);

    private final Path workspace = Path.of("/tmp/skillhub-authoring/run-42");
    private final Path script = workspace.resolve("scripts/greet.sh");

    private ScriptCommandBuilder.ScriptExecutionPlan plan() {
        return builder.plan(workspace, "sh", script, List.of("arg1", "arg two"),
                Map.of("PATH", "/usr/bin:/bin", "HOME", workspace.toString(), "LANG", "C.UTF-8"),
                "42-greet");
    }

    @Test
    void commandEnforcesIsolationProfile() {
        List<String> command = plan().command();
        assertThat(command).containsSubsequence(
                List.of("docker", "run", "--rm", "--name", "skillhub-validation-42-greet"));
        assertThat(command).containsSubsequence(List.of("--network", "none"));
        assertThat(command).containsSubsequence(List.of("--memory", "256m"));
        assertThat(command).containsSubsequence(List.of("--cpus", "1.0"));
        assertThat(command).containsSubsequence(List.of("--pids-limit", "128"));
        assertThat(command).contains("--read-only");
        assertThat(command).containsSubsequence(List.of("--tmpfs", "/tmp:rw,size=64m"));
        assertThat(command).containsSubsequence(List.of("--cap-drop", "ALL"));
        assertThat(command).containsSubsequence(List.of("--security-opt", "no-new-privileges"));
        assertThat(command).containsSubsequence(List.of("--log-driver", "none"));
    }

    @Test
    void workspaceIsMountedAndScriptUsesContainerPath() {
        List<String> command = plan().command();
        assertThat(command).containsSubsequence(
                List.of("-v", workspace + ":" + DockerScriptCommandBuilder.CONTAINER_WORKSPACE));
        assertThat(command).containsSubsequence(List.of("-w", DockerScriptCommandBuilder.CONTAINER_WORKSPACE));
        assertThat(command).contains("/workspace/scripts/greet.sh");
        assertThat(command).endsWith("alpine:3.20", "sh", "/workspace/scripts/greet.sh", "arg1", "arg two");
    }

    @Test
    void environmentIsPassedViaFlagsWithHomeRemapped() {
        List<String> command = plan().command();
        assertThat(command).contains("PATH=/usr/bin:/bin");
        assertThat(command).contains("HOME=" + DockerScriptCommandBuilder.CONTAINER_WORKSPACE);
        assertThat(command).contains("LANG=C.UTF-8");
        // the docker CLI keeps its own environment to reach the daemon
        assertThat(plan().environment()).isNull();
    }

    @Test
    void containerNamesAreDockerSafe() {
        assertThat(DockerScriptCommandBuilder.containerName("42-greet")).isEqualTo("skillhub-validation-42-greet");
        assertThat(DockerScriptCommandBuilder.containerName("7-task/with spaces"))
                .isEqualTo("skillhub-validation-7-task-with-spaces");
        assertThat(DockerScriptCommandBuilder.containerName("task"))
                .isEqualTo("skillhub-validation-task");
    }

    @Test
    void dockerLimitsComeFromConfiguration() {
        properties.getLocalScript().getDocker().setImage("python:3.12-alpine");
        properties.getLocalScript().getDocker().setMemory("512m");
        properties.getLocalScript().getDocker().setCpus("2.0");
        List<String> command = plan().command();
        assertThat(command).containsSubsequence(List.of("--memory", "512m"));
        assertThat(command).containsSubsequence(List.of("--cpus", "2.0"));
        assertThat(command).contains("python:3.12-alpine");
    }
}
