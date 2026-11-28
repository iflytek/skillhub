package com.iflytek.skillhub.stream;

import com.iflytek.skillhub.observability.MessageObservationSupport;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.redisson.api.AutoClaimResult;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisSystemException;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractStreamConsumer<T> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private static final String FIELD_RETRY_COUNT = "retryCount";
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;
    private static final int DEFAULT_READ_BATCH_SIZE = 10;
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(2);

    /**
     * Bounds how often a missing consumer group (Redis NOGROUP) is recreated at runtime, so a
     * persistently failing XGROUP CREATE cannot turn the consume loop into a creation hot loop.
     */
    private static final Duration GROUP_RECOVERY_RETRY_INTERVAL = Duration.ofSeconds(30);

    /**
     * Pause between consume attempts after consecutive failures: 1s, 2s, 4s ... capped at 30s,
     * reset by the first successful poll. XREADGROUP on a missing key/group fails immediately
     * instead of blocking, so without this a Redis outage turns the loop into a hot loop.
     */
    private static final long BACKOFF_INITIAL_MILLIS = Duration.ofSeconds(1).toMillis();
    private static final long BACKOFF_MAX_MILLIS = Duration.ofSeconds(30).toMillis();

    /** The first failure of a streak is logged in full; afterwards at most one summary per interval. */
    private static final long FAILURE_SUMMARY_INTERVAL_MILLIS = Duration.ofSeconds(60).toMillis();

    private final RedissonClient redissonClient;
    private final String streamKey;
    private final String groupName;
    private final String consumerName;
    private final boolean reclaimEnabled;
    private final Duration reclaimMinIdle;
    private final int reclaimBatchSize;
    private final Duration reclaimInterval;
    private final MessageObservationSupport messageObservationSupport;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong nextGroupRecoveryAttemptMillis = new AtomicLong(0L);

    // Touched only by the consumer thread.
    private int consecutiveFailures;
    private long lastFailureLogMillis;
    private long suppressedFailureLogs;

    private RStream<String, String> stream;
    private ExecutorService consumerExecutor;
    private ScheduledExecutorService reclaimExecutor;

    protected AbstractStreamConsumer(RedissonClient redissonClient,
                                     String streamKey,
                                     String groupName,
                                     MessageObservationSupport messageObservationSupport) {
        this(
                redissonClient,
                streamKey,
                groupName,
                true,
                Duration.ofMinutes(2),
                20,
                Duration.ofSeconds(30),
                messageObservationSupport
        );
    }

    protected AbstractStreamConsumer(RedissonClient redissonClient,
                                     String streamKey,
                                     String groupName,
                                     boolean reclaimEnabled,
                                     Duration reclaimMinIdle,
                                     int reclaimBatchSize,
                                     Duration reclaimInterval,
                                     MessageObservationSupport messageObservationSupport) {
        this.redissonClient = redissonClient;
        this.streamKey = streamKey;
        this.groupName = groupName;
        this.reclaimEnabled = reclaimEnabled;
        this.reclaimMinIdle = reclaimMinIdle;
        this.reclaimBatchSize = reclaimBatchSize;
        this.reclaimInterval = reclaimInterval;
        this.messageObservationSupport = messageObservationSupport;
        this.consumerName = consumerPrefix() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @PostConstruct
    public void init() {
        if (redissonClient == null) {
            return;
        }
        this.stream = createStream();
        initializeStreamAndGroup();
        startConsumer();
        startPendingReclaimer();
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (consumerExecutor != null) {
            consumerExecutor.shutdownNow();
        }
        if (reclaimExecutor != null) {
            reclaimExecutor.shutdownNow();
        }
    }

    private void initializeStreamAndGroup() {
        try {
            stream().createGroup(StreamCreateGroupArgs.name(groupName).makeStream());
        } catch (Exception e) {
            if (!isConsumerGroupAlreadyExists(e)) {
                log.warn("Failed to create consumer group: stream={}, group={}", streamKey, groupName, e);
            }
        }
    }

    static boolean isConsumerGroupAlreadyExists(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Detects the Redis NOGROUP error on the cause chain. Only genuine Redis command failures
     * (Redisson or Spring Data Redis wrapping) carrying the server's NOGROUP error count, so a
     * business exception that merely mentions the word never triggers group recovery.
     */
    static boolean isConsumerGroupMissing(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (isRedisCommandFailure(current)
                    && current.getMessage() != null
                    && current.getMessage().contains("NOGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isRedisCommandFailure(Throwable error) {
        return error instanceof RedisException || error instanceof RedisSystemException;
    }

    /**
     * Recreates a consumer group that disappeared at runtime (for example after a cache Redis
     * restart without persistence). The stream itself is never deleted or trimmed, and an
     * existing group is never reset: BUSYGROUP from a concurrent creator is treated as
     * recovered. The group is recreated from the stream's beginning so entries stranded while
     * the group was missing are redelivered instead of skipped; business-level idempotency
     * absorbs the duplicates. Attempts are throttled so a persistently failing creation cannot
     * hot-loop XGROUP CREATE; the triggering error is always rethrown so the consume loop's
     * logging and backoff apply and the next poll picks up the recreated group.
     */
    private void recoverMissingConsumerGroup(RuntimeException trigger, String operation) {
        if (!isConsumerGroupMissing(trigger)) {
            return;
        }
        long now = currentTimeMillis();
        long nextAttempt = nextGroupRecoveryAttemptMillis.get();
        if (now < nextAttempt
                || !nextGroupRecoveryAttemptMillis.compareAndSet(nextAttempt, now + GROUP_RECOVERY_RETRY_INTERVAL.toMillis())) {
            // Another attempt is running on this or the reclaimer thread, or within the retry interval.
            return;
        }
        try {
            // "0", not StreamMessageId.MIN: MIN serializes as "-", which XRANGE accepts but
            // XGROUP CREATE rejects with "ERR Invalid stream ID".
            stream().createGroup(StreamCreateGroupArgs.name(groupName).makeStream().id(StreamMessageId.ALL));
            log.warn("Recreated missing consumer group after NOGROUP: stream={}, group={}, operation={}",
                    streamKey, groupName, operation);
        } catch (Exception e) {
            if (isConsumerGroupAlreadyExists(e)) {
                log.info("Consumer group already present again after NOGROUP: stream={}, group={}, operation={}",
                        streamKey, groupName, operation);
            } else {
                log.warn("Failed to recreate missing consumer group after NOGROUP: stream={}, group={}, operation={}",
                        streamKey, groupName, operation, e);
            }
        }
    }

    private void startConsumer() {
        running.set(true);
        consumerExecutor = Executors.newSingleThreadExecutor(threadFactory(consumerPrefix() + "-consumer"));
        consumerExecutor.submit(this::consumeLoop);
    }

    private void startPendingReclaimer() {
        if (!reclaimEnabled) {
            return;
        }
        reclaimExecutor = Executors.newSingleThreadScheduledExecutor(threadFactory(consumerPrefix() + "-reclaimer"));
        long intervalMillis = Math.max(1L, reclaimInterval.toMillis());
        reclaimExecutor.scheduleWithFixedDelay(this::safeReclaimPendingMessages,
                intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    void consumeLoop() {
        while (running.get()) {
            try {
                consumeAvailableMessages();
                onConsumeSuccess();
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    break;
                }
                long delayMillis = onConsumeFailure(e);
                try {
                    backoffSleep(delayMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void onConsumeSuccess() {
        if (consecutiveFailures > 0) {
            log.info("Resumed consuming stream messages after {} consecutive failures: stream={}, group={}, consumer={}",
                    consecutiveFailures, streamKey, groupName, consumerName);
            consecutiveFailures = 0;
            suppressedFailureLogs = 0;
        }
    }

    /**
     * Records a failed poll, logs it without flooding, and returns how long to pause before the
     * next attempt. The first failure of a streak keeps its stack trace; later ones are folded
     * into one summary line per {@link #FAILURE_SUMMARY_INTERVAL_MILLIS}.
     */
    private long onConsumeFailure(Exception error) {
        if (consecutiveFailures < Integer.MAX_VALUE) {
            consecutiveFailures++;
        }
        long now = currentTimeMillis();
        if (consecutiveFailures == 1) {
            log.error("Failed while consuming stream messages: stream={}, group={}, consumer={}",
                    streamKey, groupName, consumerName, error);
            lastFailureLogMillis = now;
            suppressedFailureLogs = 0;
        } else if (now - lastFailureLogMillis >= FAILURE_SUMMARY_INTERVAL_MILLIS) {
            log.error("Still failing while consuming stream messages: stream={}, group={}, consumer={}, "
                            + "consecutiveFailures={}, suppressed={}, lastError={}",
                    streamKey, groupName, consumerName, consecutiveFailures, suppressedFailureLogs,
                    describe(error));
            lastFailureLogMillis = now;
            suppressedFailureLogs = 0;
        } else {
            suppressedFailureLogs++;
        }
        return backoffDelayMillis(consecutiveFailures);
    }

    static long backoffDelayMillis(int consecutiveFailures) {
        if (consecutiveFailures <= 1) {
            return BACKOFF_INITIAL_MILLIS;
        }
        // 1s << 5 already exceeds the cap; clamp the shift so large counts cannot overflow.
        int shift = Math.min(consecutiveFailures - 1, 20);
        return Math.min(BACKOFF_MAX_MILLIS, BACKOFF_INITIAL_MILLIS << shift);
    }

    private static String describe(Throwable error) {
        return error.getClass().getSimpleName() + ": " + error.getMessage();
    }

    /** Clock and pause are seams so tests can drive the backoff without waiting. */
    long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    void backoffSleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private void safeReclaimPendingMessages() {
        try {
            reclaimPendingMessages();
        } catch (Exception e) {
            log.error("Failed while reclaiming pending stream messages: stream={}, group={}, consumer={}",
                    streamKey, groupName, consumerName, e);
        }
    }

    void consumeAvailableMessages() {
        Map<StreamMessageId, Map<String, String>> messages;
        try {
            messages = stream().readGroup(
                    groupName,
                    consumerName,
                    StreamReadGroupArgs.neverDelivered()
                            .count(readBatchSize())
                            .timeout(POLL_TIMEOUT)
            );
        } catch (RuntimeException e) {
            recoverMissingConsumerGroup(e, "readGroup");
            throw e;
        }
        processMessages(messages);
    }

    void reclaimPendingMessages() {
        if (!reclaimEnabled) {
            return;
        }
        StreamMessageId startId = StreamMessageId.MIN;
        while (true) {
            AutoClaimResult<String, String> result;
            try {
                result = stream().autoClaim(
                        groupName,
                        consumerName,
                        reclaimMinIdle.toMillis(),
                        TimeUnit.MILLISECONDS,
                        startId,
                        reclaimBatchSize
                );
            } catch (RuntimeException e) {
                recoverMissingConsumerGroup(e, "autoClaim");
                throw e;
            }
            if (result == null || result.getMessages() == null || result.getMessages().isEmpty()) {
                return;
            }
            processMessages(result.getMessages());
            if (result.getMessages().size() < reclaimBatchSize || result.getNextId() == null) {
                return;
            }
            startId = result.getNextId();
        }
    }

    private void processMessages(Map<StreamMessageId, Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        // Scope each entry independently because one XREADGROUP batch may contain unrelated traces.
        messages.forEach(this::handleMessage);
    }

    void handleMessage(StreamMessageId messageId, Map<String, String> data) {
        messageObservationSupport.observeProcess(
                RedisStreamMessageCarrier.MESSAGING_SYSTEM,
                streamKey,
                data,
                RedisStreamMessageCarrier.ADAPTER,
                () -> {
                    handleMessageInScope(messageId, data);
                    return null;
                }
        );
    }

    private void handleMessageInScope(StreamMessageId messageId, Map<String, String> data) {
        T payload = parsePayload(messageId.toString(), data);
        if (payload == null) {
            acknowledge(messageId);
            return;
        }

        int retryCount = parseRetryCount(data);
        try {
            markProcessing(payload);
            processBusiness(payload);
            markCompleted(payload);
            acknowledge(messageId);
        } catch (Exception e) {
            messageObservationSupport.recordCurrentError(observationError(e));
            if (shouldDeferFailure(payload, e)) {
                markDeferred(payload, e);
            } else {
                handleFailure(payload, retryCount, e);
                acknowledge(messageId);
            }
        }
    }

    private void handleFailure(T payload, int retryCount, Exception e) {
        if (shouldRetry(payload, e, retryCount)) {
            // Retry publication remains inside the current consumer scope, so the new producer
            // span and message carrier continue the original trace.
            retryMessage(payload, retryCount + 1);
            return;
        }
        markFailed(payload, truncateError(finalFailureReason(payload, e, retryCount)));
    }

    protected String finalFailureReason(T payload, Exception error, int retryCount) {
        return taskDisplayName() + " failed (retried " + retryCount + " times): " + error.getMessage();
    }

    protected int parseRetryCount(Map<String, String> data) {
        try {
            return Integer.parseInt(data.getOrDefault(FIELD_RETRY_COUNT, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    protected String truncateError(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 500 ? error.substring(0, 500) : error;
    }

    protected RStream<String, String> createStream() {
        return redissonClient.getStream(streamKey, StringCodec.INSTANCE);
    }

    protected void acknowledge(StreamMessageId messageId) {
        if (stream().ack(groupName, messageId) > 0) {
            // This stream has one consumer group. Removing acknowledged entries prevents
            // completed scan tasks from growing the Redis stream without bound.
            stream().remove(messageId);
        }
    }

    protected int readBatchSize() {
        return DEFAULT_READ_BATCH_SIZE;
    }

    protected int maxRetryCount() {
        return DEFAULT_MAX_RETRY_COUNT;
    }

    protected boolean shouldRetry(T payload, Exception error, int retryCount) {
        return retryCount < maxRetryCount();
    }

    protected boolean shouldDeferFailure(T payload, Exception error) {
        return false;
    }

    protected Exception observationError(Exception error) {
        return error;
    }

    protected void markDeferred(T payload, Exception error) {
        log.warn("Deferring {} task without acknowledging its stream entry: {}",
                taskDisplayName(), payloadIdentifier(payload), error);
    }

    protected final RStream<String, String> stream() {
        if (stream == null) {
            stream = createStream();
        }
        return stream;
    }

    protected final String consumerName() {
        return consumerName;
    }

    private ThreadFactory threadFactory(String namePrefix) {
        return runnable -> {
            Thread thread = new Thread(runnable, namePrefix + "-" + consumerName);
            thread.setDaemon(true);
            return thread;
        };
    }

    protected abstract String taskDisplayName();

    protected abstract String consumerPrefix();

    protected abstract T parsePayload(String messageId, Map<String, String> data);

    protected abstract String payloadIdentifier(T payload);

    protected abstract void markProcessing(T payload);

    protected abstract void processBusiness(T payload);

    protected abstract void markCompleted(T payload);

    protected abstract void markFailed(T payload, String error);

    protected abstract void retryMessage(T payload, int retryCount);
}
