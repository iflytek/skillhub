package com.iflytek.skillhub.observability.business;

import com.iflytek.skillhub.observability.tracing.SkillHubTracingConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** Production configurations must coexist in the same startup, before any raw test context. */
@Tag("process-global-otel-context")
class BusinessLogsTracingCoexistenceTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({SkillHubTracingConfiguration.class, BusinessLogsConfiguration.class})
    static class Application {}

    @Test
    void enablingBusinessLogsPreservesTraceAndSpanCorrelation() {
        new ApplicationContextRunner().withUserConfiguration(Application.class)
                .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none",
                        "skillhub.observability.tracing-mode=otel-sdk",
                        "management.tracing.sampling.probability=1",
                        "skillhub.observability.business-logs.enabled=true",
                        "skillhub.observability.business-logs.endpoint=http://127.0.0.1:4318/v1/logs")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(BusinessLogsPipeline.class);
                    Tracer tracer = context.getBean(Tracer.class);
                    var span = tracer.nextSpan().name("business-logs-coexistence").start();
                    try (var ignored = tracer.withSpan(span)) {
                        assertThat(MDC.get("traceId")).isEqualTo(span.context().traceId()).hasSize(32);
                        assertThat(MDC.get("spanId")).isEqualTo(span.context().spanId()).hasSize(16);
                    } finally {
                        span.end();
                        MDC.clear();
                    }
                });
    }
}
