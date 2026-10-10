package com.iflytek.skillhub.observability.tracing;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.util.Set;

/**
 * Keeps the OpenTelemetry SDK outside the application context unless the deployment explicitly
 * selects {@code otel-sdk}. The normal Spring Boot NOOP tracer remains available in the other
 * modes.
 */
public final class TracingModeAutoConfigurationImportFilter
        implements AutoConfigurationImportFilter, EnvironmentAware {

    static final String TRACING_MODE_PROPERTY = "skillhub.observability.tracing-mode";

    private static final Set<String> OTEL_AUTO_CONFIGURATIONS = Set.of(
            "org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration",
            "org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryAutoConfiguration"
    );

    private static final Set<String> OTLP_EXPORT_AUTO_CONFIGURATIONS = Set.of(
            "org.springframework.boot.actuate.autoconfigure.tracing.otlp.OtlpAutoConfiguration"
    );

    private Environment environment;

    @Override
    public boolean[] match(
            String[] autoConfigurationClasses,
            AutoConfigurationMetadata autoConfigurationMetadata
    ) {
        boolean otelSdkEnabled = environment != null
                && "otel-sdk".equalsIgnoreCase(
                        environment.getProperty(TRACING_MODE_PROPERTY, "none")
                );
        boolean otlpExportEnabled = otelSdkEnabled
                && hasText(environment.getProperty("management.otlp.tracing.endpoint"));
        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int index = 0; index < autoConfigurationClasses.length; index++) {
            String autoConfigurationClass = autoConfigurationClasses[index];
            matches[index] = autoConfigurationClass != null
                    && ((otelSdkEnabled
                    || !OTEL_AUTO_CONFIGURATIONS.contains(autoConfigurationClass))
                    && (otlpExportEnabled
                    || !OTLP_EXPORT_AUTO_CONFIGURATIONS.contains(autoConfigurationClass)));
        }
        return matches;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
