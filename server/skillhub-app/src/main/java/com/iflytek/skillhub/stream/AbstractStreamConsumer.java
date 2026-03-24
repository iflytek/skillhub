package com.iflytek.skillhub.stream;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.redisson.api.AutoClaimResult;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractStreamConsumer<T> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private static final String FIELD_RETRY_COUNT = "retryCount";
    private static final int MAX_RETRY_COUNT = 3;
    private static final int READ_BATCH_SIZE = 10;
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(2);

    private final RedissonClient redissonClient;
    private final String streamKey;
    private final String groupName;
    private final String consumerName;
    private final boolean reclaimEnabled;
    private final Duration reclaimMinIdle;
    private final int reclaimBatchSize;
    private final Duration reclaimInterval;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private RStream<String, String> stream;
    private ExecutorService consumerExecutor;
    private ScheduledExecutorService reclaimExecutor;

    protected AbstractStreamConsumer(RedissonClient redissonClient,
                                     String streamKey,
                                     String groupName) {
        this(redissonClient, streamKey, groupName, true, Duration.ofMinutes(2), 20, Duration.ofSeconds(30));
    }

    protected AbstractStreamConsumer(RedissonClient redissonClient,
                                     String streamKey,
                                     String groupName,
                                     boolean reclaimEnabled,
                                     Duration reclaimMinIdle,
                                     int reclaimBatchSize,
                                     Duration reclaimInterval) {
        this.redissonClient = redissonClient;
        this.streamKey = streamKey;
        this.groupName = groupName;
        this.reclaimEnabled = reclaimEnabled;
        this.reclaimMinIdle = reclaimMinIdle;
        this.reclaimBatchSize = reclaimBatchSize;
        this.reclaimInterval = reclaimInterval;
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

    private void consumeLoop() {
        while (running.get()) {
            try {
                consumeAvailableMessages();
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    break;
                }
                log.error("Failed while consuming stream messages: stream={}, group={}, consumer={}",
                        streamKey, groupName, consumerName, e);
            }
        }
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
        Map<StreamMessageId, Map<String, String>> messages = stream().readGroup(
                groupName,
                consumerName,
                StreamReadGroupArgs.neverDelivered()
                        .count(READ_BATCH_SIZE)
                        .timeout(POLL_TIMEOUT)
        );
        processMessages(messages);
    }

    void reclaimPendingMessages() {
        if (!reclaimEnabled) {
            return;
        }
        StreamMessageId startId = StreamMessageId.MIN;
        while (true) {
            AutoClaimResult<String, String> result = stream().autoClaim(
                    groupName,
                    consumerName,
                    reclaimMinIdle.toMillis(),
                    TimeUnit.MILLISECONDS,
                    startId,
                    reclaimBatchSize
            );
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
        messages.forEach(this::handleMessage);
    }

    void handleMessage(StreamMessageId messageId, Map<String, String> data) {
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
            handleFailure(payload, retryCount, e);
            acknowledge(messageId);
        }
    }

    private void handleFailure(T payload, int retryCount, Exception e) {
        if (retryCount < MAX_RETRY_COUNT) {
            retryMessage(payload, retryCount + 1);
            return;
        }
        markFailed(payload, truncateError(
                taskDisplayName() + " failed (retried " + retryCount + " times): " + e.getMessage()
        ));
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
        stream().ack(groupName, messageId);
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
