package com.iflytek.skillhub.service.authoring.adapter;

import com.iflytek.skillhub.config.AuthoringProperties;
import com.iflytek.skillhub.domain.authoring.runtime.RuntimeEventSink;
import com.iflytek.skillhub.domain.authoring.runtime.RuntimeExecutionContext;
import com.iflytek.skillhub.domain.authoring.runtime.SkillRuntimeAdapter;
import com.iflytek.skillhub.domain.authoring.runtime.TaskResult;
import com.iflytek.skillhub.domain.authoring.spec.TaskType;
import com.iflytek.skillhub.domain.authoring.spec.ValidationTaskSpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Behavior-layer adapter that executes the skill's own scripts in an isolated working
 * directory with a scrubbed environment. Only whitelisted interpreters may run and only
 * environment variables named in the binding's {@code envAllowlist} pass through.
 *
 * <p>Where the process actually runs is delegated to a {@link ScriptCommandBuilder}:
 * the inline backend spawns the interpreter on the server host, the docker backend
 * wraps it in a locked-down container. Everything else (streaming, buffering, timeout,
 * cancel) is backend-independent.
 */
@Component
public class LocalScriptRuntimeAdapter implements SkillRuntimeAdapter {

    private static final int MAX_STREAM_BUFFER = 256 * 1024;
    private static final long POLL_SLICE_MS = 200;

    private final AuthoringProperties properties;
    private final Map<String, ScriptCommandBuilder> commandBuilders;

    public LocalScriptRuntimeAdapter(AuthoringProperties properties,
                                     List<ScriptCommandBuilder> commandBuilders) {
        this.properties = properties;
        this.commandBuilders = commandBuilders.stream()
                .collect(Collectors.toUnmodifiableMap(ScriptCommandBuilder::backend,
                        Function.identity()));
    }

    @Override
    public String agentType() {
        return "local-script";
    }

    @Override
    public boolean supports(TaskType taskType) {
        return taskType == TaskType.SCRIPT;
    }

    @Override
    public boolean enabled() {
        return properties.getLocalScript().isEnabled() && commandBuilder().available();
    }

    @Override
    public TaskResult execute(RuntimeExecutionContext context, ValidationTaskSpec task,
                              RuntimeEventSink sink) throws Exception {
        ScriptCommandBuilder builder = commandBuilder();
        if (!builder.available()) {
            throw new IllegalStateException(
                    "script execution backend '" + builder.backend() + "' is not available"
                            + " (is the docker daemon reachable?)");
        }
        String interpreter = resolveInterpreter(context);
        Path scriptPath = resolveInsideWorkingDirectory(context.workingDirectory(), task.script());
        String runTag = context.runId() + "-" + task.name();
        Map<String, String> scrubbedEnvironment = scrubEnvironment(context);

        ScriptCommandBuilder.ScriptExecutionPlan plan = builder.plan(
                context.workingDirectory(), interpreter, scriptPath,
                task.safeArgs(), scrubbedEnvironment, runTag);

        sink.toolCall(task.name(), interpreter + " [" + builder.backend() + "]",
                String.join(" ", task.safeArgs()));

        ProcessBuilder processBuilder = new ProcessBuilder(plan.command());
        processBuilder.directory(context.workingDirectory().toFile());
        processBuilder.redirectErrorStream(false);
        if (plan.environment() != null) {
            processBuilder.environment().clear();
            processBuilder.environment().putAll(plan.environment());
        }

        Process process = processBuilder.start();
        BoundedBuffer stdout = new BoundedBuffer(MAX_STREAM_BUFFER);
        BoundedBuffer stderr = new BoundedBuffer(MAX_STREAM_BUFFER);
        Thread stdoutReader = streamReader(process.getInputStream(), "stdout", task.name(), stdout, sink);
        Thread stderrReader = streamReader(process.getErrorStream(), "stderr", task.name(), stderr, sink);
        stdoutReader.start();
        stderrReader.start();

        Integer exitCode = await(process, task, context, builder, runTag);
        stdoutReader.join(TimeUnit.SECONDS.toMillis(5));
        stderrReader.join(TimeUnit.SECONDS.toMillis(5));

        sink.toolResult(task.name(), interpreter,
                "{\"backend\":\"" + builder.backend() + "\",\"exitCode\":" + exitCode
                        + ",\"stdoutBytes\":" + stdout.size()
                        + ",\"stderrBytes\":" + stderr.size() + "}");

        return TaskResult.ofScript(exitCode, stdout.content(), stderr.content(), 1,
                new WorkingDirectoryArtifacts(context.workingDirectory()));
    }

