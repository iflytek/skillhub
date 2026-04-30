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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
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
}
