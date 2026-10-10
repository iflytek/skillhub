package com.iflytek.skillhub.observability.tracing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Startup-time observability choices owned by SkillHub.
 */
@ConfigurationProperties(prefix = "skillhub.observability")
public class SkillHubObservabilityProperties {

    private TracingMode tracingMode = TracingMode.NONE;

    public TracingMode getTracingMode() {
        return tracingMode;
    }

    public void setTracingMode(TracingMode tracingMode) {
        this.tracingMode = tracingMode;
    }
}
