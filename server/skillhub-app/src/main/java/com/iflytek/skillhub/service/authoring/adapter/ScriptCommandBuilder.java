package com.iflytek.skillhub.service.authoring.adapter;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Builds the process command for one behavior-task script execution. Two backends
 * ship: {@code inline} runs the script as a subprocess on the server host (local
 * development only); {@code docker} runs it inside a locked-down container (no
 * network, memory/CPU/pids caps, read-only root filesystem) for production.
 */
public interface ScriptCommandBuilder {

    /** Backend identifier matching {@code skillhub.authoring.local-script.execution-mode}. */
    String backend();

    /** Whether the backend can execute right now (for docker: daemon reachable). */
    boolean available();

    /**
     * Builds the execution plan for one task. {@code scrubbedEnvironment} carries
     * exactly the variables the script may see; backends decide how to deliver
     * them (inline: process environment; docker: {@code -e} flags, with the
     * workspace path remapped to its mount point inside the container).
     *
     * @param runTag stable per-task tag (run id + task name), used for container
     *               naming so abnormal exits can be force-removed
     */
    ScriptExecutionPlan plan(Path workspace, String interpreter, Path scriptPath,
                             List<String> args, Map<String, String> scrubbedEnvironment,
                             String runTag);

    /**
     * Best-effort cleanup after the wrapping process was killed (timeout or
     * cancel). Killing the {@code docker run} client does not stop the container,
     * so the docker backend force-removes it here.
     */
    default void cleanup(String runTag) {
    }

    /**
     * Command line plus environment. A null environment means the spawned client
     * (e.g. the docker CLI) needs the parent's environment intact; the isolation
     * then lives in the container flags instead.
     */
    record ScriptExecutionPlan(List<String> command, Map<String, String> environment) {

        public static ScriptExecutionPlan inherited(List<String> command) {
            return new ScriptExecutionPlan(command, null);
        }
    }
}
