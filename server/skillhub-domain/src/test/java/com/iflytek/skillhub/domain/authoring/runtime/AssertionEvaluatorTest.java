package com.iflytek.skillhub.domain.authoring.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.authoring.spec.AssertionSpec;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AssertionEvaluatorTest {

    private final AssertionEvaluator evaluator = new AssertionEvaluator();

    private static TaskResult scriptResult(int exitCode, String stdout, String stderr) {
        return TaskResult.ofScript(exitCode, stdout, stderr, 1, path -> Optional.empty());
    }

    @Test
    void exitCodeAssertionPassesAndFails() {
        AssertionSpec assertion = new AssertionSpec("exit_code", Map.of("equals", 0));

        assertThat(evaluator.evaluate(List.of(assertion), scriptResult(0, "", ""))).isEmpty();
        assertThat(evaluator.evaluate(List.of(assertion), scriptResult(1, "", "")))
                .singleElement()
                .satisfies(failure -> assertThat(failure.describe()).contains("exit code"));
    }

    @Test
    void stdoutContainsChecksValue() {
        AssertionSpec assertion = new AssertionSpec("stdout_contains", Map.of("value", "OK"));

        assertThat(evaluator.evaluate(List.of(assertion), scriptResult(0, "everything OK here", "")))
                .isEmpty();
        assertThat(evaluator.evaluate(List.of(assertion), scriptResult(0, "failed", "")))
                .hasSize(1);
    }

    @Test
    void stdoutMatchesUsesRegexFindSemantics() {
        AssertionSpec assertion =
                new AssertionSpec("stdout_matches", Map.of("pattern", "duration: \\d+ms"));

        assertThat(evaluator.evaluate(List.of(assertion), scriptResult(0, "run finished, duration: 42ms", "")))
                .isEmpty();
        assertThat(evaluator.evaluate(List.of(assertion), scriptResult(0, "no timing info", "")))
                .hasSize(1);
    }

    @Test
    void jsonPointerAssertionComparesValue() {
        AssertionSpec assertion = new AssertionSpec("stdout_json",
                Map.of("pointer", "/status", "equals", "healthy"));

        assertThat(evaluator.evaluate(List.of(assertion),
                scriptResult(0, "{\"status\":\"healthy\"}", ""))).isEmpty();
        assertThat(evaluator.evaluate(List.of(assertion),
                scriptResult(0, "{\"status\":\"unhealthy\"}", ""))).hasSize(1);
        assertThat(evaluator.evaluate(List.of(assertion), scriptResult(0, "not json", "")))
                .singleElement()
                .satisfies(failure -> assertThat(failure.describe()).contains("not valid JSON"));
    }

    @Test
    void artifactExistsChecksContentHash() {
        byte[] content = "artifact-bytes".getBytes();
        String sha = TaskResult.sha256Hex(content);
        TaskResult result = TaskResult.ofScript(0, "", "", 0, path ->
                "out/report.txt".equals(path) ? Optional.of(content) : Optional.empty());

        AssertionSpec exists = new AssertionSpec("artifact_exists", Map.of("path", "out/report.txt"));
        AssertionSpec missing = new AssertionSpec("artifact_exists", Map.of("path", "out/none.txt"));
        AssertionSpec wrongHash = new AssertionSpec("artifact_exists",
                Map.of("path", "out/report.txt", "sha256", "deadbeef"));

        assertThat(evaluator.evaluate(List.of(exists), result)).isEmpty();
        assertThat(evaluator.evaluate(List.of(missing), result)).hasSize(1);
        assertThat(evaluator.evaluate(List.of(wrongHash), result))
                .singleElement()
                .satisfies(failure -> assertThat(failure.describe()).contains("sha256"));
    }

    @Test
    void toolCallCountHonorsMinAndMax() {
        TaskResult result = TaskResult.ofScript(0, "", "", 2, path -> Optional.empty());

        assertThat(evaluator.evaluate(
                List.of(new AssertionSpec("tool_call_count", Map.of("min", 1, "max", 3))), result))
                .isEmpty();
        assertThat(evaluator.evaluate(
                List.of(new AssertionSpec("tool_call_count", Map.of("min", 3))), result))
                .hasSize(1);
        assertThat(evaluator.evaluate(
                List.of(new AssertionSpec("tool_call_count", Map.of("max", 1))), result))
                .hasSize(1);
    }

    @Test
    void responseAssertionsApplyToPromptResults() {
        TaskResult result = TaskResult.ofPrompt("the skill summarizes documents", 0,
                path -> Optional.empty());

        assertThat(evaluator.evaluate(List.of(
                        new AssertionSpec("response_contains", Map.of("value", "summarizes"))), result))
                .isEmpty();
        assertThat(evaluator.evaluate(List.of(
                        new AssertionSpec("response_contains", Map.of("value", "translates"))), result))
                .hasSize(1);
    }

    @Test
    void unknownAssertionTypeFailsEvaluation() {
        List<AssertionSpec> assertions = List.of(new AssertionSpec("mind_read", Map.of()));

        assertThat(evaluator.evaluate(assertions, scriptResult(0, "", "")))
                .singleElement()
                .satisfies(failure -> assertThat(failure.describe()).contains("unknown assertion type"));
    }

    @Test
    void validateSyntaxAcceptsKnownTypesPerTaskKind() {
        List<AssertionSpec> scriptAssertions = List.of(
                new AssertionSpec("exit_code", Map.of("equals", 0)),
                new AssertionSpec("stdout_contains", Map.of("value", "OK")));
        List<AssertionSpec> promptAssertions = List.of(
                new AssertionSpec("response_contains", Map.of("value", "OK")),
                new AssertionSpec("tool_call_count", Map.of("min", 0)));

        assertThat(evaluator.validateSyntax(scriptAssertions, false)).isEmpty();
        assertThat(evaluator.validateSyntax(promptAssertions, true)).isEmpty();

        List<AssertionSpec> crossKind = List.of(
                new AssertionSpec("exit_code", Map.of("equals", 0)),
                new AssertionSpec("response_contains", Map.of("value", "OK")));
        assertThat(evaluator.validateSyntax(crossKind, true)).hasSize(1);
        assertThat(evaluator.validateSyntax(crossKind, false)).hasSize(1);
    }
}
