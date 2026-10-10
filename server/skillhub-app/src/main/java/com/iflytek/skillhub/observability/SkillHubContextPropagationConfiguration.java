package com.iflytek.skillhub.observability;

import com.iflytek.skillhub.observability.tracing.SkillHubObservabilityProperties;
import com.iflytek.skillhub.observability.tracing.TracingMode;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.contextpropagation.ObservationAwareSpanThreadLocalAccessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

/**
 * Defines the context captured by SkillHub-managed asynchronous executors.
 */
@Configuration(proxyBeanMethods = false)
public class SkillHubContextPropagationConfiguration {

    @Bean
    ContextRegistry skillHubContextRegistry(
            RequestIdAccessor requestIdAccessor,
            SkillHubObservabilityProperties observabilityProperties,
            ObservationRegistry observationRegistry,
            Tracer tracer
    ) {
        ContextRegistry registry = new ContextRegistry()
                .loadContextAccessors()
                .loadThreadLocalAccessors();
        registry.registerThreadLocalAccessor(
                new RequestIdThreadLocalAccessor(requestIdAccessor)
        );
        if (observabilityProperties.getTracingMode() == TracingMode.OTEL_SDK) {
            registry.registerThreadLocalAccessor(
                    new ObservationAwareSpanThreadLocalAccessor(observationRegistry, tracer)
            );
        }
        return registry;
    }

    @Bean
    ContextPropagatingTaskDecorator skillHubContextPropagatingTaskDecorator(
            ContextRegistry skillHubContextRegistry
    ) {
        ContextSnapshotFactory snapshotFactory = ContextSnapshotFactory.builder()
                .contextRegistry(skillHubContextRegistry)
                .clearMissing(true)
                .build();
        return new ContextPropagatingTaskDecorator(snapshotFactory);
    }
}
