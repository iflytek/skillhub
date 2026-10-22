package com.iflytek.skillhub.domain.authoring.spec;

import java.util.List;
import java.util.Optional;

/**
 * Parsed content of a draft's {@code validation.yaml}: the ordered list of behavior
 * validation tasks executed by the BEHAVIOR layer. A draft without the file simply
 * runs zero behavior tasks.
 */
public record ValidationSpec(int version, List<ValidationTaskSpec> tasks) {

    public static final String FILE_PATH = "validation.yaml";
    public static final int CURRENT_VERSION = 1;
    public static final int DEFAULT_TASK_TIMEOUT_MS = 60_000;

    public static ValidationSpec empty() {
        return new ValidationSpec(CURRENT_VERSION, List.of());
    }

    public List<ValidationTaskSpec> safeTasks() {
        return tasks == null ? List.of() : List.copyOf(tasks);
    }

    public Optional<ValidationTaskSpec> findTask(String name) {
        return safeTasks().stream().filter(task -> task.name().equals(name)).findFirst();
    }
}
