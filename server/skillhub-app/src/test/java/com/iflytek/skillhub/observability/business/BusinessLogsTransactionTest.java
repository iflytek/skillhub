package com.iflytek.skillhub.observability.business;

import com.iflytek.skillhub.domain.event.*;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.service.SkillSlugResolutionService;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.storage.ObjectMetadata;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.resources.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Testcontainers
class BusinessLogsTransactionTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withTmpFs(java.util.Map.of("/var/lib/postgresql/data", "rw"))
            .withCreateContainerCmdModifier(cmd -> cmd.withName("skillhub-1009-business-transactions"));

    private final BusinessEventSnapshot snapshot = new BusinessEventSnapshot(7L, SkillVisibility.PRIVATE,
            "approver", "user", "review", Instant.parse("2026-10-01T00:00:00Z"));

    @Test
    void realPostgresCommitEmitsAndRollbackDoesNotForEveryIncludedFact() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table if not exists analytics_transaction_probe (id int primary key)");
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var exporter = new CapturingExporter();
        var metrics = new BusinessLogsMetrics(new SimpleMeterRegistry());
        var properties = new BusinessLogsProperties();
        try (var pipeline = new BusinessLogsPipeline(properties, Resource.empty(), exporter, metrics);
             var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(BusinessEventListener.class,
                    () -> new BusinessEventListener(new BusinessEventMapper(properties), pipeline, metrics));
            context.refresh();
            var events = List.<SkillBusinessEvent>of(
                    new SkillPublishedEvent(1L, 2L, "approver", snapshot),
                    new SkillDownloadedEvent(1L, 2L, snapshot),
                    new ReviewApprovedEvent(3L, 1L, 2L, "approver", "owner", snapshot),
                    new ReviewRejectedEvent(3L, 1L, 2L, "approver", "owner", "secret", snapshot),
                    new PromotionApprovedEvent(4L, 1L, "approver", "owner", snapshot),
                    new PromotionRejectedEvent(4L, 1L, "approver", "owner", "secret", snapshot),
                    new SkillStatusChangedEvent(1L, SkillStatus.ACTIVE, SkillStatus.ARCHIVED, snapshot),
                    new SkillVisibilityChangedEvent(1L, SkillVisibility.PUBLIC, SkillVisibility.PRIVATE, snapshot),
                    new SkillVersionYankedEvent(1L, 2L, "approver", true, snapshot));
            transaction.executeWithoutResult(status -> {
                jdbc.update("insert into analytics_transaction_probe values (1) on conflict do nothing");
                events.forEach(context::publishEvent);
                assertThat(metrics.enqueued.count()).isZero();
            });
            pipeline.forceFlush().join(5, TimeUnit.SECONDS);
            assertThat(exporter.records).hasSize(9);
            assertThat(exporter.records).extracting(r -> r.getAttributes().get(AttributeKey.stringKey("skillhub.event.name")))
                    .containsExactlyInAnyOrderElementsOf(BusinessEventMapper.EVENT_NAMES);
            transaction.executeWithoutResult(status -> {
                jdbc.update("insert into analytics_transaction_probe values (2)");
                events.forEach(context::publishEvent);
                status.setRollbackOnly();
            });
            pipeline.forceFlush().join(5, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("select count(*) from analytics_transaction_probe where id=2", Integer.class)).isZero();
            assertThat(exporter.records).hasSize(9);
            context.publishEvent(new SkillDownloadedEvent(1L, 2L, snapshot));
            pipeline.forceFlush().join(5, TimeUnit.SECONDS);
            assertThat(exporter.records).hasSize(10);
            assertThat(exporter.records.getLast().getTimestampEpochNanos()).isEqualTo(snapshot.occurredAt().getEpochSecond() * 1_000_000_000L);
            System.out.println("BUSINESS_LOGS postgres: 9 commit facts, 0 rollback facts, 1 nontransactional fact");
        }
    }

    @Test
    void nestedSavepointRollbackIsExplicitlyOutsideSuppressionContract() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table nested_analytics_probe (id int primary key)");
        var manager = new DataSourceTransactionManager(dataSource);
        var outer = new TransactionTemplate(manager);
        var nested = new TransactionTemplate(manager);
        nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        var exporter = new CapturingExporter();
        var metrics = new BusinessLogsMetrics(new SimpleMeterRegistry());
        var properties = new BusinessLogsProperties();
        try (var pipeline = new BusinessLogsPipeline(properties, Resource.empty(), exporter, metrics)) {
            var listener = new BusinessEventListener(new BusinessEventMapper(properties), pipeline, metrics);
            outer.executeWithoutResult(status -> {
                nested.executeWithoutResult(savepoint -> {
                    assertThat(savepoint.hasSavepoint()).isTrue();
                    jdbc.update("insert into nested_analytics_probe values (1)");
                    listener.onEvent(new SkillPublishedEvent(1L, 2L, "approver", snapshot));
                    savepoint.setRollbackOnly();
                });
                assertThat(jdbc.queryForObject("select count(*) from nested_analytics_probe", Integer.class)).isZero();
                assertThat(metrics.enqueued.count()).isZero();
            });
            pipeline.forceFlush().join(5, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("select count(*) from nested_analytics_probe", Integer.class)).isZero();
            // Synchronization follows the physical transaction, not the rolled-back savepoint.
            assertThat(exporter.records).hasSize(1);
        }
    }

    @Test
    void requiresNewCommitExportsEvenWhenOuterTransactionRollsBack() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table requires_new_analytics_probe (id int primary key)");
        var manager = new DataSourceTransactionManager(dataSource);
        var outer = new TransactionTemplate(manager);
        var independent = new TransactionTemplate(manager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var exporter = new CapturingExporter();
        var metrics = new BusinessLogsMetrics(new SimpleMeterRegistry());
        var properties = new BusinessLogsProperties();
        try (var pipeline = new BusinessLogsPipeline(properties, Resource.empty(), exporter, metrics)) {
            var listener = new BusinessEventListener(new BusinessEventMapper(properties), pipeline, metrics);
            outer.executeWithoutResult(status -> {
                jdbc.update("insert into requires_new_analytics_probe values (1)");
                listener.onEvent(new SkillPublishedEvent(1L, 2L, "approver", snapshot));
                independent.executeWithoutResult(inner -> {
                    jdbc.update("insert into requires_new_analytics_probe values (2)");
                    listener.onEvent(new SkillPublishedEvent(3L, 4L, "approver", snapshot));
                });
                assertThat(metrics.enqueued.count()).isEqualTo(1);
                status.setRollbackOnly();
            });
            pipeline.forceFlush().join(5, TimeUnit.SECONDS);
            assertThat(jdbc.queryForList("select id from requires_new_analytics_probe", Integer.class)).containsExactly(2);
            assertThat(exporter.records).hasSize(1);
            assertThat(exporter.records.getFirst().getAttributes().get(AttributeKey.longKey("skillhub.skill.id"))).isEqualTo(3L);
        }
    }

    @Test
    void transactionDocumentationStatesSavepointExclusionAndIndependentCommit() throws Exception {
        for (String path : List.of("docs/business-event-logs.md",
                "openspec/changes/add-otlp-business-event-logs/design.md",
                "openspec/changes/add-otlp-business-event-logs/specs/business-event-logs/spec.md",
                "openspec/changes/add-otlp-business-event-logs/acceptance.md")) {
            assertThat(java.nio.file.Files.readString(java.nio.file.Path.of("../..", path)))
                    .as(path).contains("PROPAGATION_NESTED", "REQUIRES_NEW");
        }
    }

    @Test
    void unsampledSpanCapturedBeforeCommitAndNoMdcOrBaggageExported() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var exporter = new CapturingExporter();
        var metrics = new BusinessLogsMetrics(new SimpleMeterRegistry());
        var properties = new BusinessLogsProperties();
        var spanContext = SpanContext.create("12345678901234567890123456789012", "1234567890123456", TraceFlags.getDefault(), TraceState.getDefault());
        try (var pipeline = new BusinessLogsPipeline(properties, Resource.empty(), exporter, metrics)) {
            var listener = new BusinessEventListener(new BusinessEventMapper(properties), pipeline, metrics);
            transaction.executeWithoutResult(status -> {
                try (var mdc = MDC.putCloseable("sensitive.mdc", "SYNTHETIC-1009-MDC-SECRET");
                     var baggage = Baggage.builder().put("sensitive.baggage", "SYNTHETIC-1009-BAGGAGE-SECRET").build().makeCurrent();
                     var ignored = Span.wrap(spanContext).makeCurrent()) {
                    assertThat(MDC.get("sensitive.mdc")).isEqualTo("SYNTHETIC-1009-MDC-SECRET");
                    assertThat(Baggage.current().getEntryValue("sensitive.baggage")).isEqualTo("SYNTHETIC-1009-BAGGAGE-SECRET");
                    listener.onEvent(new SkillPublishedEvent(1L, 2L, "approver", snapshot));
                }
                assertThat(Span.current().getSpanContext().isValid()).isFalse();
            });
            pipeline.forceFlush().join(5, TimeUnit.SECONDS);
            assertThat(exporter.records).hasSize(1);
            assertThat(exporter.records.getFirst().getSpanContext()).isEqualTo(spanContext);
            assertThat(exporter.records.getFirst().getSpanContext().isSampled()).isFalse();
            var exported = exporter.records.getFirst();
            for (String value : List.of(exported.getAttributes().toString(), String.valueOf(exported.getBody().asString()),
                    exported.getResource().getAttributes().toString())) {
                assertThat(value).doesNotContain("sensitive.mdc", "sensitive.baggage",
                        "SYNTHETIC-1009-MDC-SECRET", "SYNTHETIC-1009-BAGGAGE-SECRET");
            }
        }
    }

    @Test
    void saturationDoesNotWaitForReceiverAndShutdownIsBounded() throws Exception {
        var properties = new BusinessLogsProperties();
        properties.setMaxQueueSize(2);
        properties.setMaxExportBatchSize(1);
        properties.setScheduleDelay(Duration.ofMillis(10));
        properties.setShutdownTimeout(Duration.ofMillis(100));
        var metrics = new BusinessLogsMetrics(new SimpleMeterRegistry());
        var entered = new CountDownLatch(1);
        var pending = new CompletableResultCode();
        var exporter = new CapturingExporter() {
            @Override public CompletableResultCode export(Collection<LogRecordData> records) { entered.countDown(); return pending; }
            @Override public CompletableResultCode shutdown() { pending.fail(); return CompletableResultCode.ofSuccess(); }
        };
        var pipeline = new BusinessLogsPipeline(properties, Resource.empty(), exporter, metrics);
        var listener = new BusinessEventListener(new BusinessEventMapper(properties), pipeline, metrics);
        try {
            listener.onEvent(new SkillPublishedEvent(1L, 2L, "approver", snapshot));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            long start = System.nanoTime();
            for (int i = 0; i < 1000; i++) { listener.onEvent(new SkillPublishedEvent(1L, 2L, "approver", snapshot)); }
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertThat(elapsed).isLessThan(1000);
            assertThat(metrics.enqueued.count()).isEqualTo(2);
            assertThat(metrics.queueFull.count()).isEqualTo(999);
            start = System.nanoTime();
            pipeline.close();
            long shutdown = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertThat(shutdown).isLessThan(1000);
            System.out.printf("BUSINESS_LOGS saturation: 1000 caller events in %d ms, drops=999, shutdown=%d ms%n", elapsed, shutdown);
        } finally { pipeline.close(); pending.fail(); }
    }

    @Test
    void mapperAndExporterExceptionsCannotEscapeBusinessThread() {
        var properties = new BusinessLogsProperties();
        var metrics = new BusinessLogsMetrics(new SimpleMeterRegistry());
        var exporter = new CapturingExporter() {
            @Override public CompletableResultCode export(Collection<LogRecordData> records) { throw new IllegalStateException("must not be logged"); }
        };
        try (var pipeline = new BusinessLogsPipeline(properties, Resource.empty(), exporter, metrics)) {
            var listener = new BusinessEventListener(new BusinessEventMapper(properties), pipeline, metrics);
            assertThatCode(() -> listener.onEvent(null)).doesNotThrowAnyException();
            assertThat(metrics.errors.count()).isEqualTo(1);
            assertThatCode(() -> listener.onEvent(new SkillPublishedEvent(1L, 2L, "approver", snapshot))).doesNotThrowAnyException();
            pipeline.forceFlush().join(5, TimeUnit.SECONDS);
            assertThat(metrics.exportFailures.count()).isEqualTo(1);
        }
    }

    @Test
    void actualNontransactionalDownloadProducerEmitsOnceAfterPermissionsAndNeverForReviewOrDeniedAccess() {
        var properties = new BusinessLogsProperties();
        var exporter = new CapturingExporter();
        var metrics = new BusinessLogsMetrics(new SimpleMeterRegistry());
        try (var pipeline = new BusinessLogsPipeline(properties, Resource.empty(), exporter, metrics);
             var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(BusinessEventListener.class,
                    () -> new BusinessEventListener(new BusinessEventMapper(properties), pipeline, metrics));
            context.refresh();
            var namespaces = mock(NamespaceRepository.class);
            var skills = mock(SkillRepository.class);
            var versions = mock(SkillVersionRepository.class);
            var storage = mock(ObjectStorageService.class);
            var skill = new Skill(7L, "never-export-this-slug", "owner", SkillVisibility.PUBLIC);
            org.springframework.test.util.ReflectionTestUtils.setField(skill, "id", 1L);
            skill.setLatestVersionId(2L);
            var version = new SkillVersion(1L, "1.0.0", "owner");
            org.springframework.test.util.ReflectionTestUtils.setField(version, "id", 2L);
            version.setStatus(SkillVersionStatus.PUBLISHED);
            version.setDownloadReady(true);
            var namespace = new Namespace("test", "Never export namespace name", "owner");
            org.springframework.test.util.ReflectionTestUtils.setField(namespace, "id", 7L);
            when(namespaces.findBySlug("test")).thenReturn(java.util.Optional.of(namespace));
            when(skills.findByNamespaceIdAndSlug(7L, skill.getSlug())).thenReturn(List.of(skill));
            when(versions.findById(2L)).thenReturn(java.util.Optional.of(version));
            when(storage.exists("packages/1/2/bundle.zip")).thenReturn(true);
            when(storage.getMetadata("packages/1/2/bundle.zip")).thenReturn(new ObjectMetadata(12, "application/zip", Instant.now()));
            when(storage.generatePresignedUrl(anyString(), any(), anyString())).thenReturn("https://secret-signed-url.invalid/?token=not-exported");
            var service = new SkillDownloadService(namespaces, skills, versions, mock(SkillVersionStatsRepository.class),
                    mock(SkillFileRepository.class), mock(SkillTagRepository.class), storage, new VisibilityChecker(),
                    context, new SkillSlugResolutionService(skills));
            var response = service.downloadLatest("test", skill.getSlug(), null, java.util.Map.of());
            assertThat(response.presignedUrl()).contains("secret-signed-url");
            pipeline.forceFlush().join(5, TimeUnit.SECONDS);
            assertThat(exporter.records).hasSize(1);
            assertThat(exporter.records.getFirst().getAttributes().get(AttributeKey.stringKey("user.id"))).isNull();
            assertThat(exporter.records.getFirst().getAttributes().toString()).doesNotContain("not-exported", skill.getSlug());
            service.downloadReviewVersion(skill, version);
            skill.setVisibility(SkillVisibility.PRIVATE);
            assertThatThrownBy(() -> service.downloadLatest("test", skill.getSlug(), null, java.util.Map.of()))
                    .isInstanceOf(DomainForbiddenException.class);
            assertThatThrownBy(() -> service.downloadLatest("test", skill.getSlug(), "stranger", java.util.Map.of()))
                    .isInstanceOf(DomainForbiddenException.class);
            pipeline.forceFlush().join(5, TimeUnit.SECONDS);
            assertThat(exporter.records).hasSize(1);
            System.out.println("BUSINESS_LOGS real download service: anonymous prepared=1, review/anonymous-private/unauthorized-private=0");
        }
    }

    static class CapturingExporter implements LogRecordExporter {
        final List<LogRecordData> records = new CopyOnWriteArrayList<>();
        @Override public CompletableResultCode export(Collection<LogRecordData> records) { this.records.addAll(records); return CompletableResultCode.ofSuccess(); }
        @Override public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
        @Override public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
    }
}
