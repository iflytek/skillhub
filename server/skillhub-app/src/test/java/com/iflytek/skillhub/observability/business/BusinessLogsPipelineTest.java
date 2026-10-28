package com.iflytek.skillhub.observability.business;

import com.iflytek.skillhub.domain.event.BusinessEventSnapshot;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.resources.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class BusinessLogsPipelineTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void lateCompletionAfterDeadlineCannotReleaseTwiceOrChangeOutcome(boolean lateSuccess) throws Exception {
        var properties = new BusinessLogsProperties();
        properties.setMaxQueueSize(1);
        properties.setMaxExportBatchSize(1);
        properties.setTimeout(Duration.ofMillis(100));
        properties.setRetryMaxAttempts(1);
        properties.setRetryMaxBackoff(Duration.ofMillis(1));
        var metrics = new BusinessLogsMetrics(new SimpleMeterRegistry());
        var exports = new LinkedBlockingQueue<CompletableResultCode>();
        var exporter = new LogRecordExporter() {
            @Override public CompletableResultCode export(Collection<LogRecordData> records) {
                var result = new CompletableResultCode();
                exports.add(result);
                return result;
            }
            @Override public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
            @Override public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
        };
        try (var pipeline = new BusinessLogsPipeline(properties, Resource.empty(), exporter, metrics)) {
            var snapshot = new BusinessEventSnapshot(7L, SkillVisibility.PUBLIC, "reviewer", "user", "review", Instant.now());
            var record = new BusinessEventMapper(properties).map(new SkillPublishedEvent(1L, 2L, "reviewer", snapshot)).orElseThrow();
            pipeline.emit(record, Context.root());
            var abandoned = exports.poll(5, TimeUnit.SECONDS);
            assertThat(abandoned).isNotNull();
            pipeline.forceFlush().join(5, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(metrics.exportFailures.count()).isEqualTo(1));
            pipeline.emit(record, Context.root());
            var recovering = exports.poll(5, TimeUnit.SECONDS);
            assertThat(recovering).isNotNull();
            if (lateSuccess) { abandoned.succeed(); } else { abandoned.fail(); }
            // The recovered permit is now occupied again. Late completion must not add capacity.
            pipeline.emit(record, Context.root());
            assertThat(metrics.enqueued.count()).isEqualTo(2);
            assertThat(metrics.queueFull.count()).isEqualTo(1);
            assertThat(metrics.exported.count()).isZero();
            assertThat(metrics.exportFailures.count()).isEqualTo(1);
            recovering.succeed();
            pipeline.forceFlush().join(5, TimeUnit.SECONDS);
            assertThat(metrics.exported.count()).isEqualTo(1);
            assertThat(metrics.exportFailures.count()).isEqualTo(1);
        }
    }
}
