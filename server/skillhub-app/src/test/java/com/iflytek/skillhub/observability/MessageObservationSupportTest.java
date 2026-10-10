package com.iflytek.skillhub.observability;

import com.iflytek.skillhub.observability.tracing.SkillHubTracingConfiguration;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MessageObservationSupportTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestApplication.class)
            .withPropertyValues(
                    "spring.flyway.enabled=false",
                    "spring.jpa.hibernate.ddl-auto=none"
            );

    @Test
    void shouldPropagateTraceAndRequestIdAcrossMessageBoundaryWithoutLeakingWorkerContext() {
        contextRunner
                .withPropertyValues(
                        "skillhub.observability.tracing-mode=otel-sdk",
                        "management.tracing.sampling.probability=1.0"
                )
                .run(context -> {
                    ObservationRegistry observationRegistry = context.getBean(ObservationRegistry.class);
                    RequestIdAccessor requestIdAccessor = context.getBean(RequestIdAccessor.class);
                    Tracer tracer = context.getBean(Tracer.class);
                    MessageObservationSupport support = new MessageObservationSupport(
                            observationRegistry,
                            requestIdAccessor
                    );
                    TestCarrier carrier = new TestCarrier();
                    Observation parent = Observation.start("publish-request", observationRegistry);
                    String parentTraceId;
                    String producerSpanId;

                    try (RequestIdAccessor.Scope ignored = requestIdAccessor.open("request-async-1");
                         Observation.Scope observationScope = parent.openScope()) {
                        parentTraceId = tracer.currentSpan().context().traceId();
                        producerSpanId = support.observePublish(
                                "redis",
                                "skillhub:scan:requests",
                                carrier,
                                TEST_CARRIER_ADAPTER,
                                () -> tracer.currentSpan().context().spanId()
                        );
                    } finally {
                        parent.stop();
                    }

                    assertThat(carrier.get(MessageObservationSupport.REQUEST_ID_FIELD))
                            .isEqualTo("request-async-1");
                    assertThat(carrier.get("traceparent"))
                            .matches("^00-" + parentTraceId + "-[0-9a-f]{16}-0[01]$");

                    ExecutorService worker = Executors.newSingleThreadExecutor();
                    try {
                        ContextValues consumed = worker.submit(() -> support.observeProcess(
                                "redis",
                                "skillhub:scan:requests",
                                carrier,
                                TEST_CARRIER_ADAPTER,
                                () -> currentValues(requestIdAccessor, tracer)
                        )).get(5, TimeUnit.SECONDS);

                        assertThat(consumed.requestId()).isEqualTo("request-async-1");
                        assertThat(consumed.mdcRequestId()).isEqualTo("request-async-1");
                        assertThat(consumed.traceId()).isEqualTo(parentTraceId);
                        assertThat(consumed.spanId()).isNotEqualTo(producerSpanId);

                        ContextValues clean = worker.submit(
                                () -> currentValues(requestIdAccessor, tracer)
                        ).get(5, TimeUnit.SECONDS);
                        assertThat(clean.requestId()).isNull();
                        assertThat(clean.mdcRequestId()).isNull();
                        assertThat(clean.traceId()).isNull();
                        assertThat(clean.spanId()).isNull();
                    } finally {
                        worker.shutdownNow();
                        MDC.clear();
                    }
                });
    }

    @Test
    void shouldClearMissingOrInvalidMessageContextAndRestoreOuterScope() {
        RequestIdAccessor requestIdAccessor = new RequestIdAccessor();
        MessageObservationSupport support = new MessageObservationSupport(
                ObservationRegistry.NOOP,
                requestIdAccessor
        );
        TestCarrier carrier = new TestCarrier();
        carrier.set(MessageObservationSupport.REQUEST_ID_FIELD, "invalid request id");

        try (RequestIdAccessor.Scope ignored = requestIdAccessor.open("outer-request")) {
            String valueInsideMessage = support.observeProcess(
                    "test-broker",
                    "jobs",
                    carrier,
                    TEST_CARRIER_ADAPTER,
                    requestIdAccessor::current
            );

            assertThat(valueInsideMessage).isNull();
            assertThat(requestIdAccessor.current()).isEqualTo("outer-request");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void shouldRemoveCallerSuppliedTransportContextBeforePublishingWithoutTracer() {
        RequestIdAccessor requestIdAccessor = new RequestIdAccessor();
        MessageObservationSupport support = new MessageObservationSupport(
                ObservationRegistry.NOOP,
                requestIdAccessor
        );
        TestCarrier carrier = new TestCarrier();
        carrier.set("traceparent", "caller-controlled");
        carrier.set(MessageObservationSupport.REQUEST_ID_FIELD, "caller-controlled");

        try (RequestIdAccessor.Scope ignored = requestIdAccessor.open("trusted-request")) {
            support.observePublish(
                    "test-broker",
                    "jobs",
                    carrier,
                    TEST_CARRIER_ADAPTER,
                    () -> null
            );
        } finally {
            MDC.clear();
        }

        assertThat(carrier.get("traceparent")).isNull();
        assertThat(carrier.get(MessageObservationSupport.REQUEST_ID_FIELD))
                .isEqualTo("trusted-request");
    }

    @Test
    void shouldUseOpenTelemetryMessagingOperationSemantics() {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        List<Observation.Context> stoppedContexts = new ArrayList<>();
        observationRegistry.observationConfig().observationHandler(
                new ObservationHandler<Observation.Context>() {
                    @Override
                    public void onStop(Observation.Context context) {
                        stoppedContexts.add(context);
                    }

                    @Override
                    public boolean supportsContext(Observation.Context context) {
                        return true;
                    }
                }
        );
        MessageObservationSupport support = new MessageObservationSupport(
                observationRegistry,
                new RequestIdAccessor()
        );
        TestCarrier carrier = new TestCarrier();

        support.observePublish(
                "redis",
                "skillhub:scan:requests",
                carrier,
                TEST_CARRIER_ADAPTER,
                () -> null
        );
        support.observeProcess(
                "redis",
                "skillhub:scan:requests",
                carrier,
                TEST_CARRIER_ADAPTER,
                () -> null
        );

        Observation.Context publish = findContext(stoppedContexts, "skillhub.message.publish");
        assertThat(publish.getContextualName()).isEqualTo("publish skillhub:scan:requests");
        assertThat(lowCardinalityValue(publish, "messaging.operation.name")).isEqualTo("publish");
        assertThat(lowCardinalityValue(publish, "messaging.operation.type")).isEqualTo("send");

        Observation.Context process = findContext(stoppedContexts, "skillhub.message.process");
        assertThat(process.getContextualName()).isEqualTo("process skillhub:scan:requests");
        assertThat(lowCardinalityValue(process, "messaging.operation.name")).isEqualTo("process");
        assertThat(lowCardinalityValue(process, "messaging.operation.type")).isEqualTo("process");
    }

    private Observation.Context findContext(
            List<Observation.Context> contexts,
            String observationName
    ) {
        return contexts.stream()
                .filter(context -> observationName.equals(context.getName()))
                .findFirst()
                .orElseThrow();
    }

    private String lowCardinalityValue(Observation.Context context, String key) {
        for (KeyValue keyValue : context.getLowCardinalityKeyValues()) {
            if (key.equals(keyValue.getKey())) {
                return keyValue.getValue();
            }
        }
        return null;
    }

    private ContextValues currentValues(RequestIdAccessor requestIdAccessor, Tracer tracer) {
        Span currentSpan = tracer.currentSpan();
        return new ContextValues(
                requestIdAccessor.current(),
                MDC.get(RequestIdAccessor.MDC_KEY),
                currentSpan == null ? null : currentSpan.context().traceId(),
                currentSpan == null ? null : currentSpan.context().spanId()
        );
    }

    private record ContextValues(
            String requestId,
            String mdcRequestId,
            String traceId,
            String spanId
    ) {
    }

    private static final MessageCarrierAdapter<TestCarrier> TEST_CARRIER_ADAPTER =
            new MessageCarrierAdapter<>() {
                @Override
                public String get(TestCarrier carrier, String key) {
                    return carrier.get(key);
                }

                @Override
                public void set(TestCarrier carrier, String key, String value) {
                    carrier.set(key, value);
                }

                @Override
                public void remove(TestCarrier carrier, String key) {
                    carrier.set(key, null);
                }
            };

    private static final class TestCarrier {
        private final List<Header> headers = new ArrayList<>();

        private void set(String key, String value) {
            headers.removeIf(header -> header.key().equals(key));
            if (value != null) {
                headers.add(new Header(key, value));
            }
        }

        private String get(String key) {
            return headers.stream()
                    .filter(header -> header.key().equals(key))
                    .map(Header::value)
                    .findFirst()
                    .orElse(null);
        }
    }

    private record Header(String key, String value) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({SkillHubTracingConfiguration.class, RequestIdAccessor.class})
    static class TestApplication {
    }
}
