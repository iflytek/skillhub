package com.iflytek.skillhub.service.authoring.adapter;

import com.iflytek.skillhub.config.AuthoringProperties;
import com.iflytek.skillhub.domain.authoring.runtime.RuntimeEventSink;
import com.iflytek.skillhub.domain.authoring.runtime.RuntimeExecutionContext;
import com.iflytek.skillhub.domain.authoring.service.ScriptEnvironmentPolicy;
import com.iflytek.skillhub.domain.authoring.spec.TaskType;
import com.iflytek.skillhub.domain.authoring.spec.ValidationTaskSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledOnOs({OS.LINUX, OS.MAC})
class LocalScriptEnvironmentExecutionTest {

    @TempDir
    Path workspace;

    @Test
    void realScriptReceivesSelectedSyntheticValueAndStreamsItsOutput() throws Exception {
        Files.writeString(workspace.resolve("check.sh"), """
                printf '%s|%s' "$AUTHORING_DEMO" "${AUTHORING_UNSELECTED-unset}" > executed.txt
                printf '%s\\n' "$AUTHORING_DEMO"
                """);
        var lookups = new ArrayList<String>();
        var sink = new RecordingSink();

        var result = adapter(lookups).execute(context(List.of("AUTHORING_DEMO")), task(), sink);

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("synthetic-review-value");
        assertThat(result.stderr()).isEmpty();
        assertThat(Files.readString(workspace.resolve("executed.txt")))
                .isEqualTo("synthetic-review-value|unset");
        assertThat(sink.logs).containsExactly("synthetic-review-value");
        assertThat(lookups).containsExactly("PATH", "AUTHORING_DEMO");
    }

    @Test
    void deniedSelectionDoesNotStartTheRealScriptOrLookUpValues() throws Exception {
        Files.writeString(workspace.resolve("check.sh"), "printf 'started' > executed.txt\n");
        var lookups = new ArrayList<String>();
        var sink = new RecordingSink();
        var adapter = adapter(lookups);

        assertThatThrownBy(() -> adapter.execute(
                context(List.of("AUTHORING_DEMO", "AUTHORING_DENIED")), task(), sink))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(workspace.resolve("executed.txt")).doesNotExist();
        assertThat(lookups).isEmpty();
        assertThat(sink.logs).isEmpty();
        assertThat(sink.toolCalls).isZero();
    }

    private LocalScriptRuntimeAdapter adapter(List<String> lookups) {
        var properties = new AuthoringProperties();
        properties.getLocalScript().setExecutionMode(AuthoringProperties.ScriptExecutionMode.INLINE);
        properties.getLocalScript().setEnvironmentAllowlist(List.of("AUTHORING_DEMO", "AUTHORING_UNSELECTED"));
        return new LocalScriptRuntimeAdapter(properties, List.of(new InlineScriptCommandBuilder()),
                new ScriptEnvironmentPolicy(() -> properties.getLocalScript().getEnvironmentAllowlist()),
                name -> {
                    lookups.add(name);
                    return "PATH".equals(name) ? "/usr/bin:/bin" : "synthetic-review-value";
                });
    }

    private RuntimeExecutionContext context(List<String> selection) {
        return new RuntimeExecutionContext(1L, workspace,
                Map.of("interpreter", "sh", "envAllowlist", selection), List.of(), null);
    }

    private ValidationTaskSpec task() {
        return new ValidationTaskSpec("environment-check", "", TaskType.SCRIPT,
                "check.sh", List.of(), null, 5_000, List.of());
    }

    private static final class RecordingSink implements RuntimeEventSink {
        private final List<String> logs = Collections.synchronizedList(new ArrayList<>());
        private int toolCalls;

        @Override
        public void agentMessage(String taskName, String content) { }

        @Override
        public void toolCall(String taskName, String tool, String argumentsJson) {
            toolCalls++;
        }

        @Override
        public void toolResult(String taskName, String tool, String summaryJson) { }

        @Override
        public void log(String taskName, String stream, String line) {
            logs.add(line);
        }
    }
}
