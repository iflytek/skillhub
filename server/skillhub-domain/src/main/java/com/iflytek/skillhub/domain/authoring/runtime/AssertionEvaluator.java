package com.iflytek.skillhub.domain.authoring.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.authoring.spec.AssertionSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Evaluates rule-based assertions against a task result. Deterministic checks only:
 * exit codes, regex/substring matching, JSON pointer equality, artifact presence and
 * hashes, and tool call counts. Semantic quality checks are intentionally not
 * approximated here — they belong to model-assisted review, which stays outside the
 * minimal closed loop.
 */
public class AssertionEvaluator {

    /** Assertion types valid for SCRIPT tasks. */
    public static final List<String> SCRIPT_ASSERTION_TYPES = List.of(
            "exit_code", "stdout_matches", "stdout_contains", "stdout_json",
            "artifact_exists", "tool_call_count");

    /** Assertion types valid for PROMPT tasks. */
    public static final List<String> PROMPT_ASSERTION_TYPES = List.of(
            "response_matches", "response_contains", "response_json", "tool_call_count");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Validates assertion types and required parameters without a result. Used by the
     * configuration layer so malformed assertions fail fast, before any execution.
     */
    public List<String> validateSyntax(List<AssertionSpec> assertions, boolean promptTask) {
        List<String> problems = new ArrayList<>();
        List<String> allowed = promptTask ? PROMPT_ASSERTION_TYPES : SCRIPT_ASSERTION_TYPES;
        for (AssertionSpec assertion : assertions) {
            String type = assertion.type();
            if (type == null || type.isBlank()) {
                problems.add("assertion without a type");
                continue;
            }
            if (!allowed.contains(type)) {
                problems.add("assertion type '" + type + "' is not valid for "
                        + (promptTask ? "prompt" : "script") + " tasks (allowed: "
                        + String.join(", ", allowed) + ")");
                continue;
            }
            switch (type) {
                case "exit_code" -> {
                    if (assertion.paramInt("equals") == null) {
                        problems.add("exit_code assertion requires an integer 'equals'");
                    }
                }
                case "stdout_matches", "response_matches" -> {
                    String pattern = assertion.paramString("pattern");
                    if (pattern == null || pattern.isBlank()) {
                        problems.add(type + " assertion requires a 'pattern'");
                    } else if (!compiles(pattern)) {
                        problems.add(type + " assertion has an invalid regex: " + pattern);
                    }
                }
                case "stdout_contains", "response_contains" -> {
                    if (assertion.paramString("value") == null) {
                        problems.add(type + " assertion requires a 'value'");
                    }
                }
                case "stdout_json", "response_json" -> {
                    if (assertion.paramString("pointer") == null) {
                        problems.add(type + " assertion requires a 'pointer'");
                    }
                    if (assertion.paramString("equals") == null && assertion.paramInt("equals") == null) {
                        problems.add(type + " assertion requires an 'equals' value");
                    }
                }
                case "artifact_exists" -> {
                    if (assertion.paramString("path") == null || assertion.paramString("path").isBlank()) {
                        problems.add("artifact_exists assertion requires a 'path'");
                    }
                }
                case "tool_call_count" -> {
                    Integer min = assertion.paramInt("min");
                    Integer max = assertion.paramInt("max");
                    if (min == null && max == null) {
                        problems.add("tool_call_count assertion requires 'min' and/or 'max'");
                    }
                }
                default -> problems.add("unknown assertion type: " + type);
            }
        }
        return problems;
    }

    /** Evaluates all assertions against the task result; empty list means all passed. */
    public List<AssertionFailure> evaluate(List<AssertionSpec> assertions, TaskResult result) {
        List<AssertionFailure> failures = new ArrayList<>();
        for (AssertionSpec assertion : assertions) {
            String reason = evaluateOne(assertion, result);
            if (reason != null) {
                failures.add(new AssertionFailure(assertion, reason));
            }
        }
        return failures;
    }

    private String evaluateOne(AssertionSpec assertion, TaskResult result) {
        String type = assertion.type();
        return switch (type == null ? "" : type) {
            case "exit_code" -> {
                Integer expected = assertion.paramInt("equals");
                if (result.exitCode() == null) {
                    yield "task did not run to completion, no exit code";
                }
                if (expected == null || result.exitCode() != expected) {
                    yield "expected exit code " + expected + " but got " + result.exitCode();
                }
                yield null;
            }
            case "stdout_matches" -> matchPattern(assertion.paramString("pattern"), result.stdout(), "stdout");
            case "stdout_contains" -> containsValue(assertion.paramString("value"), result.stdout(), "stdout");
            case "response_matches" -> matchPattern(assertion.paramString("pattern"), result.response(), "response");
            case "response_contains" -> containsValue(assertion.paramString("value"), result.response(), "response");
            case "stdout_json" -> jsonPointer(assertion, result.stdout(), "stdout");
            case "response_json" -> jsonPointer(assertion, result.response(), "response");
            case "artifact_exists" -> artifactExists(assertion, result);
            case "tool_call_count" -> toolCallCount(assertion, result);
            default -> "unknown assertion type: " + type;
        };
    }

    private String matchPattern(String patternText, String actual, String field) {
        if (patternText == null) {
            return "pattern is missing";
        }
        if (actual == null) {
            return field + " is empty";
        }
        if (!Pattern.compile(patternText).matcher(actual).find()) {
            return field + " does not match pattern: " + patternText;
        }
        return null;
    }

    private String containsValue(String value, String actual, String field) {
        if (value == null) {
            return "value is missing";
        }
        if (actual == null || !actual.contains(value)) {
            return field + " does not contain: " + value;
        }
        return null;
    }

    private String jsonPointer(AssertionSpec assertion, String actual, String field) {
        String pointer = assertion.paramString("pointer");
        String expected = assertion.paramString("equals");
        if (pointer == null || expected == null) {
            return "pointer or equals is missing";
        }
        if (actual == null || actual.isBlank()) {
            return field + " is empty";
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(actual);
        } catch (Exception exception) {
            return field + " is not valid JSON";
        }
        JsonNode node = root.at(pointer);
        if (node.isMissingNode()) {
            return field + " has no value at pointer " + pointer;
        }
        String actualText = node.isTextual() ? node.asText() : node.toString();
        if (!expected.equals(actualText)) {
            return "value at " + pointer + " is " + actualText + ", expected " + expected;
        }
        return null;
    }

    private String artifactExists(AssertionSpec assertion, TaskResult result) {
        String path = assertion.paramString("path");
        if (path == null) {
            return "path is missing";
        }
        byte[] content = result.artifacts().read(path).orElse(null);
        if (content == null) {
            return "artifact not found: " + path;
        }
        String expectedSha = assertion.paramString("sha256");
        if (expectedSha != null && !expectedSha.isBlank()) {
            String actualSha = TaskResult.sha256Hex(content);
            if (!expectedSha.toLowerCase().equals(actualSha)) {
                return "artifact " + path + " sha256 is " + actualSha + ", expected " + expectedSha;
            }
        }
        return null;
    }

    private String toolCallCount(AssertionSpec assertion, TaskResult result) {
        Integer min = assertion.paramInt("min");
        Integer max = assertion.paramInt("max");
        int actual = result.toolCallCount();
        if (min != null && actual < min) {
            return "tool call count " + actual + " is below minimum " + min;
        }
        if (max != null && actual > max) {
            return "tool call count " + actual + " exceeds maximum " + max;
        }
        return null;
    }

    private boolean compiles(String pattern) {
        try {
            Pattern.compile(pattern);
            return true;
        } catch (PatternSyntaxException exception) {
            return false;
        }
    }
}
