package com.iflytek.skillhub.service.authoring.adapter;

import com.iflytek.skillhub.config.AuthoringProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Production backend: the script runs inside a throwaway container with a hard
 * isolation profile — no network, memory/CPU/pids caps, read-only root filesystem,
 * all capabilities dropped, no-new-privileges, no log persistence. Only the
 * validation workspace is mounted (read-write) so scripts can produce artifacts;
 * every other path the container sees is immutable or ephemeral tmpfs.
 *
 * <p>Network isolation is deliberately not configurable: a validation run must
 * never be able to reach other services. The image must provide the configured
 * interpreter (the default alpine ships {@code sh}; python/node skills need a
 * matching image).
 */
@Component
public class DockerScriptCommandBuilder implements ScriptCommandBuilder {

    /** Mount point of the validation workspace inside the container. */
    public static final String CONTAINER_WORKSPACE = "/workspace";

    private static final Logger log = LoggerFactory.getLogger(DockerScriptCommandBuilder.class);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CLEANUP_TIMEOUT = Duration.ofSeconds(5);

    private final AuthoringProperties properties;
    private volatile Boolean daemonReachable;

    public DockerScriptCommandBuilder(AuthoringProperties properties) {
        this.properties = properties;
    }

    @Override
    public String backend() {
        return "docker";
    }

    @Override
    public boolean available() {
        Boolean cached = daemonReachable;
        if (cached == null) {
            cached = probeDaemon();
            daemonReachable = cached;
        }
        return cached;
    }

    /** Re-probes the daemon on the next {@link #available()} call. */
    void resetProbe() {
        daemonReachable = null;
    }

    private boolean probeDaemon() {
        try {
            Process process = new ProcessBuilder("docker", "info", "--format", "{{.ServerVersion}}")
                    .start();
            if (!process.waitFor(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException exception) {
            return false;
        }
    }

    @Override
    public ScriptExecutionPlan plan(Path workspace, String interpreter, Path scriptPath,
                                    List<String> args, Map<String, String> scrubbedEnvironment,
                                    String runTag) {
        AuthoringProperties.LocalScript.Docker docker = properties.getLocalScript().getDocker();
        String hostWorkspace = realPath(workspace);
        ensureContainerReadable(workspace);

        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("run");
        command.add("--rm");
        command.add("--name");
        command.add(containerName(runTag));
        hostUser().ifPresent(user -> {
            command.add("--user");
            command.add(user);
        });
        command.add("--network");
        command.add("none");
        command.add("--memory");
        command.add(docker.getMemory());
        command.add("--cpus");
        command.add(docker.getCpus());
        command.add("--pids-limit");
        command.add(String.valueOf(docker.getPidsLimit()));
        command.add("--read-only");
        command.add("--tmpfs");
        command.add("/tmp:rw,size=" + docker.getTmpfsSize());
        command.add("--cap-drop");
        command.add("ALL");
        command.add("--security-opt");
        command.add("no-new-privileges");
        command.add("--log-driver");
        command.add("none");
        for (Map.Entry<String, String> entry : scrubbedEnvironment.entrySet()) {
            command.add("-e");
            command.add(entry.getKey() + "=" + containerEnvValue(entry.getKey(), entry.getValue()));
        }
        command.add("-v");
        command.add(hostWorkspace + ":" + CONTAINER_WORKSPACE);
        command.add("-w");
        command.add(CONTAINER_WORKSPACE);
        command.add(docker.getImage());
        command.add(interpreter);
        command.add(containerScriptPath(workspace, scriptPath));
        command.addAll(args);
        // the docker CLI needs its own environment to reach the daemon; the script
        // only sees the -e variables above, inside the container
        return ScriptExecutionPlan.inherited(List.copyOf(command));
    }

    @Override
    public void cleanup(String runTag) {
        try {
            Process process = new ProcessBuilder("docker", "rm", "-f", containerName(runTag)).start();
            if (!process.waitFor(CLEANUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
            }
        } catch (IOException | InterruptedException exception) {
            log.warn("Could not force-remove validation container {}: {}",
                    containerName(runTag), exception.getMessage());
        }
    }


    /** Cached "uid:gid" of the host JVM user, so container-created files stay deletable. */
    private static final java.util.Optional<String> HOST_USER = resolveHostUser();

    private static java.util.Optional<String> resolveHostUser() {
        try {
            String uid = readCommandOutput("id", "-u").trim();
            String gid = readCommandOutput("id", "-g").trim();
            if (uid.matches("\\d+") && gid.matches("\\d+")) {
                return java.util.Optional.of(uid + ":" + gid);
            }
        } catch (Exception exception) {
            // non-POSIX host (e.g. Windows without id): run as the image default
        }
        return java.util.Optional.empty();
    }

    private static String readCommandOutput(String... command) throws Exception {
        Process process = new ProcessBuilder(command).start();
        String output;
        try (var reader = process.inputReader()) {
            output = reader.lines().findFirst().orElse("");
        }
        process.waitFor();
        return output;
    }

    private static java.util.Optional<String> hostUser() {
        return HOST_USER;
    }

    /**
     * The container drops every capability (including DAC_OVERRIDE), so the
     * workspace files must be world-readable (and directories traversable)
     * on the host or the in-container interpreter cannot open them. Fresh JVM
     * temp directories are owner-only on Linux, which would break script
     * execution there; macOS Docker Desktop masks this by remapping
     * ownership inside the VM.
     */
    private static void ensureContainerReadable(Path root) {
        try (var stream = Files.walk(root)) {
            stream.forEach(path -> {
                try {
                    if (Files.isDirectory(path)) {
                        // traversable and writable so the script can create
                        // artifacts inside the mounted workspace
                        path.toFile().setExecutable(true, false);
                        path.toFile().setWritable(true, false);
                    }
                    path.toFile().setReadable(true, false);
                } catch (Exception exception) {
                    // best effort: a locked file surfaces as a task failure
                    // with the interpreter's own error message
                }
            });
        } catch (IOException exception) {
            // the mount itself will fail visibly if the tree is unreadable
        }
    }

    /** Container names allow [a-zA-Z0-9][a-zA-Z0-9_.-]+. */
    static String containerName(String runTag) {
        String sanitized = runTag == null ? "unknown" : runTag.replaceAll("[^a-zA-Z0-9_.-]", "-");
        if (sanitized.isEmpty() || !Character.isLetterOrDigit(sanitized.charAt(0))) {
            sanitized = "r" + sanitized;
        }
        return "skillhub-validation-" + sanitized;
    }

    private String containerScriptPath(Path workspace, Path scriptPath) {
        return CONTAINER_WORKSPACE + "/" + workspace.relativize(scriptPath);
    }

    /** HOME points at the workspace; inside the container that is the mount point. */
    private String containerEnvValue(String key, String value) {
        return "HOME".equals(key) ? CONTAINER_WORKSPACE : value;
    }

    /** Resolves symlinks (e.g. macOS /var/folders → /private/var/folders) for bind mounts. */
    private String realPath(Path workspace) {
        try {
            return workspace.toRealPath().toString();
        } catch (IOException exception) {
            return workspace.toAbsolutePath().toString();
        }
    }
}
