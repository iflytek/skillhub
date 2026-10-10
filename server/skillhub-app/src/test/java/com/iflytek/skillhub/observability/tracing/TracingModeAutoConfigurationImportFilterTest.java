package com.iflytek.skillhub.observability.tracing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TracingModeAutoConfigurationImportFilterTest {

    private static final String CORE_OTEL_AUTO_CONFIGURATION =
            "org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration";
    private static final String TRACING_OTEL_AUTO_CONFIGURATION =
            "org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryAutoConfiguration";
    private static final String OTLP_AUTO_CONFIGURATION =
            "org.springframework.boot.actuate.autoconfigure.tracing.otlp.OtlpAutoConfiguration";
    private static final String NOOP_AUTO_CONFIGURATION =
            "org.springframework.boot.actuate.autoconfigure.tracing.NoopTracerAutoConfiguration";

    private final TracingModeAutoConfigurationImportFilter filter =
            new TracingModeAutoConfigurationImportFilter();

    @Test
    void shouldExcludeApplicationOtelForDefaultNoneMode() {
        filter.setEnvironment(new MockEnvironment());

        assertThat(matches()).containsExactly(false, false, false, true, false);
    }

    @Test
    void shouldExcludeApplicationOtelForExternalAgentMode() {
        filter.setEnvironment(new MockEnvironment()
                .withProperty(
                        TracingModeAutoConfigurationImportFilter.TRACING_MODE_PROPERTY,
                        "external-agent"
                ));

        assertThat(matches()).containsExactly(false, false, false, true, false);
    }

    @Test
    void shouldEnableApplicationOtelOnlyForOtelSdkMode() {
        filter.setEnvironment(new MockEnvironment()
                .withProperty(
                        TracingModeAutoConfigurationImportFilter.TRACING_MODE_PROPERTY,
                        "otel-sdk"
                ));

        assertThat(matches()).containsExactly(true, true, false, true, false);
    }

    @Test
    void shouldEnableOtlpExporterOnlyWhenEndpointHasText() {
        filter.setEnvironment(new MockEnvironment()
                .withProperty(
                        TracingModeAutoConfigurationImportFilter.TRACING_MODE_PROPERTY,
                        "otel-sdk"
                )
                .withProperty("management.otlp.tracing.endpoint", "http://127.0.0.1:4318/v1/traces"));

        assertThat(matches()).containsExactly(true, true, true, true, false);
    }

    @Test
    void shouldExcludeOtlpExporterWhenEndpointIsEmpty() {
        filter.setEnvironment(new MockEnvironment()
                .withProperty(
                        TracingModeAutoConfigurationImportFilter.TRACING_MODE_PROPERTY,
                        "otel-sdk"
                )
                .withProperty("management.otlp.tracing.endpoint", ""));

        assertThat(matches()).containsExactly(true, true, false, true, false);
    }

    private boolean[] matches() {
        return filter.match(
                new String[]{
                        CORE_OTEL_AUTO_CONFIGURATION,
                        TRACING_OTEL_AUTO_CONFIGURATION,
                        OTLP_AUTO_CONFIGURATION,
                        NOOP_AUTO_CONFIGURATION,
                        null
                },
                mock(AutoConfigurationMetadata.class)
        );
    }
}
