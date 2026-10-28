package com.iflytek.skillhub.observability.business;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Startup-only operator settings. Deliberately no toString: headers contain secrets. */
@ConfigurationProperties("skillhub.observability.business-logs")
public class BusinessLogsProperties {
    private boolean enabled;
    private String endpoint = "";
    private String headers = "";
    private Duration timeout = Duration.ofSeconds(3);
    private String compression = "gzip";
    private int maxQueueSize = 2048;
    private int maxExportBatchSize = 256;
    private Duration scheduleDelay = Duration.ofSeconds(1);
    private Duration shutdownTimeout = Duration.ofSeconds(5);
    private int retryMaxAttempts = 3;
    private Duration retryInitialBackoff = Duration.ofMillis(200);
    private Duration retryMaxBackoff = Duration.ofSeconds(1);
    private Set<String> eventAllowlist = BusinessEventMapper.EVENT_NAMES;
    private Set<Long> namespaceAllowlist = Set.of();

    public void validate() {
        if (!enabled) { return; }
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPath() == null || uri.getPath().isBlank() || "/".equals(uri.getPath())
                    || host.startsWith("169.254.") || host.equals("100.100.100.200")
                    || host.equalsIgnoreCase("metadata.google.internal") || host.equalsIgnoreCase("metadata")
                    || host.equalsIgnoreCase("[fd00:ec2::254]") || host.equals("0.0.0.0")) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException ex) {
            throw new IllegalStateException("skillhub.observability.business-logs.endpoint requires an explicit HTTP(S) Logs URL without credentials, query, fragment or metadata target");
        }
        require(maxQueueSize >= 1 && maxQueueSize <= 65536, "max-queue-size must be 1..65536");
        require(maxExportBatchSize >= 1 && maxExportBatchSize <= Math.min(4096, maxQueueSize),
                "max-export-batch-size must be 1..min(4096,max-queue-size)");
        duration(timeout, 100, 30000, "timeout");
        duration(scheduleDelay, 10, 60000, "schedule-delay");
        duration(shutdownTimeout, 100, 30000, "shutdown-timeout");
        duration(retryInitialBackoff, 1, 5000, "retry-initial-backoff");
        duration(retryMaxBackoff, 1, 10000, "retry-max-backoff");
        require(retryMaxAttempts >= 1 && retryMaxAttempts <= 5, "retry-max-attempts must be 1..5");
        require(retryInitialBackoff.compareTo(retryMaxBackoff) <= 0, "retry backoff bounds are inconsistent");
        require(Set.of("none", "gzip").contains(compression), "compression must be none or gzip");
        require(eventAllowlist != null && BusinessEventMapper.EVENT_NAMES.containsAll(eventAllowlist), "event-allowlist contains unsupported event names");
        require(namespaceAllowlist != null && namespaceAllowlist.size() <= 1000
                && namespaceAllowlist.stream().allMatch(id -> id != null && id > 0), "namespace-allowlist requires at most 1000 positive IDs");
        parsedHeaders();
    }

    Map<String, String> parsedHeaders() {
        try {
            if (headers == null || headers.isBlank()) { return Map.of(); }
            if (headers.length() > 8192) { throw new IllegalArgumentException(); }
            Map<String, String> result = new LinkedHashMap<>();
            String[] pairs = headers.split(",", -1);
            if (pairs.length > 16) { throw new IllegalArgumentException(); }
            for (String header : pairs) {
                String[] pair = header.split("=", 2);
                if (pair.length != 2 || result.size() >= 16) { throw new IllegalArgumentException(); }
                String name = URLDecoder.decode(pair[0].trim(), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair[1].trim(), StandardCharsets.UTF_8);
                if (!name.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]{1,128}")
                        || value.chars().anyMatch(c -> c < 32 || c > 126)) { throw new IllegalArgumentException(); }
                result.put(name, value);
            }
            return Map.copyOf(result);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("business-logs.headers must contain at most 16 URL-encoded name=value pairs (8192 characters), without control characters");
        }
    }

    private static void duration(Duration value, long min, long max, String key) {
        require(value != null && value.compareTo(Duration.ofMillis(min)) >= 0
                && value.compareTo(Duration.ofMillis(max)) <= 0, key + " is outside documented bounds");
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException("skillhub.observability.business-logs: " + message); }
    }

    Duration exportBudget() {
        return timeout.plus(retryMaxBackoff).multipliedBy(retryMaxAttempts).plusSeconds(1);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getHeaders() { return headers; }
    public void setHeaders(String headers) { this.headers = headers; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }
    public String getCompression() { return compression; }
    public void setCompression(String compression) { this.compression = compression; }
    public int getMaxQueueSize() { return maxQueueSize; }
    public void setMaxQueueSize(int maxQueueSize) { this.maxQueueSize = maxQueueSize; }
    public int getMaxExportBatchSize() { return maxExportBatchSize; }
    public void setMaxExportBatchSize(int maxExportBatchSize) { this.maxExportBatchSize = maxExportBatchSize; }
    public Duration getScheduleDelay() { return scheduleDelay; }
    public void setScheduleDelay(Duration scheduleDelay) { this.scheduleDelay = scheduleDelay; }
    public Duration getShutdownTimeout() { return shutdownTimeout; }
    public void setShutdownTimeout(Duration shutdownTimeout) { this.shutdownTimeout = shutdownTimeout; }
    public int getRetryMaxAttempts() { return retryMaxAttempts; }
    public void setRetryMaxAttempts(int retryMaxAttempts) { this.retryMaxAttempts = retryMaxAttempts; }
    public Duration getRetryInitialBackoff() { return retryInitialBackoff; }
    public void setRetryInitialBackoff(Duration retryInitialBackoff) { this.retryInitialBackoff = retryInitialBackoff; }
    public Duration getRetryMaxBackoff() { return retryMaxBackoff; }
    public void setRetryMaxBackoff(Duration retryMaxBackoff) { this.retryMaxBackoff = retryMaxBackoff; }
    public Set<String> getEventAllowlist() { return eventAllowlist; }
    public void setEventAllowlist(Set<String> eventAllowlist) { this.eventAllowlist = eventAllowlist; }
    public Set<Long> getNamespaceAllowlist() { return namespaceAllowlist; }
    public void setNamespaceAllowlist(Set<Long> namespaceAllowlist) { this.namespaceAllowlist = namespaceAllowlist; }
}
