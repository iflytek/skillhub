package com.iflytek.skillhub.observability.business;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessLogsExporterBoundaryTest {
    @Test
    void onlyOwnedDispatchersAreContainedAndPreviousDefaultHandlerIsUnchanged() throws Exception {
        var previous = Thread.getDefaultUncaughtExceptionHandler();
        var handled = new CopyOnWriteArrayList<Thread>();
        var failures = new CopyOnWriteArrayList<Throwable>();
        Thread.UncaughtExceptionHandler sentinel = (thread, failure) -> { handled.add(thread); failures.add(failure); };
        Thread.setDefaultUncaughtExceptionHandler(sentinel);
        try {
            var metrics = new BusinessLogsMetrics(new SimpleMeterRegistry());
            ThreadGroup group = BusinessLogsExporterBoundary.build(() -> Thread.currentThread().getThreadGroup(), metrics);
            var failure = new IllegalStateException("SYNTHETIC-1009-UNCAUGHT-PLACEHOLDER");
            Thread owned = new Thread(group, () -> { throw failure; }, "okhttp-dispatch-1");
            // Same dispatcher name outside the private group must NOT be swallowed.
            Thread unrelated = new Thread(() -> { throw failure; }, "okhttp-dispatch-1");
            // Even inside the private group, other thread kinds retain normal default handling.
            Thread otherKind = new Thread(group, () -> { throw failure; }, "unrelated-worker");
            for (Thread thread : java.util.List.of(owned, unrelated, otherKind)) {
                thread.start(); thread.join(5000); assertThat(thread.isAlive()).isFalse();
            }
            assertThat(handled).containsExactly(unrelated, otherKind);
            assertThat(failures).containsExactly(failure, failure);
            assertThat(metrics.errors.count()).isEqualTo(1);
            assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameAs(sentinel);
        } finally { Thread.setDefaultUncaughtExceptionHandler(previous); }
    }

    @Test
    void constructionFailureIsFixedTextWithoutRawCauseAndInterruptionIsRestored() {
        var metrics = new BusinessLogsMetrics(new SimpleMeterRegistry());
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> BusinessLogsExporterBoundary.build(() -> {
                throw new IllegalArgumentException("SYNTHETIC-1009-CONSTRUCTION-PLACEHOLDER");
            }, metrics)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Business Logs exporter initialization failed").hasNoCause();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
    }
}
