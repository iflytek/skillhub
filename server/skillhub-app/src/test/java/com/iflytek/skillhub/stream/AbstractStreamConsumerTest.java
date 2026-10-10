package com.iflytek.skillhub.stream;

import com.iflytek.skillhub.observability.MessageObservationSupport;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import io.micrometer.observation.ObservationRegistry;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AbstractStreamConsumerTest {

    @Test
    void handleMessage_acknowledgesAfterSuccessfulProcessing() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        StreamMessageId messageId = new StreamMessageId(1, 0);
        when(stream.ack("scan-group", messageId)).thenReturn(1L);
        TestConsumer consumer = new TestConsumer(stream);

        consumer.handleMessage(messageId, Map.of("payload", "ok"));

        verify(stream).ack("scan-group", messageId);
        verify(stream).remove(messageId);
    }

    @Test
    void handleMessage_doesNotDeleteWhenAcknowledgementReturnsZero() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        StreamMessageId messageId = new StreamMessageId(10, 0);
        when(stream.ack("scan-group", messageId)).thenReturn(0L);
        TestConsumer consumer = new TestConsumer(stream);

        consumer.handleMessage(messageId, Map.of("payload", "ok"));

        verify(stream).ack("scan-group", messageId);
        verify(stream, never()).remove(messageId);
    }

    @Test
    void handleMessage_doesNotDeleteWhenAcknowledgementFails() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        StreamMessageId messageId = new StreamMessageId(11, 0);
        when(stream.ack("scan-group", messageId))
                .thenThrow(new RedisSystemException("redis unavailable", new IllegalStateException("offline")));
        TestConsumer consumer = new TestConsumer(stream);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> consumer.handleMessage(messageId, Map.of("payload", "ok")))
                .isInstanceOf(RedisSystemException.class);

        verify(stream, never()).remove(messageId);
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
    void handleMessage_deferredFailureRemainsPending() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        TestConsumer consumer = new TestConsumer(stream);
        consumer.fail = true;
        consumer.defer = true;
        StreamMessageId messageId = new StreamMessageId(20, 0);

        consumer.handleMessage(messageId, Map.of("payload", "busy"));

        verify(stream, never()).ack("scan-group", messageId);
        verify(stream, never()).remove(messageId);
        assertThat(consumer.deferred).isTrue();
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
    void handleMessage_restoresPropagatedRequestIdOnlyWhileProcessing() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        RequestIdAccessor requestIdAccessor = new RequestIdAccessor();
        TestConsumer consumer = new TestConsumer(stream, requestIdAccessor);

        consumer.handleMessage(
                new StreamMessageId(8, 0),
                Map.of(
                        "payload", "correlated",
                        MessageObservationSupport.REQUEST_ID_FIELD, "request-stream-1"
                )
        );

        assertThat(consumer.processedRequestId).isEqualTo("request-stream-1");
        assertThat(requestIdAccessor.current()).isNull();
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
        private final RequestIdAccessor requestIdAccessor;
        private boolean fail;
        private boolean defer;
        private boolean deferred;
        private String processedRequestId;

        private TestConsumer(RStream<String, String> stream) {
            this(stream, new RequestIdAccessor());
        }

        private TestConsumer(RStream<String, String> stream, RequestIdAccessor requestIdAccessor) {
            super(
                    mock(RedissonClient.class),
                    "scan-stream",
                    "scan-group",
                    true,
                    Duration.ofMinutes(2),
                    20,
                    Duration.ofSeconds(30),
                    new MessageObservationSupport(ObservationRegistry.NOOP, requestIdAccessor)
            );
            this.stream = stream;
            this.requestIdAccessor = requestIdAccessor;
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
            processedRequestId = requestIdAccessor.current();
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

        @Override
        protected boolean shouldDeferFailure(String payload, Exception error) {
            return defer;
        }

        @Override
        protected void markDeferred(String payload, Exception error) {
            deferred = true;
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
