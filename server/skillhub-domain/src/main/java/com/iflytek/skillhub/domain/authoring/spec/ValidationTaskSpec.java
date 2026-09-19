package com.iflytek.skillhub.domain.authoring.spec;

import java.util.List;

/**
 * One behavior validation task parsed from {@code validation.yaml}.
 */
public record ValidationTaskSpec(
        String name,
        String description,
        TaskType type,
        String script,
        List<String> args,
        String prompt,
        int timeoutMs,
        List<AssertionSpec> assertions
) {

    public List<String> safeArgs() {
        return args == null ? List.of() : List.copyOf(args);
    }

    public List<AssertionSpec> safeAssertions() {
        return assertions == null ? List.of() : List.copyOf(assertions);
    }
}
