package com.iflytek.skillhub.observability.business;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

/**
 * Private dispatcher exception boundary for the managed OTel 1.31 OkHttp sender.
 * Its DaemonThreadFactory captures the constructing thread's group. Construct only the exporter
 * here, never application work or the batch processor. No JVM default handler is changed.
 */
final class BusinessLogsExporterBoundary extends ThreadGroup {
    // OkHttp may initialize shared housekeeping threads in this group. They delegate normally and
    // must not retain a closed pipeline's registry through this otherwise inert group.
    private final WeakReference<BusinessLogsMetrics> metrics;

    private BusinessLogsExporterBoundary(BusinessLogsMetrics metrics) {
        super("skillhub-business-logs-http");
        this.metrics = new WeakReference<>(metrics);
    }

    static <T> T build(Supplier<T> factory, BusinessLogsMetrics metrics) {
        var result = new FutureTask<T>(factory::get);
        Thread initializer = new Thread(new BusinessLogsExporterBoundary(metrics), result,
                "skillhub-business-logs-http-init");
        initializer.setDaemon(true);
        initializer.start();
        boolean interrupted = false;
        try {
            // Construction is local (no receiver IO). Finish ownership transfer even on interruption,
            // rather than abandoning a newly built exporter and its executor; restore the flag below.
            while (true) {
                try { return result.get(); }
                catch (InterruptedException ex) { interrupted = true; }
                catch (ExecutionException ex) {
                    // Never attach a potentially credential-bearing SDK construction exception.
                    throw new IllegalStateException("Business Logs exporter initialization failed");
                }
            }
        } finally {
            if (interrupted) { Thread.currentThread().interrupt(); }
        }
    }

    @Override
    public void uncaughtException(Thread thread, Throwable failure) {
        if (thread.getThreadGroup() == this && thread.getName().startsWith("okhttp-dispatch-")) {
            // Do not inspect, render or forward the throwable. The export deadline owns failed-batch
            // accounting/permit release; this counts the callback error and emits only fixed text.
            BusinessLogsMetrics current = metrics.get();
            if (current != null) { current.error(); }
        } else {
            super.uncaughtException(thread, failure);
        }
    }
}
