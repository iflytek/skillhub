package com.iflytek.skillhub.domain.authoring.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationSpecParserTest {

    private final ValidationSpecParser parser = new ValidationSpecParser();

    @Test
    void parsesValidScriptTask() {
        ValidationSpecParser.ParseOutcome outcome = parser.parse("""
                version: 1
                tasks:
                  - name: smoke
                    description: basic check
                    type: script
                    script: scripts/check.sh
                    args: ["--quiet"]
                    timeoutMs: 5000
                    assertions:
                      - type: exit_code
                        equals: 0
                      - type: stdout_contains
                        value: OK
                """);

        assertThat(outcome.isValid()).isTrue();
        ValidationSpec spec = outcome.spec();
        assertThat(spec.version()).isEqualTo(1);
        assertThat(spec.safeTasks()).hasSize(1);
        ValidationTaskSpec task = spec.safeTasks().get(0);
        assertThat(task.name()).isEqualTo("smoke");
        assertThat(task.type()).isEqualTo(TaskType.SCRIPT);
        assertThat(task.script()).isEqualTo("scripts/check.sh");
        assertThat(task.safeArgs()).containsExactly("--quiet");
        assertThat(task.timeoutMs()).isEqualTo(5000);
        assertThat(task.safeAssertions()).hasSize(2);
        assertThat(task.safeAssertions().get(0).type()).isEqualTo("exit_code");
        assertThat(task.safeAssertions().get(0).paramInt("equals")).isZero();
    }

    @Test
    void parsesPromptTaskWithoutOptionalFields() {
        ValidationSpecParser.ParseOutcome outcome = parser.parse("""
                version: 1
                tasks:
                  - name: ask
                    type: prompt
                    prompt: What is this skill about?
                """);

        assertThat(outcome.isValid()).isTrue();
        ValidationTaskSpec task = outcome.spec().safeTasks().get(0);
        assertThat(task.type()).isEqualTo(TaskType.PROMPT);
        assertThat(task.timeoutMs()).isEqualTo(ValidationSpec.DEFAULT_TASK_TIMEOUT_MS);
        assertThat(task.safeAssertions()).isEmpty();
    }

    @Test
    void emptyContentIsASpecError() {
        ValidationSpecParser.ParseOutcome outcome = parser.parse("   \n");

        assertThat(outcome.isValid()).isFalse();
        assertThat(outcome.errors()).singleElement()
                .satisfies(error -> assertThat(error.ruleCode()).isEqualTo("SPEC_EMPTY"));
    }

    @Test
    void invalidYamlIsASpecErrorNotAnException() {
        ValidationSpecParser.ParseOutcome outcome = parser.parse("version: [unbalanced");

        assertThat(outcome.isValid()).isFalse();
        assertThat(outcome.errors()).singleElement()
                .satisfies(error -> assertThat(error.ruleCode()).isEqualTo("SPEC_YAML_INVALID"));
    }

    @Test
    void unsupportedVersionIsRejected() {
        ValidationSpecParser.ParseOutcome outcome = parser.parse("""
                version: 2
                tasks: []
                """);

        assertThat(outcome.isValid()).isFalse();
        assertThat(outcome.errors()).singleElement()
                .satisfies(error -> assertThat(error.ruleCode()).isEqualTo("SPEC_VERSION_INVALID"));
    }

    @Test
    void missingTasksListIsRejected() {
        ValidationSpecParser.ParseOutcome outcome = parser.parse("version: 1\n");

        assertThat(outcome.isValid()).isFalse();
        assertThat(outcome.errors()).singleElement()
                .satisfies(error -> assertThat(error.ruleCode()).isEqualTo("SPEC_TASKS_MISSING"));
    }

    @Test
    void duplicateTaskNamesAreRejected() {
        ValidationSpecParser.ParseOutcome outcome = parser.parse("""
                version: 1
                tasks:
                  - name: smoke
                    type: script
                    script: a.sh
                  - name: smoke
                    type: script
                    script: b.sh
                """);

        assertThat(outcome.isValid()).isFalse();
        assertThat(outcome.errors()).singleElement()
                .satisfies(error -> assertThat(error.ruleCode()).isEqualTo("TASK_NAME_DUPLICATE"));
    }

    @Test
    void scriptTaskRequiresScriptAndPromptTaskRequiresPrompt() {
        ValidationSpecParser.ParseOutcome outcome = parser.parse("""
                version: 1
                tasks:
                  - name: no-script
                    type: script
                  - name: no-prompt
                    type: prompt
                """);

        List<String> codes = outcome.errors().stream().map(ValidationSpecParser.SpecError::ruleCode).toList();
        assertThat(codes).containsExactly("TASK_SCRIPT_MISSING", "TASK_PROMPT_MISSING");
    }

    @Test
    void unknownTypeAndInvalidTimeoutAreRejected() {
        ValidationSpecParser.ParseOutcome outcome = parser.parse("""
                version: 1
                tasks:
                  - name: weird
                    type: telepathy
                  - name: slow
                    type: script
                    script: a.sh
                    timeoutMs: 999999999
                """);

        List<String> codes = outcome.errors().stream().map(ValidationSpecParser.SpecError::ruleCode).toList();
        assertThat(codes).contains("TASK_TYPE_INVALID", "TASK_TIMEOUT_INVALID");
    }

    @Test
    void invalidTaskNameIsRejected() {
        ValidationSpecParser.ParseOutcome outcome = parser.parse("""
                version: 1
                tasks:
                  - name: "has space"
                    type: script
                    script: a.sh
                """);

        assertThat(outcome.isValid()).isFalse();
        assertThat(outcome.errors()).singleElement()
                .satisfies(error -> assertThat(error.ruleCode()).isEqualTo("TASK_NAME_INVALID"));
    }

    @Test
    void assertionWithoutTypeIsRejected() {
        ValidationSpecParser.ParseOutcome outcome = parser.parse("""
                version: 1
                tasks:
                  - name: smoke
                    type: script
                    script: a.sh
                    assertions:
                      - equals: 0
                """);

        assertThat(outcome.isValid()).isFalse();
        assertThat(outcome.errors()).singleElement()
                .satisfies(error -> assertThat(error.ruleCode()).isEqualTo("ASSERTION_TYPE_MISSING"));
    }
}
