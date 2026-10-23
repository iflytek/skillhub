package com.iflytek.skillhub.service.authoring.adapter;

import com.iflytek.skillhub.config.AuthoringProperties;
import com.iflytek.skillhub.domain.authoring.runtime.RuntimeExecutionContext;
import com.iflytek.skillhub.domain.authoring.service.ScriptEnvironmentPolicy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class LocalScriptEnvironmentAuthorizationTest {
    @TempDir Path workspace;
    private static final String DEMO = "AUTHORING_DEMO";
    private final AuthoringProperties properties = new AuthoringProperties();
    private RuntimeExecutionContext context(Object selection) {
        return new RuntimeExecutionContext(1L, Path.of("dummy-workspace").toAbsolutePath(),
                selection == null ? Map.of() : Map.of("envAllowlist", selection), List.of(), null);
    }
    @Test void onlySelectedDefinedDummyValuesAreResolved() {
        properties.getLocalScript().setEnvironmentAllowlist(List.of(DEMO, "AUTHORING_UNDEFINED", "AUTHORING_OTHER"));
        var calls = new ArrayList<String>();
        var adapter = adapter(calls);
        var result = adapter.scrubEnvironment(context(List.of(DEMO, "AUTHORING_UNDEFINED")));
        assertThat(result).containsEntry(DEMO, "dummy-value").containsEntry("PATH", "/dummy/bin")
                .containsEntry("LANG", "C.UTF-8").containsEntry("HOME", context(null).workingDirectory().toString())
                .doesNotContainKeys("AUTHORING_UNDEFINED", "AUTHORING_OTHER");
        assertThat(calls).containsExactly("PATH", DEMO, "AUTHORING_UNDEFINED");
    }
    @Test void emptyBindingGetsOnlyTrustedStartupEnvironment() {
        var calls = new ArrayList<String>();
        var result = adapter(calls).scrubEnvironment(context(null));
        assertThat(result.keySet()).containsExactly("PATH", "HOME", "LANG");
        assertThat(calls).containsExactly("PATH");
    }
    @Test void unauthorizedMixedSelectionIsRejectedBeforeAnyLookup() {
        properties.getLocalScript().setEnvironmentAllowlist(List.of(DEMO));
        var calls = new ArrayList<String>();
        var adapter = adapter(calls);
        assertThatThrownBy(() -> adapter.scrubEnvironment(context(List.of(DEMO, "AUTHORING_DENIED"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("dummy-value");
        assertThat(calls).isEmpty();
    }
    @Test void savedBindingIsRejectedAfterEffectiveRevocation() {
        properties.getLocalScript().setEnvironmentAllowlist(List.of(DEMO));
        var calls = new ArrayList<String>();
        var adapter = adapter(calls);
        var saved = context(List.of(DEMO));
        assertThat(adapter.scrubEnvironment(saved)).containsKey(DEMO);
        properties.getLocalScript().setEnvironmentAllowlist(List.of());
        calls.clear();
        assertThatThrownBy(() -> adapter.scrubEnvironment(saved)).isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).isEmpty();
    }
    @Test void reservedAliasesNeverReachLookup() {
        var calls = new ArrayList<String>();
        var adapter = adapter(calls);
        for (String name : List.of("PATH", "Path", "path", "HOME", "LANG", "SYSTEMROOT", "SystemRoot")) {
            assertThatThrownBy(() -> adapter.scrubEnvironment(context(List.of(name))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(calls).isEmpty();
    }
    @Test void bothBackendModesRejectBeforeAvailabilityProbeOrPlanning() {
        for (var mode : AuthoringProperties.ScriptExecutionMode.values()) {
            properties.getLocalScript().setExecutionMode(mode);
            String backend = mode == AuthoringProperties.ScriptExecutionMode.DOCKER ? "docker" : "inline";
            ScriptCommandBuilder builder = new ScriptCommandBuilder() {
                public String backend() { return backend; }
                public boolean available() { throw new AssertionError("must not probe backend"); }
                public ScriptExecutionPlan plan(Path w, String i, Path s, List<String> a, Map<String,String> e, String t) {
                    throw new AssertionError("must not plan backend");
                }
            };
            var adapter = new LocalScriptRuntimeAdapter(properties, List.of(builder),
                    new ScriptEnvironmentPolicy(() -> properties.getLocalScript().getEnvironmentAllowlist()),
                    name -> { throw new AssertionError("must not look up environment"); });
            assertThatThrownBy(() -> adapter.execute(context(List.of(DEMO)), null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void inlinePlanReceivesOnlyAuthorizedMap() {
        properties.getLocalScript().setEnvironmentAllowlist(List.of(DEMO));
        var ctx = context(List.of(DEMO));
        var environment = adapter(new ArrayList<>()).scrubEnvironment(ctx);
        var plan = new InlineScriptCommandBuilder().plan(ctx.workingDirectory(), "sh",
                ctx.workingDirectory().resolve("check.sh"), List.of(), environment, "dummy");
        assertThat(plan.environment()).containsEntry(DEMO, "dummy-value").hasSize(4);
        assertThat(plan.command()).hasSize(2);
    }
    @Test void dockerPlanForwardsOnlyAuthorizedMapWithoutStartingContainer() {
        properties.getLocalScript().setEnvironmentAllowlist(List.of(DEMO));
        var ctx = context(List.of(DEMO));
        var environment = adapter(new ArrayList<>()).scrubEnvironment(ctx);
        var plan = new DockerScriptCommandBuilder(properties).plan(workspace, "sh",
                workspace.resolve("check.sh"), List.of(), environment, "dummy");
        assertThat(plan.environment()).isNull(); // CLI control environment remains inherited.
        assertThat(plan.command()).contains(DEMO + "=dummy-value", "HOME=/workspace", "--read-only");
        assertThat(plan.command()).doesNotContain("AUTHORING_UNDEFINED=", "AUTHORING_DENIED=");
        assertThat(plan.command().stream().filter("-e"::equals).count()).isEqualTo(4);
    }
    private LocalScriptRuntimeAdapter adapter(List<String> calls) {
        return new LocalScriptRuntimeAdapter(properties, List.of(),
                new ScriptEnvironmentPolicy(() -> properties.getLocalScript().getEnvironmentAllowlist()),
                name -> {
                    calls.add(name);
                    return switch (name) { case "PATH" -> "/dummy/bin"; case DEMO -> "dummy-value"; default -> null; };
                });
    }
}
