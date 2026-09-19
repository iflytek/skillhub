package com.iflytek.skillhub.domain.authoring.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Parses a draft's {@code validation.yaml} into {@link ValidationSpec}.
 *
 * <p>Follows the same defensive YAML loading rules as {@code SkillMetadataParser}:
 * SnakeYAML safe constructor, bounded aliases, nesting depth, and code points.
 * Parse problems are reported as structured spec errors (rule code + message) instead
 * of exceptions so the configuration validation layer can surface them as findings.
 */
public class ValidationSpecParser {

    private static final int MAX_YAML_ALIASES = 20;
    private static final int MAX_YAML_NESTING_DEPTH = 20;
    private static final int MAX_YAML_CODE_POINTS = 100_000;
    private static final int MAX_TASKS = 50;
    private static final int MAX_TASK_TIMEOUT_MS = 600_000;

    /** A structured parse or semantic error detected while reading the spec. */
    public record SpecError(String ruleCode, String message) {}

    public record ParseOutcome(ValidationSpec spec, List<SpecError> errors) {

        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    public ParseOutcome parse(String content) {
        if (content == null || content.isBlank()) {
            return failure("SPEC_EMPTY", "validation.yaml is empty");
        }
        Object parsed;
        try {
            LoaderOptions loaderOptions = new LoaderOptions();
            loaderOptions.setAllowDuplicateKeys(false);
            loaderOptions.setMaxAliasesForCollections(MAX_YAML_ALIASES);
            loaderOptions.setNestingDepthLimit(MAX_YAML_NESTING_DEPTH);
            loaderOptions.setCodePointLimit(MAX_YAML_CODE_POINTS);
            Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));
            parsed = yaml.load(content);
        } catch (Exception exception) {
            return failure("SPEC_YAML_INVALID", "validation.yaml is not valid YAML: "
                    + exception.getMessage());
        }
        if (!(parsed instanceof Map<?, ?> root)) {
            return failure("SPEC_NOT_MAP", "validation.yaml must be a YAML object");
        }

        List<SpecError> errors = new ArrayList<>();
        Object versionValue = root.get("version");
        int version = 1;
        if (versionValue instanceof Number number) {
            version = number.intValue();
        } else if (versionValue != null) {
            try {
                version = Integer.parseInt(versionValue.toString().trim());
            } catch (NumberFormatException e) {
                errors.add(new SpecError("SPEC_VERSION_INVALID",
                        "version must be an integer, got: " + versionValue));
            }
        }
        if (version != ValidationSpec.CURRENT_VERSION) {
            errors.add(new SpecError("SPEC_VERSION_INVALID",
                    "unsupported spec version: " + version + " (expected "
                            + ValidationSpec.CURRENT_VERSION + ")"));
        }

        List<ValidationTaskSpec> tasks = new ArrayList<>();
        java.util.Set<String> seenNames = new java.util.HashSet<>();
        Object tasksValue = root.get("tasks");
        if (tasksValue == null) {
            errors.add(new SpecError("SPEC_TASKS_MISSING", "validation.yaml must define a tasks list"));
        } else if (!(tasksValue instanceof List<?> rawTasks)) {
            errors.add(new SpecError("SPEC_TASKS_INVALID", "tasks must be a list"));
        } else {
            if (rawTasks.size() > MAX_TASKS) {
                errors.add(new SpecError("SPEC_TASKS_TOO_MANY",
                        "too many tasks: " + rawTasks.size() + " (max " + MAX_TASKS + ")"));
            }
            for (int i = 0; i < rawTasks.size(); i++) {
                ValidationTaskSpec task = parseTask(rawTasks.get(i), i, errors, seenNames);
                if (task != null) {
                    tasks.add(task);
                }
            }
        }

