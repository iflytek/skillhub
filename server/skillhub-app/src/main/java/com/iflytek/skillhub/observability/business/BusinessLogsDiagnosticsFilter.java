package com.iflytek.skillhub.observability.business;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

/**
 * Drops raw OTel/OkHttp diagnostics emitted on the business exporter's own network threads.
 * Those messages can echo receiver bodies or transport exception text containing credentials.
 * The managed 1.31 sender reports results on its dispatcher threads, which are all created in
 * {@link BusinessLogsExporterBoundary}; Trace and other exporters share the same logger names but
 * run on other threads, so their diagnostics keep the logging configuration's normal behavior.
 */
public final class BusinessLogsDiagnosticsFilter extends TurboFilter {
    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params,
                              Throwable throwable) {
        if (logger == null) { return FilterReply.NEUTRAL; }
        String name = logger.getName();
        if ((name.startsWith("io.opentelemetry.") || name.startsWith("okhttp3."))
                && Thread.currentThread().getThreadGroup() instanceof BusinessLogsExporterBoundary) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}
