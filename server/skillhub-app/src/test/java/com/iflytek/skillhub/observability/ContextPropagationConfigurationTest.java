package com.iflytek.skillhub.observability;

import com.iflytek.skillhub.config.AsyncConfig;
import com.iflytek.skillhub.observability.tracing.SkillHubTracingConfiguration;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextPropagationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestApplication.class)
            .withPropertyValues(
                    "spring.flyway.enabled=false",
                    "spring.jpa.hibernate.ddl-auto=none"
            );

    @Test
    void requestIdShouldPropagateRestoreNestedScopeAndNotLeakOnThreadReuse() {
        contextRunner
                .withPropertyValues("skillhub.observability.tracing-mode=none")
                .run(context -> {
                    RequestIdAccessor requestIdAccessor =
                            context.getBean(RequestIdAccessor.class);
                    TaskDecorator taskDecorator = context.getBean(TaskDecorator.class);
                    ExecutorService worker = Executors.newSingleThreadExecutor();
                    try {
                        ContextValues propagated;
                        try (RequestIdAccessor.Scope ignored =
                                     requestIdAccessor.open("request-one")) {
                            propagated = execute(worker, taskDecorator, () -> {
                                assertThat(requestIdAccessor.current())
                                        .isEqualTo("request-one");
                                try (RequestIdAccessor.Scope nested =
                                             requestIdAccessor.open("nested")) {
                                    assertThat(requestIdAccessor.current())
                                            .isEqualTo("nested");
                                }
                                return currentValues(requestIdAccessor, null);
                            });
                        }

                        assertThat(propagated.requestId()).isEqualTo("request-one");
                        assertThat(propagated.mdcRequestId()).isEqualTo("request-one");

                        try (RequestIdAccessor.Scope ignored =
                                     requestIdAccessor.open("request-failure")) {
                            assertThatThrownBy(() -> execute(
                                    worker,
                                    taskDecorator,
                                    () -> {
                                        throw new IllegalStateException("expected failure");
                                    }
                            )).hasCauseInstanceOf(IllegalStateException.class);
                        }

                        ContextValues clean = execute(
                                worker,
                                taskDecorator,
                                () -> currentValues(requestIdAccessor, null)
                        );
                        assertThat(clean.requestId()).isNull();
                        assertThat(clean.mdcRequestId()).isNull();
                    } finally {
                        worker.shutdownNow();
                        MDC.clear();
                    }
                });
    }

    @Test
    void configuredEventExecutorShouldPropagateRequestId() {
        contextRunner
                .withPropertyValues("skillhub.observability.tracing-mode=none")
                .run(context -> {
                    RequestIdAccessor requestIdAccessor =
                            context.getBean(RequestIdAccessor.class);
                    Executor executor = context.getBean("skillhubEventExecutor", Executor.class);
                    try (RequestIdAccessor.Scope ignored =
                                 requestIdAccessor.open("configured-executor")) {
                        FutureTask<ContextValues> task = new FutureTask<>(
                                () -> currentValues(requestIdAccessor, null)
                        );
                        executor.execute(task);

                        assertThat(task.get(5, TimeUnit.SECONDS).requestId())
                                .isEqualTo("configured-executor");
                    } finally {
                        MDC.clear();
                    }
                });
    }

    @Test
    void callerRunsPolicyShouldRestoreCallerScopeAndLeaveWorkerClean() {
        contextRunner
                .withPropertyValues("skillhub.observability.tracing-mode=none")
                .run(context -> {
                    RequestIdAccessor requestIdAccessor =
                            context.getBean(RequestIdAccessor.class);
                    TaskDecorator taskDecorator = context.getBean(TaskDecorator.class);
                    ThreadPoolTaskExecutor executor = callerRunsExecutor(taskDecorator);
                    CountDownLatch workerStarted = new CountDownLatch(1);
                    CountDownLatch releaseWorker = new CountDownLatch(1);
                    FutureTask<Void> blockingTask = new FutureTask<>(() -> {
                        workerStarted.countDown();
                        releaseWorker.await(5, TimeUnit.SECONDS);
                        return null;
                    });
                    try {
                        executor.execute(blockingTask);
                        assertThat(workerStarted.await(5, TimeUnit.SECONDS)).isTrue();

                        AtomicReference<ContextValues> callerRunValues =
                                new AtomicReference<>();
                        String callerThread = Thread.currentThread().getName();
                        try (RequestIdAccessor.Scope ignored =
                                     requestIdAccessor.open("caller-request")) {
                            executor.execute(() -> {
                                assertThat(Thread.currentThread().getName())
                                        .isEqualTo(callerThread);
                                callerRunValues.set(currentValues(
                                        requestIdAccessor,
                                        null
                                ));
                            });
                            assertThat(requestIdAccessor.current())
                                    .isEqualTo("caller-request");
                            assertThat(MDC.get(RequestIdAccessor.MDC_KEY))
                                    .isEqualTo("caller-request");
                        }

                        assertThat(callerRunValues.get().requestId())
                                .isEqualTo("caller-request");
                        releaseWorker.countDown();
                        blockingTask.get(5, TimeUnit.SECONDS);

                        FutureTask<ContextValues> cleanTask = new FutureTask<>(
                                () -> currentValues(requestIdAccessor, null)
                        );
                        executor.execute(cleanTask);
                        ContextValues clean = cleanTask.get(5, TimeUnit.SECONDS);
                        assertThat(clean.requestId()).isNull();
                        assertThat(clean.mdcRequestId()).isNull();
                    } finally {
                        releaseWorker.countDown();
                        executor.shutdown();
                        MDC.clear();
                    }
                });
    }

    @Test
    void otelSpanShouldPropagateAndBeClearedAfterTask() {
        contextRunner
                .withPropertyValues(
                        "skillhub.observability.tracing-mode=otel-sdk",
                        "management.tracing.sampling.probability=1.0"
                )
                .run(context -> {
                    RequestIdAccessor requestIdAccessor =
                            context.getBean(RequestIdAccessor.class);
                    TaskDecorator taskDecorator = context.getBean(TaskDecorator.class);
                    Tracer tracer = context.getBean(Tracer.class);
                    ExecutorService worker = Executors.newSingleThreadExecutor();
                    Span span = tracer.nextSpan().name("parent").start();
                    try {
                        ContextValues propagated;
                        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                            propagated = execute(
                                    worker,
                                    taskDecorator,
                                    () -> currentValues(requestIdAccessor, tracer)
                            );
                        }

                        assertThat(propagated.traceId())
                                .isEqualTo(span.context().traceId());
                        assertThat(propagated.mdcTraceId())
                                .isEqualTo(span.context().traceId());

                        ContextValues clean = execute(
                                worker,
                                taskDecorator,
                                () -> currentValues(requestIdAccessor, tracer)
                        );
                        assertThat(clean.traceId()).isNull();
                        assertThat(clean.mdcTraceId()).isNull();
                    } finally {
                        span.end();
                        worker.shutdownNow();
                        MDC.clear();
                    }
                });
    }

    @Test
    void otelObservationShouldPropagateItsTraceAndRestoreWorker() {
        contextRunner
                .withPropertyValues(
                        "skillhub.observability.tracing-mode=otel-sdk",
                        "management.tracing.sampling.probability=1.0"
                )
                .run(context -> {
                    RequestIdAccessor requestIdAccessor =
                            context.getBean(RequestIdAccessor.class);
                    TaskDecorator taskDecorator = context.getBean(TaskDecorator.class);
                    Tracer tracer = context.getBean(Tracer.class);
                    ObservationRegistry observationRegistry =
                            context.getBean(ObservationRegistry.class);
                    ExecutorService worker = Executors.newSingleThreadExecutor();
                    Observation observation = Observation
                            .createNotStarted("parent-observation", observationRegistry)
                            .start();
                    try {
                        ContextValues propagated;
                        String parentTraceId;
                        try (Observation.Scope ignored = observation.openScope()) {
                            assertThat(tracer.currentSpan()).isNotNull();
                            parentTraceId = tracer.currentSpan().context().traceId();
                            propagated = execute(
                                    worker,
                                    taskDecorator,
                                    () -> currentValues(requestIdAccessor, tracer)
                            );
                        }

                        assertThat(propagated.traceId()).isEqualTo(parentTraceId);
                        assertThat(propagated.mdcTraceId()).isEqualTo(parentTraceId);

                        ContextValues clean = execute(
                                worker,
                                taskDecorator,
                                () -> currentValues(requestIdAccessor, tracer)
                        );
                        assertThat(clean.traceId()).isNull();
                        assertThat(clean.mdcTraceId()).isNull();
                    } finally {
                        observation.stop();
                        worker.shutdownNow();
                        MDC.clear();
                    }
                });
    }

    private ContextValues currentValues(
            RequestIdAccessor requestIdAccessor,
            Tracer tracer
    ) {
        Span currentSpan = tracer == null ? null : tracer.currentSpan();
        return new ContextValues(
                requestIdAccessor.current(),
                MDC.get(RequestIdAccessor.MDC_KEY),
                currentSpan == null ? null : currentSpan.context().traceId(),
                MDC.get("traceId")
        );
    }

    private <T> T execute(
            Executor executor,
            TaskDecorator taskDecorator,
            Callable<T> action
    ) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        executor.execute(taskDecorator.decorate(task));
        return task.get(5, TimeUnit.SECONDS);
    }

    private ThreadPoolTaskExecutor callerRunsExecutor(TaskDecorator taskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setTaskDecorator(taskDecorator);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    private record ContextValues(
            String requestId,
            String mdcRequestId,
            String traceId,
            String mdcTraceId
    ) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({
            SkillHubTracingConfiguration.class,
            SkillHubContextPropagationConfiguration.class,
            RequestIdAccessor.class,
            AsyncConfig.class
    })
    static class TestApplication {
    }
}
