package com.iflytek.skillhub.stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class AbstractStreamConsumerTest {

    @Test
    void onMessage_acknowledgesAfterSuccessfulProcessing() {
        StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        org.mockito.Mockito.when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        TestConsumer consumer = new TestConsumer(redisTemplate);
        MapRecord<String, String, String> message = StreamRecords.newRecord()
                .in("scan-stream")
                .withId(RecordId.of("1-0"))
                .ofMap(Map.of("payload", "ok"));

        consumer.onMessage(message);

        verify(streamOperations).acknowledge("scan-stream", "scan-group", message.getId());
    }

    @Test
    void onMessage_acknowledgesAfterRetryableFailure() {
        StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        org.mockito.Mockito.when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        TestConsumer consumer = new TestConsumer(redisTemplate);
        consumer.fail = true;
        MapRecord<String, String, String> message = StreamRecords.newRecord()
                .in("scan-stream")
                .withId(RecordId.of("2-0"))
                .ofMap(Map.of("payload", "boom"));

        consumer.onMessage(message);

        verify(streamOperations).acknowledge("scan-stream", "scan-group", message.getId());
        verify(streamOperations, times(1)).acknowledge("scan-stream", "scan-group", message.getId());
    }

    @Test
    void onMessage_reusesRedisTemplateForAcknowledgement() {
        StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        org.mockito.Mockito.when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        CountingConsumer consumer = new CountingConsumer(redisTemplate);
        MapRecord<String, String, String> first = StreamRecords.newRecord()
                .in("scan-stream")
                .withId(RecordId.of("3-0"))
                .ofMap(Map.of("payload", "one"));
        MapRecord<String, String, String> second = StreamRecords.newRecord()
                .in("scan-stream")
                .withId(RecordId.of("4-0"))
                .ofMap(Map.of("payload", "two"));

        consumer.onMessage(first);
        consumer.onMessage(second);

        org.junit.jupiter.api.Assertions.assertEquals(1, consumer.templateCreationCount.get());
    }

    private static class TestConsumer extends AbstractStreamConsumer<String> {
        private final StringRedisTemplate redisTemplate;
        private boolean fail;

        private TestConsumer(StringRedisTemplate redisTemplate) {
            super(mock(RedisConnectionFactory.class), "scan-stream", "scan-group");
            this.redisTemplate = redisTemplate;
        }

        @Override
        protected StringRedisTemplate createRedisTemplate() {
            return redisTemplate;
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
        private final AtomicInteger templateCreationCount = new AtomicInteger();

        private CountingConsumer(StringRedisTemplate redisTemplate) {
            super(redisTemplate);
        }

        @Override
        protected StringRedisTemplate createRedisTemplate() {
            templateCreationCount.incrementAndGet();
            return super.createRedisTemplate();
        }
    }
}