        if (!errors.isEmpty()) {
            return new ParseOutcome(ValidationSpec.empty(), List.copyOf(errors));
        }
        return new ParseOutcome(new ValidationSpec(version, List.copyOf(tasks)), List.of());
    }

    private ValidationTaskSpec parseTask(Object rawTask, int index, List<SpecError> errors,
                                         java.util.Set<String> seenNames) {
        String where = "tasks[" + index + "]";
        if (!(rawTask instanceof Map<?, ?> taskMap)) {
            errors.add(new SpecError("TASK_NOT_MAP", where + " must be a YAML object"));
            return null;
        }

        String name = stringField(taskMap, "name");
        if (name == null || name.isBlank()) {
            errors.add(new SpecError("TASK_NAME_MISSING", where + " is missing a name"));
        } else if (!name.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}")) {
            errors.add(new SpecError("TASK_NAME_INVALID",
                    where + " has an invalid name: " + name));
        } else if (!seenNames.add(name)) {
            errors.add(new SpecError("TASK_NAME_DUPLICATE", where + " duplicates task name: " + name));
        }

        String typeRaw = stringField(taskMap, "type");
        TaskType type;
        if (typeRaw == null) {
            errors.add(new SpecError("TASK_TYPE_MISSING", where + " is missing a type"));
            return null;
        }
        try {
            type = TaskType.valueOf(typeRaw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add(new SpecError("TASK_TYPE_INVALID",
                    where + " has unknown type: " + typeRaw + " (expected script or prompt)"));
            return null;
        }

        String script = stringField(taskMap, "script");
        String prompt = stringField(taskMap, "prompt");
        if (type == TaskType.SCRIPT && (script == null || script.isBlank())) {
            errors.add(new SpecError("TASK_SCRIPT_MISSING",
                    where + " (script task) must define script"));
        }
        if (type == TaskType.PROMPT && (prompt == null || prompt.isBlank())) {
            errors.add(new SpecError("TASK_PROMPT_MISSING",
                    where + " (prompt task) must define prompt"));
        }

        List<String> args = new ArrayList<>();
        Object argsValue = taskMap.get("args");
        if (argsValue instanceof List<?> rawArgs) {
            for (Object arg : rawArgs) {
                if (arg != null) {
                    args.add(arg.toString());
                }
            }
        } else if (argsValue != null) {
            errors.add(new SpecError("TASK_ARGS_INVALID", where + " args must be a list"));
        }

        int timeoutMs = ValidationSpec.DEFAULT_TASK_TIMEOUT_MS;
        Object timeoutValue = taskMap.get("timeoutMs");
        if (timeoutValue instanceof Number number) {
            timeoutMs = number.intValue();
        } else if (timeoutValue != null) {
            errors.add(new SpecError("TASK_TIMEOUT_INVALID",
                    where + " timeoutMs must be an integer"));
        }
        if (timeoutMs <= 0 || timeoutMs > MAX_TASK_TIMEOUT_MS) {
            errors.add(new SpecError("TASK_TIMEOUT_INVALID",
                    where + " timeoutMs must be between 1 and " + MAX_TASK_TIMEOUT_MS));
        }

        List<AssertionSpec> assertions = new ArrayList<>();
        Object assertionsValue = taskMap.get("assertions");
        if (assertionsValue instanceof List<?> rawAssertions) {
            for (Object rawAssertion : rawAssertions) {
                if (rawAssertion instanceof Map<?, ?> assertionMap) {
                    Object assertionType = assertionMap.get("type");
                    Map<String, Object> params = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : assertionMap.entrySet()) {
                        if (!"type".equals(entry.getKey()) && entry.getKey() != null) {
                            params.put(entry.getKey().toString(), entry.getValue());
                        }
                    }
                    if (assertionType == null || assertionType.toString().isBlank()) {
                        errors.add(new SpecError("ASSERTION_TYPE_MISSING",
                                where + " has an assertion without a type"));
                    } else {
                        assertions.add(new AssertionSpec(assertionType.toString(), params));
                    }
                } else {
                    errors.add(new SpecError("ASSERTION_NOT_MAP",
                            where + " assertions must be objects"));
                }
            }
        } else if (assertionsValue != null) {
            errors.add(new SpecError("ASSERTIONS_INVALID", where + " assertions must be a list"));
        }

        String description = stringField(taskMap, "description");
        return new ValidationTaskSpec(name == null ? where : name, description, type, script,
                List.copyOf(args), prompt, timeoutMs, List.copyOf(assertions));
    }

    private String stringField(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    private ParseOutcome failure(String ruleCode, String message) {
        return new ParseOutcome(ValidationSpec.empty(), List.of(new SpecError(ruleCode, message)));
    }
}
