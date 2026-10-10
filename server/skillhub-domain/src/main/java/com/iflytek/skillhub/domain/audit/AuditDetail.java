package com.iflytek.skillhub.domain.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the JSON stored in {@code audit_log.detail_json}.
 *
 * <p>That column is PostgreSQL {@code JSONB}, so the value has to be valid JSON or the
 * insert fails. Hand-concatenating it does not survive real input: a value containing a
 * newline, tab, backslash, or any other control character produces a string PostgreSQL
 * rejects, and the audit write then fails after the domain mutation has already
 * committed. Escaping only {@code "} — or only {@code "} and {@code \} — is not enough.
 *
 * <p>Every audit detail payload should be built here so there is exactly one place where
 * that escaping is decided.
 *
 * <p>The mapper is a private static instance rather than the injected application bean on
 * purpose: audit records are a stored format, and they should not change shape because
 * someone reconfigures Jackson elsewhere in the application.
 */
public final class AuditDetail {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AuditDetail() {
    }

    /**
     * Renders a single-field detail payload, e.g. {@code {"slug":"my-skill"}}.
     */
    public static String of(String key, Object value) {
        return builder().put(key, value).build();
    }

    /**
     * Renders a two-field detail payload, preserving argument order.
     */
    public static String of(String key1, Object value1, String key2, Object value2) {
        return builder().put(key1, value1).put(key2, value2).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Accumulates fields in insertion order. Use it when a field is conditional.
     */
    public static final class Builder {

        private final Map<String, Object> fields = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Adds a field. A {@code null} value is skipped, so an optional field can be
         * offered unconditionally.
         */
        public Builder put(String key, Object value) {
            if (value != null) {
                fields.put(key, value);
            }
            return this;
        }

        /**
         * Returns the rendered JSON, or {@code null} when no field was set — the audit
         * log stores {@code null} rather than an empty object for "no detail".
         */
        public String build() {
            if (fields.isEmpty()) {
                return null;
            }
            try {
                return MAPPER.writeValueAsString(fields);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize audit detail JSON", e);
            }
        }
    }
}
