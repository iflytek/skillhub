package com.iflytek.skillhub.domain.authoring.spec;

import java.util.Map;

/**
 * One assertion inside a validation task. Assertions are deliberately loosely typed:
 * {@code type} selects the check and {@code params} carries its typed parameters,
 * which the evaluator validates and coerces. Unknown types or missing parameters are
 * reported as configuration-layer findings before behavior execution starts.
 */
public record AssertionSpec(String type, Map<String, Object> params) {

    public Map<String, Object> safeParams() {
        return params == null ? Map.of() : params;
    }

    public String paramString(String key) {
        Object value = safeParams().get(key);
        return value == null ? null : value.toString();
    }

    public Integer paramInt(String key) {
        Object value = safeParams().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
