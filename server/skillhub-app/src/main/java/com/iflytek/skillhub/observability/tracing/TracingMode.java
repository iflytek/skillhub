package com.iflytek.skillhub.observability.tracing;

/**
 * Selects the single tracing implementation that may be active in the application process.
 */
public enum TracingMode {
    NONE,
    OTEL_SDK,
    EXTERNAL_AGENT
}
