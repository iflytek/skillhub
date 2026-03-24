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

    private static class TestConsumer extends AbstractStreamConsumer<String> {
        private final RStream<String, String> stream;
        private boolean fail;

        private TestConsumer(RStream<String, String> stream) {
            super(mock(RedissonClient.class), "scan-stream", "scan-group", true, Duration.ofMinutes(2), 20, Duration.ofSeconds(30));
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
        }

        @Override
        protected void retryMessage(String payload, int retryCount) {
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
}