    private ScriptCommandBuilder commandBuilder() {
        String mode = properties.getLocalScript().getExecutionMode()
                == AuthoringProperties.ScriptExecutionMode.DOCKER ? "docker" : "inline";
        ScriptCommandBuilder builder = commandBuilders.get(mode);
        if (builder == null) {
            throw new IllegalStateException(
                    "no script command builder registered for execution mode: " + mode);
        }
        return builder;
    }

    private String resolveInterpreter(RuntimeExecutionContext context) {
        String configured = context.configString("interpreter");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return "sh";
    }

    private Map<String, String> scrubEnvironment(RuntimeExecutionContext context) {
        Map<String, String> environment = new java.util.LinkedHashMap<>();
        environment.put("PATH", System.getenv().getOrDefault("PATH", "/usr/bin:/bin"));
        environment.put("HOME", context.workingDirectory().toString());
        environment.put("LANG", "C.UTF-8");
        Object allowlist = context.safeAdapterConfig().get("envAllowlist");
        if (allowlist instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry != null) {
                    String value = System.getenv(entry.toString());
                    if (value != null) {
                        environment.put(entry.toString(), value);
                    }
                }
            }
        }
        return environment;
    }

    /**
     * Waits for process exit while honoring the task timeout and the cooperative cancel
     * signal. Returns the exit code, or {@code null} when the process had to be killed;
     * abnormal exits also trigger the builder's cleanup (the docker backend must
     * force-remove the container because killing the CLI client does not stop it).
     */
    private Integer await(Process process, ValidationTaskSpec task, RuntimeExecutionContext context,
                          ScriptCommandBuilder builder, String runTag) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofMillis(task.timeoutMs()).toNanos();
        while (true) {
            if (process.waitFor(POLL_SLICE_MS, TimeUnit.MILLISECONDS)) {
                return process.exitValue();
            }
            if (context.cancellation() != null && context.cancellation().isCancelRequested()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                builder.cleanup(runTag);
                return null;
            }
            if (System.nanoTime() > deadline) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                builder.cleanup(runTag);
                throw new IllegalStateException(
                        "task '" + task.name() + "' exceeded its timeout of " + task.timeoutMs() + " ms");
            }
        }
    }

    private Thread streamReader(java.io.InputStream stream, String streamName, String taskName,
                                BoundedBuffer buffer, RuntimeEventSink sink) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line);
                    sink.log(taskName, streamName, line);
                }
            } catch (IOException ignored) {
                // stream closed when the process dies; buffered content is kept
            }
        }, "validation-stream-" + streamName);
        thread.setDaemon(true);
        return thread;
    }

    static Path resolveInsideWorkingDirectory(Path workingDirectory, String relativePath) {
        Path resolved = workingDirectory.resolve(relativePath).normalize();
        if (!resolved.startsWith(workingDirectory.normalize())) {
            throw new IllegalArgumentException(
                    "path escapes the working directory: " + relativePath);
        }
        return resolved;
    }

    /** Cap-aware single-stream buffer: keeps the head of the output for findings. */
    private static final class BoundedBuffer {
        private final StringBuilder builder = new StringBuilder();
        private final int limit;

        BoundedBuffer(int limit) {
            this.limit = limit;
        }

        synchronized void append(String line) {
            if (builder.length() >= limit) {
                return;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line, 0, Math.min(line.length(), limit - builder.length()));
        }

        synchronized String content() {
            return builder.toString();
        }

        synchronized int size() {
            return builder.length();
        }
    }
}
