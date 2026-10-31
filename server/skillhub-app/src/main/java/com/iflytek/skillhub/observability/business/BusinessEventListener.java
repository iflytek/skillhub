package com.iflytek.skillhub.observability.business;

import com.iflytek.skillhub.domain.event.SkillBusinessEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Synchronous O(1) snapshot capture; only SDK admission runs after commit, never network IO. */
public final class BusinessEventListener {
    private final BusinessEventMapper mapper;
    private final BusinessLogsPipeline pipeline;
    private final BusinessLogsMetrics metrics;

    BusinessEventListener(BusinessEventMapper mapper, BusinessLogsPipeline pipeline, BusinessLogsMetrics metrics) {
        this.mapper = mapper;
        this.pipeline = pipeline;
        this.metrics = metrics;
    }

    @EventListener
    public void onEvent(SkillBusinessEvent event) {
        try {
            mapper.map(event).ifPresent(record -> {
                SpanContext span = Span.current().getSpanContext();
                // Retain only valid standard span identity, never baggage or arbitrary request/MDC data.
                Context context = span.isValid() ? Context.root().with(Span.wrap(span)) : Context.root();
                if (TransactionSynchronizationManager.isActualTransactionActive()
                        && TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void afterCommit() { pipeline.emit(record, context); }
                    });
                } else {
                    pipeline.emit(record, context);
                }
            });
        } catch (RuntimeException ex) {
            metrics.error();
        }
    }
}
