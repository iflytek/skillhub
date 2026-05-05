package com.iflytek.skillhub.stream;

import io.lettuce.core.RedisBusyException;
import org.junit.jupiter.api.Test;
import org.redisson.api.AutoClaimResult;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.springframework.data.redis.RedisSystemException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractStreamConsumerTest {

    @Test
    void handleMessage_acknowledgesAfterSuccessfulProcessing() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        TestConsumer consumer = new TestConsumer(stream);
        StreamMessageId messageId = new StreamMessageId(1, 0);

        consumer.handleMessage(messageId, Map.of("payload", "ok"));

        verify(stream).ack("scan-group", messageId);
    }

    @Test
    void handleMessage_acknowledgesAfterRetryableFailure() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        TestConsumer consumer = new TestConsumer(stream);
        consumer.fail = true;
        StreamMessageId messageId = new StreamMessageId(2, 0);

        consumer.handleMessage(messageId, Map.of("payload", "boom"));

        verify(stream).ack("scan-group", messageId);
        verify(stream, times(1)).ack("scan-group", messageId);
    }

    @Test
    void consumeAvailableMessages_processesNeverDeliveredMessages() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        TestConsumer consumer = new TestConsumer(stream);
        StreamMessageId first = new StreamMessageId(3, 0);
        StreamMessageId second = new StreamMessageId(4, 0);
        Map<StreamMessageId, Map<String, String>> messages = new LinkedHashMap<>();
        messages.put(first, Map.of("payload", "one"));
        messages.put(second, Map.of("payload", "two"));
        when(stream.readGroup(eq("scan-group"), anyString(), org.mockito.ArgumentMatchers.<StreamReadGroupArgs>any()))
                .thenReturn(messages);

        consumer.consumeAvailableMessages();

        verify(stream).ack("scan-group", first);
        verify(stream).ack("scan-group", second);
    }

    @Test
    void reclaimPendingMessages_autoClaimsAndProcessesMessages() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        TestConsumer consumer = new TestConsumer(stream);
        StreamMessageId reclaimedId = new StreamMessageId(5, 0);
        when(stream.autoClaim(eq("scan-group"), anyString(), anyLong(), eq(java.util.concurrent.TimeUnit.MILLISECONDS),
                eq(StreamMessageId.MIN), anyInt()))
                .thenReturn(new AutoClaimResult<>(
                        StreamMessageId.MAX,
                        Map.of(reclaimedId, Map.of("payload", "reclaimed")),
                        java.util.List.of()
                ));

        consumer.reclaimPendingMessages();

        verify(stream).ack("scan-group", reclaimedId);
    }

    @Test
    void handleMessage_reusesStreamInstanceForAcknowledgement() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        CountingConsumer consumer = new CountingConsumer(stream);

        consumer.handleMessage(new StreamMessageId(6, 0), Map.of("payload", "one"));
        consumer.handleMessage(new StreamMessageId(7, 0), Map.of("payload", "two"));

        assertThat(consumer.streamCreationCount.get()).isEqualTo(1);
    }

    @Test
    void detectsBusyGroupWhenWrappedInRedisSystemException() {
        RedisSystemException wrapped = new RedisSystemException(
                "Error in execution",
                new RedisBusyException("BUSYGROUP Consumer Group name already exists")
        );

        assertThat(AbstractStreamConsumer.isConsumerGroupAlreadyExists(wrapped)).isTrue();
    }

    @Test
    void handleMessage_acknowledgesNullPayloadWithoutProcessing() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        NullPayloadConsumer consumer = new NullPayloadConsumer(stream);
        StreamMessageId messageId = new StreamMessageId(8, 0);

        consumer.handleMessage(messageId, Map.of());

        verify(stream).ack("scan-group", messageId);
        assertThat(consumer.processed).isFalse();
    }

    @Test
    void handleMessage_marksFailedAfterMaxRetries() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        TestConsumer consumer = new TestConsumer(stream);
        consumer.fail = true;

        consumer.handleMessage(new StreamMessageId(9, 0), Map.of("payload", "boom", "retryCount", "3"));

        assertThat(consumer.failedError).contains("Test failed (retried 3 times): boom");
        assertThat(consumer.retryCount).isZero();
    }

    @Test
    void reclaimPendingMessages_returnsImmediatelyWhenDisabled() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        TestConsumer consumer = new TestConsumer(stream, false);

        consumer.reclaimPendingMessages();

        verify(stream, times(0)).autoClaim(anyString(), anyString(), anyLong(), eq(java.util.concurrent.TimeUnit.MILLISECONDS), any(), anyInt());
    }

    @Test
    void parseRetryCount_returnsZeroForInvalidValue() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        TestConsumer consumer = new TestConsumer(stream);

        assertThat(consumer.parseRetryCount(Map.of("retryCount", "nope"))).isZero();
    }

    @Test
    void truncateError_limitsLengthToFiveHundredCharacters() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        TestConsumer consumer = new TestConsumer(stream);

        String truncated = consumer.truncateError("x".repeat(600));

        assertThat(truncated).hasSize(500);
    }

    @Test
    void truncateError_returnsNull_whenInputIsNull() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        TestConsumer consumer = new TestConsumer(stream);

        assertThat(consumer.truncateError(null)).isNull();
    }

    @Test
    void parseRetryCount_returnsZero_whenFieldMissing() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        TestConsumer consumer = new TestConsumer(stream);

        assertThat(consumer.parseRetryCount(Map.of())).isZero();
    }

    @Test
    void isConsumerGroupAlreadyExists_returnsFalse_whenMessageIsNull() {
        Exception ex = new RuntimeException();

        assertThat(AbstractStreamConsumer.isConsumerGroupAlreadyExists(ex)).isFalse();
    }

    @Test
    void isConsumerGroupAlreadyExists_returnsFalse_whenMessageDoesNotContainBusyGroup() {
        Exception ex = new RuntimeException("some other error");

        assertThat(AbstractStreamConsumer.isConsumerGroupAlreadyExists(ex)).isFalse();
    }

    @Test
    void init_doesNothing_whenRedissonClientIsNull() {
        NullRedissonConsumer consumer = new NullRedissonConsumer();

        consumer.init();

        assertThat(consumer.consumerName()).isNotNull();
    }

    @Test
    void init_createsStreamGroupAndStartsExecutors() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        RedissonClient redisson = mock(RedissonClient.class);
        doReturn(stream).when(redisson).getStream(anyString(), any());
        InitConsumer consumer = new InitConsumer(redisson, stream);

        consumer.init();

        verify(stream).createGroup(any());
        consumer.shutdown();
    }

    @Test
    void initializeStreamAndGroup_logsWarning_whenNonBusyGroupError() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        doThrow(new RuntimeException("Connection refused")).when(stream).createGroup(any());
        RedissonClient redisson = mock(RedissonClient.class);
        doReturn(stream).when(redisson).getStream(anyString(), any());
        InitConsumer consumer = new InitConsumer(redisson, stream);

        consumer.init();

        consumer.shutdown();
    }

    @Test
    void shutdown_setsRunningToFalseAndShutsDownExecutors() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        RedissonClient redisson = mock(RedissonClient.class);
        doReturn(stream).when(redisson).getStream(anyString(), any());
        InitConsumer consumer = new InitConsumer(redisson, stream);
        consumer.init();

        consumer.shutdown();
    }

    @Test
    void shutdown_handlesNullExecutors() {
        NullRedissonConsumer consumer = new NullRedissonConsumer();

        consumer.shutdown();
    }

    @Test
    void init_swallowsBusyGroupErrors() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        doThrow(new RuntimeException("BUSYGROUP Consumer Group name already exists")).when(stream).createGroup(any());
        RedissonClient redisson = mock(RedissonClient.class);
        doReturn(stream).when(redisson).getStream(anyString(), any());
        InitConsumer consumer = new InitConsumer(redisson, stream);

        consumer.init();

        consumer.shutdown();
    }

    @Test
    void init_skipsReclaimerWhenDisabled() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        RedissonClient redisson = mock(RedissonClient.class);
        doReturn(stream).when(redisson).getStream(anyString(), any());
        DisabledReclaimInitConsumer consumer = new DisabledReclaimInitConsumer(redisson, stream);

        consumer.init();

        verify(stream, times(0)).autoClaim(anyString(), anyString(), anyLong(), eq(TimeUnit.MILLISECONDS), any(), anyInt());
        consumer.shutdown();
    }

    @Test
    void reclaimPendingMessages_returnsEarly_whenResultIsNull() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        when(stream.autoClaim(anyString(), anyString(), anyLong(), eq(java.util.concurrent.TimeUnit.MILLISECONDS),
                eq(StreamMessageId.MIN), anyInt())).thenReturn(null);
        TestConsumer consumer = new TestConsumer(stream);

        consumer.reclaimPendingMessages();

        verify(stream, times(0)).ack(anyString(), any(StreamMessageId.class));
    }

    @Test
    void reclaimPendingMessages_returnsEarly_whenMessagesAreNull() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        AutoClaimResult<String, String> result = mock(AutoClaimResult.class);
        when(result.getMessages()).thenReturn(null);
        when(stream.autoClaim(anyString(), anyString(), anyLong(), eq(java.util.concurrent.TimeUnit.MILLISECONDS),
                eq(StreamMessageId.MIN), anyInt())).thenReturn(result);
        TestConsumer consumer = new TestConsumer(stream);

        consumer.reclaimPendingMessages();

        verify(stream, times(0)).ack(anyString(), any(StreamMessageId.class));
    }

    @Test
    void reclaimPendingMessages_returnsEarly_whenMessagesAreEmpty() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        AutoClaimResult<String, String> result = new AutoClaimResult<>(
                StreamMessageId.MAX, java.util.Map.of(), java.util.List.of()
        );
        when(stream.autoClaim(anyString(), anyString(), anyLong(), eq(java.util.concurrent.TimeUnit.MILLISECONDS),
                eq(StreamMessageId.MIN), anyInt())).thenReturn(result);
        TestConsumer consumer = new TestConsumer(stream);

        consumer.reclaimPendingMessages();

        verify(stream, times(0)).ack(anyString(), any(StreamMessageId.class));
    }

    @Test
    void reclaimPendingMessages_stopsWhenMessageCountLessThanBatchSize() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        StreamMessageId msgId = new StreamMessageId(1, 0);
        AutoClaimResult<String, String> result = new AutoClaimResult<>(
                StreamMessageId.MAX,
                java.util.Map.of(msgId, java.util.Map.of("payload", "a")),
                java.util.List.of()
        );
        when(stream.autoClaim(anyString(), anyString(), anyLong(), eq(java.util.concurrent.TimeUnit.MILLISECONDS),
                eq(StreamMessageId.MIN), anyInt())).thenReturn(result);
        TestConsumer consumer = new TestConsumer(stream);

        consumer.reclaimPendingMessages();

        verify(stream).ack("scan-group", msgId);
    }

    @Test
    void reclaimPendingMessages_stopsWhenNextIdIsNull() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        StreamMessageId msgId = new StreamMessageId(1, 0);
        AutoClaimResult<String, String> result = mock(AutoClaimResult.class);
        Map<StreamMessageId, Map<String, String>> messages = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) {
            messages.put(new StreamMessageId(i + 1, 0), java.util.Map.of("payload", "a-" + i));
        }
        messages.put(msgId, java.util.Map.of("payload", "a"));
        when(result.getMessages()).thenReturn(messages);
        when(result.getNextId()).thenReturn(null);
        when(stream.autoClaim(anyString(), anyString(), anyLong(), eq(java.util.concurrent.TimeUnit.MILLISECONDS),
                eq(StreamMessageId.MIN), anyInt())).thenReturn(result);
        TestConsumer consumer = new TestConsumer(stream);

        consumer.reclaimPendingMessages();

        verify(stream).ack("scan-group", msgId);
    }

    @Test
    void reclaimPendingMessages_continuesFromNextId() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        StreamMessageId firstId = new StreamMessageId(1, 0);
        StreamMessageId secondId = new StreamMessageId(2, 0);
        StreamMessageId nextId = new StreamMessageId(99, 0);
        AutoClaimResult<String, String> firstResult = mock(AutoClaimResult.class);
        Map<StreamMessageId, Map<String, String>> firstMessages = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) {
            firstMessages.put(new StreamMessageId(i + 10, 0), Map.of("payload", "batch-" + i));
        }
        firstMessages.put(firstId, Map.of("payload", "first"));
        AutoClaimResult<String, String> secondResult = new AutoClaimResult<>(
                StreamMessageId.MAX,
                Map.of(secondId, Map.of("payload", "second")),
                java.util.List.of()
        );
        when(firstResult.getMessages()).thenReturn(firstMessages);
        when(firstResult.getNextId()).thenReturn(nextId);
        when(stream.autoClaim(anyString(), anyString(), anyLong(), eq(java.util.concurrent.TimeUnit.MILLISECONDS),
                eq(StreamMessageId.MIN), anyInt())).thenReturn(firstResult);
        when(stream.autoClaim(anyString(), anyString(), anyLong(), eq(java.util.concurrent.TimeUnit.MILLISECONDS),
                eq(nextId), anyInt())).thenReturn(secondResult);
        TestConsumer consumer = new TestConsumer(stream);

        consumer.reclaimPendingMessages();

        verify(stream).ack("scan-group", firstId);
        verify(stream).ack("scan-group", secondId);
    }

    @Test
    void processMessages_returnsEarlyForNullMessages() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        TestConsumer consumer = new TestConsumer(stream);

        consumer.invokeProcessMessages(null);

        verify(stream, times(0)).ack(anyString(), any(StreamMessageId.class));
    }

    @Test
    void consumeLoop_logsAndContinuesAfterExceptionUntilStopped() {
        ThrowingLoopConsumer consumer = new ThrowingLoopConsumer();

        consumer.invokeConsumeLoop();

        assertThat(consumer.consumeAttempts.get()).isEqualTo(1);
    }

    @Test
    void consumeLoop_stopsWhenThreadIsInterrupted() {
        InterruptingLoopConsumer consumer = new InterruptingLoopConsumer();

        consumer.invokeConsumeLoop();

        assertThat(consumer.consumeAttempts.get()).isEqualTo(1);
        Thread.interrupted();
    }

    @Test
    void stream_createsStream_whenNull() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        CountingConsumer consumer = new CountingConsumer(stream);

        consumer.stream();

        assertThat(consumer.streamCreationCount.get()).isEqualTo(1);
    }

    @Test
    void stream_usesDefaultCreateStreamImplementation() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        RedissonClient redisson = mock(RedissonClient.class);
        doReturn(stream).when(redisson).getStream("default-stream", org.redisson.client.codec.StringCodec.INSTANCE);
        DefaultCreateStreamConsumer consumer = new DefaultCreateStreamConsumer(redisson);

        assertThat(consumer.stream()).isSameAs(stream);
    }

    @Test
    void consumerName_returnsNonNullValue() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        TestConsumer consumer = new TestConsumer(stream);

        assertThat(consumer.consumerName()).isNotNull();
        assertThat(consumer.consumerName()).startsWith("test-");
    }

    @Test
    void handleFailure_retriesMessage_whenBelowMaxRetries() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        TestConsumer consumer = new TestConsumer(stream);
        consumer.fail = true;

        consumer.handleMessage(new StreamMessageId(10, 0), Map.of("payload", "boom", "retryCount", "1"));

        assertThat(consumer.retryCount).isEqualTo(2);
        assertThat(consumer.failedError).isNull();
    }

    @Test
    void safeReclaimPendingMessages_catchesException() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        when(stream.autoClaim(anyString(), anyString(), anyLong(), eq(java.util.concurrent.TimeUnit.MILLISECONDS),
                eq(StreamMessageId.MIN), anyInt())).thenThrow(new RuntimeException("boom"));
        TestConsumer consumer = new TestConsumer(stream);

        consumer.invokeSafeReclaimPendingMessages();
    }

    @Test
    void safeReclaimPendingMessages_catchesExceptionFromOverride() {
        ThrowingReclaimConsumer consumer = new ThrowingReclaimConsumer();

        consumer.invokeSafeReclaimPendingMessages();

        assertThat(consumer.reclaimAttempts.get()).isEqualTo(1);
    }

    @Test
    void safeReclaimPendingMessages_allowsSuccessfulReclaim() {
        NoOpReclaimConsumer consumer = new NoOpReclaimConsumer();

        consumer.invokeSafeReclaimPendingMessages();

        assertThat(consumer.reclaimAttempts.get()).isEqualTo(1);
    }

    @Test
    void safeReclaimPendingMessages_runsFromScheduledExecutor() throws Exception {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        RedissonClient redisson = mock(RedissonClient.class);
        doReturn(stream).when(redisson).getStream(anyString(), any());
        ScheduledThrowingReclaimConsumer consumer = new ScheduledThrowingReclaimConsumer(redisson, stream);

        consumer.init();
        assertThat(consumer.awaitAttempt()).isTrue();

        consumer.shutdown();
        assertThat(consumer.reclaimAttempts.get()).isGreaterThan(0);
    }

    @Test
    void threadFactory_createsDaemonThread() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        RedissonClient redisson = mock(RedissonClient.class);
        doReturn(stream).when(redisson).getStream(anyString(), any());
        InitConsumer consumer = new InitConsumer(redisson, stream);
        consumer.init();

        consumer.shutdown();
    }

    private static class TestConsumer extends AbstractStreamConsumer<String> {
        private final RStream<String, String> stream;
        private boolean fail;
        private String failedError;
        private int retryCount;

        private TestConsumer(RStream<String, String> stream) {
            this(stream, true);
        }

        private TestConsumer(RStream<String, String> stream, boolean reclaimEnabled) {
            super(mock(RedissonClient.class), "scan-stream", "scan-group", reclaimEnabled, Duration.ofMinutes(2), 20, Duration.ofSeconds(30));
            this.stream = stream;
        }

        @Override
        protected RStream<String, String> createStream() {
            return stream;
        }

        @Override
        protected String taskDisplayName() {
            return "Test";
        }

        @Override
        protected String consumerPrefix() {
            return "test";
        }

        @Override
        protected String parsePayload(String messageId, Map<String, String> data) {
            return data.get("payload");
        }

        @Override
        protected String payloadIdentifier(String payload) {
            return payload;
        }

        @Override
        protected void markProcessing(String payload) {
        }

        @Override
        protected void processBusiness(String payload) {
            if (fail) {
                throw new IllegalStateException("boom");
            }
        }

        @Override
        protected void markCompleted(String payload) {
        }

        @Override
        protected void markFailed(String payload, String error) {
            this.failedError = error;
        }

        @Override
        protected void retryMessage(String payload, int retryCount) {
            this.retryCount = retryCount;
        }

        private void invokeSafeReclaimPendingMessages() {
            try {
                java.lang.reflect.Method method = AbstractStreamConsumer.class
                        .getDeclaredMethod("safeReclaimPendingMessages");
                method.setAccessible(true);
                method.invoke(this);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void invokeProcessMessages(Map<StreamMessageId, Map<String, String>> messages) {
            try {
                java.lang.reflect.Method method = AbstractStreamConsumer.class
                        .getDeclaredMethod("processMessages", Map.class);
                method.setAccessible(true);
                method.invoke(this, messages);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final class CountingConsumer extends TestConsumer {
        private final AtomicInteger streamCreationCount = new AtomicInteger();

        private CountingConsumer(RStream<String, String> stream) {
            super(stream);
        }

        @Override
        protected RStream<String, String> createStream() {
            streamCreationCount.incrementAndGet();
            return super.createStream();
        }
    }

    private static final class NullPayloadConsumer extends TestConsumer {
        private boolean processed;

        private NullPayloadConsumer(RStream<String, String> stream) {
            super(stream);
        }

        @Override
        protected String parsePayload(String messageId, Map<String, String> data) {
            return null;
        }

        @Override
        protected void processBusiness(String payload) {
            processed = true;
        }
    }

    private static final class NullRedissonConsumer extends AbstractStreamConsumer<String> {
        private NullRedissonConsumer() {
            super(null, "null-stream", "null-group");
        }

        @Override
        protected String taskDisplayName() {
            return "NullTest";
        }

        @Override
        protected String consumerPrefix() {
            return "null-test";
        }

        @Override
        protected String parsePayload(String messageId, Map<String, String> data) {
            return data.get("payload");
        }

        @Override
        protected String payloadIdentifier(String payload) {
            return payload;
        }

        @Override
        protected void markProcessing(String payload) {
        }

        @Override
        protected void processBusiness(String payload) {
        }

        @Override
        protected void markCompleted(String payload) {
        }

        @Override
        protected void markFailed(String payload, String error) {
        }

        @Override
        protected void retryMessage(String payload, int retryCount) {
        }
    }

    private static final class InitConsumer extends AbstractStreamConsumer<String> {
        private final RStream<String, String> stream;

        private InitConsumer(RedissonClient redissonClient, RStream<String, String> stream) {
            super(redissonClient, "init-stream", "init-group");
            this.stream = stream;
        }

        @Override
        protected RStream<String, String> createStream() {
            return stream;
        }

        @Override
        protected String taskDisplayName() {
            return "InitTest";
        }

        @Override
        protected String consumerPrefix() {
            return "init-test";
        }

        @Override
        protected String parsePayload(String messageId, Map<String, String> data) {
            return data.get("payload");
        }

        @Override
        protected String payloadIdentifier(String payload) {
            return payload;
        }

        @Override
        protected void markProcessing(String payload) {
        }

        @Override
        protected void processBusiness(String payload) {
        }

        @Override
        protected void markCompleted(String payload) {
        }

        @Override
        protected void markFailed(String payload, String error) {
        }

        @Override
        protected void retryMessage(String payload, int retryCount) {
        }
    }

    private static final class DisabledReclaimInitConsumer extends AbstractStreamConsumer<String> {
        private final RStream<String, String> stream;

        private DisabledReclaimInitConsumer(RedissonClient redissonClient, RStream<String, String> stream) {
            super(redissonClient, "disabled-stream", "disabled-group", false, Duration.ofMinutes(2), 20, Duration.ofSeconds(30));
            this.stream = stream;
        }

        @Override
        protected RStream<String, String> createStream() {
            return stream;
        }

        @Override
        protected String taskDisplayName() {
            return "DisabledReclaim";
        }

        @Override
        protected String consumerPrefix() {
            return "disabled-reclaim";
        }

        @Override
        protected String parsePayload(String messageId, Map<String, String> data) {
            return data.get("payload");
        }

        @Override
        protected String payloadIdentifier(String payload) {
            return payload;
        }

        @Override
        protected void markProcessing(String payload) {
        }

        @Override
        protected void processBusiness(String payload) {
        }

        @Override
        protected void markCompleted(String payload) {
        }

        @Override
        protected void markFailed(String payload, String error) {
        }

        @Override
        protected void retryMessage(String payload, int retryCount) {
        }
    }

    private static final class DefaultCreateStreamConsumer extends AbstractStreamConsumer<String> {
        private DefaultCreateStreamConsumer(RedissonClient redissonClient) {
            super(redissonClient, "default-stream", "default-group");
        }

        @Override
        protected String taskDisplayName() {
            return "DefaultCreate";
        }

        @Override
        protected String consumerPrefix() {
            return "default-create";
        }

        @Override
        protected String parsePayload(String messageId, Map<String, String> data) {
            return data.get("payload");
        }

        @Override
        protected String payloadIdentifier(String payload) {
            return payload;
        }

        @Override
        protected void markProcessing(String payload) {
        }

        @Override
        protected void processBusiness(String payload) {
        }

        @Override
        protected void markCompleted(String payload) {
        }

        @Override
        protected void markFailed(String payload, String error) {
        }

        @Override
        protected void retryMessage(String payload, int retryCount) {
        }
    }

    private static class ThrowingLoopConsumer extends AbstractStreamConsumer<String> {
        protected final AtomicInteger consumeAttempts = new AtomicInteger();

        private ThrowingLoopConsumer() {
            super(null, "throwing-stream", "throwing-group");
            setRunning(true);
        }

        @Override
        void consumeAvailableMessages() {
            consumeAttempts.incrementAndGet();
            setRunning(false);
            throw new RuntimeException("boom");
        }

        protected void invokeConsumeLoop() {
            try {
                java.lang.reflect.Method method = AbstractStreamConsumer.class.getDeclaredMethod("consumeLoop");
                method.setAccessible(true);
                method.invoke(this);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void setRunning(boolean value) {
            try {
                java.lang.reflect.Field field = AbstractStreamConsumer.class.getDeclaredField("running");
                field.setAccessible(true);
                java.util.concurrent.atomic.AtomicBoolean running = (java.util.concurrent.atomic.AtomicBoolean) field.get(this);
                running.set(value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected String taskDisplayName() {
            return "ThrowingLoop";
        }

        @Override
        protected String consumerPrefix() {
            return "throwing-loop";
        }

        @Override
        protected String parsePayload(String messageId, Map<String, String> data) {
            return data.get("payload");
        }

        @Override
        protected String payloadIdentifier(String payload) {
            return payload;
        }

        @Override
        protected void markProcessing(String payload) {
        }

        @Override
        protected void processBusiness(String payload) {
        }

        @Override
        protected void markCompleted(String payload) {
        }

        @Override
        protected void markFailed(String payload, String error) {
        }

        @Override
        protected void retryMessage(String payload, int retryCount) {
        }
    }

    private static final class InterruptingLoopConsumer extends ThrowingLoopConsumer {
        @Override
        void consumeAvailableMessages() {
            consumeAttempts.incrementAndGet();
            Thread.currentThread().interrupt();
            throw new RuntimeException("boom");
        }
    }

    private static final class ThrowingReclaimConsumer extends AbstractStreamConsumer<String> {
        private final AtomicInteger reclaimAttempts = new AtomicInteger();

        private ThrowingReclaimConsumer() {
            super(null, "reclaim-stream", "reclaim-group");
        }

        @Override
        void reclaimPendingMessages() {
            reclaimAttempts.incrementAndGet();
            throw new RuntimeException("boom");
        }

        private void invokeSafeReclaimPendingMessages() {
            try {
                java.lang.reflect.Method method = AbstractStreamConsumer.class.getDeclaredMethod("safeReclaimPendingMessages");
                method.setAccessible(true);
                method.invoke(this);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected String taskDisplayName() {
            return "ThrowingReclaim";
        }

        @Override
        protected String consumerPrefix() {
            return "throwing-reclaim";
        }

        @Override
        protected String parsePayload(String messageId, Map<String, String> data) {
            return data.get("payload");
        }

        @Override
        protected String payloadIdentifier(String payload) {
            return payload;
        }

        @Override
        protected void markProcessing(String payload) {
        }

        @Override
        protected void processBusiness(String payload) {
        }

        @Override
        protected void markCompleted(String payload) {
        }

        @Override
        protected void markFailed(String payload, String error) {
        }

        @Override
        protected void retryMessage(String payload, int retryCount) {
        }
    }

    private static final class ScheduledThrowingReclaimConsumer extends AbstractStreamConsumer<String> {
        private final RStream<String, String> stream;
        private final CountDownLatch attemptLatch = new CountDownLatch(1);
        private final AtomicInteger reclaimAttempts = new AtomicInteger();

        private ScheduledThrowingReclaimConsumer(RedissonClient redissonClient, RStream<String, String> stream) {
            super(redissonClient, "scheduled-reclaim-stream", "scheduled-reclaim-group",
                    true, Duration.ofMillis(1), 1, Duration.ofMillis(1));
            this.stream = stream;
        }

        @Override
        protected RStream<String, String> createStream() {
            return stream;
        }

        @Override
        void reclaimPendingMessages() {
            reclaimAttempts.incrementAndGet();
            attemptLatch.countDown();
            throw new RuntimeException("boom");
        }

        private boolean awaitAttempt() throws InterruptedException {
            return attemptLatch.await(1, TimeUnit.SECONDS);
        }

        @Override
        protected String taskDisplayName() {
            return "ScheduledThrowingReclaim";
        }

        @Override
        protected String consumerPrefix() {
            return "scheduled-throwing-reclaim";
        }

        @Override
        protected String parsePayload(String messageId, Map<String, String> data) {
            return data.get("payload");
        }

        @Override
        protected String payloadIdentifier(String payload) {
            return payload;
        }

        @Override
        protected void markProcessing(String payload) {
        }

        @Override
        protected void processBusiness(String payload) {
        }

        @Override
        protected void markCompleted(String payload) {
        }

        @Override
        protected void markFailed(String payload, String error) {
        }

        @Override
        protected void retryMessage(String payload, int retryCount) {
        }
    }

    private static final class NoOpReclaimConsumer extends AbstractStreamConsumer<String> {
        private final AtomicInteger reclaimAttempts = new AtomicInteger();

        private NoOpReclaimConsumer() {
            super(null, "noop-reclaim-stream", "noop-reclaim-group");
        }

        @Override
        void reclaimPendingMessages() {
            reclaimAttempts.incrementAndGet();
        }

        private void invokeSafeReclaimPendingMessages() {
            try {
                java.lang.reflect.Method method = AbstractStreamConsumer.class.getDeclaredMethod("safeReclaimPendingMessages");
                method.setAccessible(true);
                method.invoke(this);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected String taskDisplayName() {
            return "NoOpReclaim";
        }

        @Override
        protected String consumerPrefix() {
            return "noop-reclaim";
        }

        @Override
        protected String parsePayload(String messageId, Map<String, String> data) {
            return data.get("payload");
        }

        @Override
        protected String payloadIdentifier(String payload) {
            return payload;
        }

        @Override
        protected void markProcessing(String payload) {
        }

        @Override
        protected void processBusiness(String payload) {
        }

        @Override
        protected void markCompleted(String payload) {
        }

        @Override
        protected void markFailed(String payload, String error) {
        }

        @Override
        protected void retryMessage(String payload, int retryCount) {
        }
    }
}
