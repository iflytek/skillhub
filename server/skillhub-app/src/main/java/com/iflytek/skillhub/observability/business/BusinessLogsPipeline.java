package com.iflytek.skillhub.observability.business;

import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogLimits;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.resources.Resource;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dedicated SDK pipeline. Admission permits bound queued + in-flight records together, conservatively
 * below the SDK queue capacity, providing exact admission metrics without accessing SDK internals.
 * All network batching/retry remains in the standard SDK/exporter.
 */
public final class BusinessLogsPipeline implements AutoCloseable {
    private final SdkLoggerProvider provider;
    private final Logger logger;
    private final LogRecordExporter exporter;
    private final BusinessLogsMetrics metrics;
    private final Duration shutdownTimeout;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    BusinessLogsPipeline(BusinessLogsProperties properties, Resource resource,
                         LogRecordExporter exporter, BusinessLogsMetrics metrics) {
        this.exporter = exporter;
        this.metrics = metrics;
        this.shutdownTimeout = properties.getShutdownTimeout();
        Semaphore capacity = new Semaphore(properties.getMaxQueueSize());
        LogRecordExporter measured = new LogRecordExporter() {
            @Override
            public CompletableResultCode export(Collection<LogRecordData> records) {
                int count = records.size();
                try {
                    CompletableResultCode result = exporter.export(records);
                    result.whenComplete(() -> {
                        capacity.release(count);
                        if (result.isSuccess()) { metrics.exported.increment(count); }
                        else { metrics.exportFailures.increment(); metrics.warn(); }
                    });
                    return result;
                } catch (RuntimeException ex) {
                    capacity.release(count);
                    metrics.exportFailures.increment();
                    metrics.warn();
                    return CompletableResultCode.ofFailure();
                }
            }
            @Override public CompletableResultCode flush() { return exporter.flush(); }
            @Override public CompletableResultCode shutdown() { return exporter.shutdown(); }
        };
        BatchLogRecordProcessor batch = BatchLogRecordProcessor.builder(measured)
                .setMeterProvider(MeterProvider.noop())
                .setMaxQueueSize(properties.getMaxQueueSize())
                .setMaxExportBatchSize(properties.getMaxExportBatchSize())
                .setScheduleDelay(properties.getScheduleDelay())
                .setExporterTimeout(properties.exportBudget()).build();
        LogRecordProcessor admission = new LogRecordProcessor() {
            @Override
            public void onEmit(Context context, ReadWriteLogRecord record) {
                if (!accepting.get()) { metrics.closed.increment(); return; }
                if (!capacity.tryAcquire()) { metrics.queueFull.increment(); return; }
                try {
                    batch.onEmit(context, record);
                    metrics.enqueued.increment();
                } catch (RuntimeException ex) {
                    capacity.release();
                    metrics.error();
                }
            }
            @Override public CompletableResultCode forceFlush() { return batch.forceFlush(); }
            @Override public CompletableResultCode shutdown() { return batch.shutdown(); }
        };
        provider = SdkLoggerProvider.builder().setResource(resource)
                .setLogLimits(() -> LogLimits.builder().setMaxNumberOfAttributes(16)
                        .setMaxAttributeValueLength(256).build())
                .addLogRecordProcessor(admission).build();
        logger = provider.loggerBuilder("com.iflytek.skillhub.business").setInstrumentationVersion("1").build();
    }

    void emit(BusinessEventMapper.MappedRecord record, Context context) {
        try {
            if (!accepting.get()) { metrics.closed.increment(); return; }
            logger.logRecordBuilder().setTimestamp(record.timestamp()).setContext(context)
                    .setAllAttributes(record.attributes()).emit();
        } catch (RuntimeException ex) {
            metrics.error();
        }
    }

    CompletableResultCode forceFlush() { return provider.forceFlush(); }

    @Override
    public void close() {
        if (!accepting.getAndSet(false)) { return; }
        try {
            CompletableResultCode shutdown = provider.shutdown();
            shutdown.join(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!shutdown.isDone()) { exporter.shutdown(); }
        } catch (RuntimeException ex) {
            metrics.error();
            // Also cancel network work if shutdown itself unexpectedly fails.
            try { exporter.shutdown(); } catch (RuntimeException shutdownException) { metrics.error(); }
        }
    }
}
