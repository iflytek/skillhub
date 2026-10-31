package com.iflytek.skillhub.observability.business;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.common.export.RetryPolicy;
import io.opentelemetry.sdk.resources.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Separate signal lifecycle; no global OTel registration or tracing beans. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BusinessLogsProperties.class)
@ConditionalOnProperty(prefix = "skillhub.observability.business-logs", name = "enabled", havingValue = "true")
public class BusinessLogsConfiguration {
    @Bean
    BusinessLogsMetrics businessLogsMetrics(MeterRegistry registry) { return new BusinessLogsMetrics(registry); }

    @Bean(destroyMethod = "close")
    BusinessLogsPipeline businessLogsPipeline(BusinessLogsProperties properties, BusinessLogsMetrics metrics,
                                               Environment environment) {
        properties.validate();
        var builder = OtlpHttpLogRecordExporter.builder().setEndpoint(properties.getEndpoint())
                .setTimeout(properties.getTimeout()).setCompression(properties.getCompression())
                .setMeterProvider(MeterProvider.noop());
        properties.parsedHeaders().forEach(builder::addHeader);
        if (properties.getRetryMaxAttempts() > 1) {
            builder.setRetryPolicy(RetryPolicy.builder().setMaxAttempts(properties.getRetryMaxAttempts())
                    .setInitialBackoff(properties.getRetryInitialBackoff()).setMaxBackoff(properties.getRetryMaxBackoff())
                    .setBackoffMultiplier(2).build());
        }
        Resource resource = Resource.create(Attributes.builder()
                .put("service.name", BusinessEventMapper.bounded(environment.getProperty("spring.application.name", "skillhub")))
                .put("service.version", BusinessEventMapper.bounded(environment.getProperty("skillhub.observability.service-version", "unknown")))
                .put("deployment.environment", BusinessEventMapper.bounded(environment.getProperty("skillhub.observability.service-environment", "local")))
                .build());
        var exporter = BusinessLogsExporterBoundary.build(builder::build, metrics);
        try {
            return new BusinessLogsPipeline(properties, resource, exporter, metrics);
        } catch (RuntimeException ex) {
            try { exporter.shutdown(); } catch (RuntimeException shutdownException) { metrics.error(); }
            throw new IllegalStateException("Business Logs pipeline initialization failed");
        }
    }

    @Bean
    BusinessEventListener businessEventListener(BusinessLogsProperties properties, BusinessLogsPipeline pipeline,
                                                BusinessLogsMetrics metrics) {
        return new BusinessEventListener(new BusinessEventMapper(properties), pipeline, metrics);
    }
}
