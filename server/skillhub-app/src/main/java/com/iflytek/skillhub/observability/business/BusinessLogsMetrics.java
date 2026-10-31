package com.iflytek.skillhub.observability.business;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fixed-cardinality counters; diagnostics contain no events, exception text, URLs or credentials. */
final class BusinessLogsMetrics {
    private static final Logger log = LoggerFactory.getLogger(BusinessLogsMetrics.class);
    private final AtomicLong lastWarning = new AtomicLong();
    final Counter enqueued;
    final Counter queueFull;
    final Counter closed;
    final Counter errors;
    final Counter exportFailures;
    final Counter exported;

    BusinessLogsMetrics(MeterRegistry registry) {
        enqueued = registry.counter("skillhub.business.logs.enqueued");
        queueFull = registry.counter("skillhub.business.logs.dropped", "reason", "queue_full");
        closed = registry.counter("skillhub.business.logs.dropped", "reason", "shutdown");
        errors = registry.counter("skillhub.business.logs.errors");
        exportFailures = registry.counter("skillhub.business.logs.export.failures");
        exported = registry.counter("skillhub.business.logs.exported");
    }

    void error() {
        errors.increment();
        warn();
    }

    void warn() {
        long now = System.nanoTime();
        long last = lastWarning.get();
        if ((last == 0 || now - last >= java.util.concurrent.TimeUnit.MINUTES.toNanos(1))
                && lastWarning.compareAndSet(last, now)) {
            log.warn("Business Logs telemetry failed; inspect skillhub.business.logs metrics and receiver health");
        }
    }
}
