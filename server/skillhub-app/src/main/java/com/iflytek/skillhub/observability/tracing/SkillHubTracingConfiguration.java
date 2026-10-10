package com.iflytek.skillhub.observability.tracing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Validates tracing mode combinations that SkillHub can determine at startup.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SkillHubObservabilityProperties.class)
public class SkillHubTracingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SkillHubTracingConfiguration.class);

    @Bean
    TracingModeGuard tracingModeGuard(
            SkillHubObservabilityProperties properties,
            Environment environment
    ) {
        TracingMode mode = properties.getTracingMode();
        String otlpEndpoint = environment.getProperty("management.otlp.tracing.endpoint");
        if (mode != TracingMode.OTEL_SDK && StringUtils.hasText(otlpEndpoint)) {
            throw new IllegalStateException(
                    "management.otlp.tracing.endpoint requires "
                            + "skillhub.observability.tracing-mode=otel-sdk"
            );
        }
        if (mode == TracingMode.OTEL_SDK
                && Boolean.FALSE.equals(environment.getProperty(
                        "management.tracing.enabled",
                        Boolean.class
                ))) {
            throw new IllegalStateException(
                    "management.tracing.enabled=false conflicts with "
                            + "skillhub.observability.tracing-mode=otel-sdk"
            );
        }
        if (mode == TracingMode.EXTERNAL_AGENT) {
            log.warn(
                    "External tracing agent mode selected. SkillHub cannot verify the agent "
                            + "identity; deployment must provide exactly one tracing agent"
            );
        }
        return new TracingModeGuard(mode);
    }

    record TracingModeGuard(TracingMode mode) {
    }
}
