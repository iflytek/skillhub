package com.iflytek.skillhub.observability.tracing;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class SkillHubTracingConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestApplication.class)
            .withPropertyValues(
                    "spring.flyway.enabled=false",
                    "spring.jpa.hibernate.ddl-auto=none"
            );

    @Test
    void noneModeShouldUseNoopTracerAndNoOtelSdk() {
        contextRunner
                .withPropertyValues("skillhub.observability.tracing-mode=none")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(Tracer.class)).isSameAs(Tracer.NOOP);
                    assertThat(context).doesNotHaveBean(OpenTelemetry.class);
                    assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class);
                });
    }

    @Test
    void externalAgentModeShouldUseNoopTracerAndNoOtelSdk() {
        contextRunner
                .withPropertyValues("skillhub.observability.tracing-mode=external-agent")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(Tracer.class)).isSameAs(Tracer.NOOP);
                    assertThat(context).doesNotHaveBean(OpenTelemetry.class);
                    assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class);
                });
    }

    @Test
    void otelSdkModeWithoutEndpointShouldCreateInProcessTracerOnly() {
        contextRunner
                .withPropertyValues(
                        "skillhub.observability.tracing-mode=otel-sdk",
                        "management.tracing.sampling.probability=1.0",
                        "management.tracing.baggage.enabled=false",
                        "management.tracing.propagation.type=W3C"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(Tracer.class)).isInstanceOf(OtelTracer.class);
                    assertThat(context).hasSingleBean(OpenTelemetry.class);
                    assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class);
                });
    }

    @Test
    void otelSdkModeWithEmptyEndpointShouldCreateInProcessTracerOnly() {
        contextRunner
                .withPropertyValues(
                        "skillhub.observability.tracing-mode=otel-sdk",
                        "management.otlp.tracing.endpoint=",
                        "management.tracing.sampling.probability=1.0",
                        "management.tracing.baggage.enabled=false",
                        "management.tracing.propagation.type=W3C"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(Tracer.class)).isInstanceOf(OtelTracer.class);
                    assertThat(context).hasSingleBean(OpenTelemetry.class);
                    assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class);
                });
    }

    @Test
    void otelSdkModeShouldCreateExporterOnlyWhenEndpointIsConfigured() {
        contextRunner
                .withPropertyValues(
                        "skillhub.observability.tracing-mode=otel-sdk",
                        "management.otlp.tracing.endpoint=http://127.0.0.1:4318/v1/traces"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OtlpHttpSpanExporter.class);
                });
    }

    @Test
    void nonOtelModeShouldRejectConfiguredOtlpEndpoint() {
        contextRunner
                .withPropertyValues(
                        "skillhub.observability.tracing-mode=none",
                        "management.otlp.tracing.endpoint=http://127.0.0.1:4318/v1/traces"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void otelSdkModeShouldRejectDisabledTracing() {
        contextRunner
                .withPropertyValues(
                        "skillhub.observability.tracing-mode=otel-sdk",
                        "management.tracing.enabled=false"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void otelSdkScopeShouldPublishTraceCorrelationToMdc() {
        contextRunner
                .withPropertyValues(
                        "skillhub.observability.tracing-mode=otel-sdk",
                        "management.tracing.sampling.probability=1.0"
                )
                .run(context -> {
                    Tracer tracer = context.getBean(Tracer.class);
                    io.micrometer.tracing.Span span = tracer.nextSpan().name("test-span").start();
                    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                        assertThat(MDC.get("traceId")).hasSize(32);
                        assertThat(MDC.get("spanId")).hasSize(16);
                    } finally {
                        span.end();
                        MDC.clear();
                    }
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(SkillHubTracingConfiguration.class)
    static class TestApplication {
    }
}
