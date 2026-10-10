package com.iflytek.skillhub.observability.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractJsonProvider;
import org.apache.skywalking.apm.toolkit.log.logback.v1.x.mdc.LogbackMDCPatternConverter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Writes only the approved correlation fields from MDC.
 */
final class CorrelationJsonProvider extends AbstractJsonProvider<ILoggingEvent> {

    private static final String REQUEST_ID_KEY = "requestId";
    private static final String TRACE_ID_KEY = "traceId";
    private static final String SPAN_ID_KEY = "spanId";
    private static final String EXTERNAL_TRACE_ID_KEY = "tid";

    private final LogbackMDCPatternConverter externalTraceIdConverter;

    CorrelationJsonProvider(boolean externalTraceIdEnabled) {
        if (externalTraceIdEnabled) {
            externalTraceIdConverter = new LogbackMDCPatternConverter();
            externalTraceIdConverter.setOptionList(List.of(EXTERNAL_TRACE_ID_KEY));
            externalTraceIdConverter.start();
        } else {
            externalTraceIdConverter = null;
        }
    }

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        Map<String, String> mdc = event.getMDCPropertyMap();
        mdc = mdc == null ? Map.of() : mdc;

        writeIfPresent(generator, "request.id", mdc.get(REQUEST_ID_KEY));
        writeIfPresent(
                generator,
                "trace.id",
                firstPresent(mdc.get(TRACE_ID_KEY), externalTraceId(event, mdc))
        );
        writeIfPresent(generator, "span.id", mdc.get(SPAN_ID_KEY));
    }

    private String externalTraceId(ILoggingEvent event, Map<String, String> mdc) {
        String traceId = mdc.get(EXTERNAL_TRACE_ID_KEY);
        if (!isPresent(traceId) && externalTraceIdConverter != null) {
            traceId = externalTraceIdConverter.convert(event);
        }
        return normalizeExternalTraceId(traceId);
    }

    private String normalizeExternalTraceId(String traceId) {
        if (!isPresent(traceId)) {
            return null;
        }
        String normalized = traceId.trim();
        if (normalized.regionMatches(true, 0, "TID:", 0, 4)) {
            normalized = normalized.substring(4).trim();
        }
        return "N/A".equalsIgnoreCase(normalized) ? null : normalized;
    }

    private String firstPresent(String preferred, String fallback) {
        return isPresent(preferred) ? preferred : fallback;
    }

    private void writeIfPresent(JsonGenerator generator, String fieldName, String value)
            throws IOException {
        if (isPresent(value)) {
            generator.writeStringField(fieldName, value);
        }
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
