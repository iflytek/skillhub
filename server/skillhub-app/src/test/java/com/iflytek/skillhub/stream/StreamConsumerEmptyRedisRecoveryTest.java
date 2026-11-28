package com.iflytek.skillhub.stream;

import com.iflytek.skillhub.observability.MessageObservationSupport;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Redis (not a mock) verification of the consumer-group recovery path: a Redis without
 * persistence restarts empty, so the stream key and the consumer group are both gone while a
 * consumer is already polling.
 */
@Testcontainers(disabledWithoutDocker = true)
class StreamConsumerEmptyRedisRecoveryTest {

    private static final String STREAM_KEY = "skillhub:test:requests";
    private static final String GROUP = "test-group";

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private RedissonClient redisson;
    private RecordingConsumer consumer;

    @BeforeEach
    void setUp() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        redisson = Redisson.create(config);
        redisson.getKeys().flushall();
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.shutdown();
        }
        redisson.shutdown();
    }

    @Test
    void emptyRedisAfterStartupRecreatesStreamAndGroupAndKeepsConsuming() throws Exception {
        consumer = new RecordingConsumer(redisson);
        consumer.init();
        assertThat(groupNames()).containsExactly(GROUP);

        // Redis restarts without persistence: key and group disappear under a running consumer.
        redisson.getKeys().flushall();
        assertThat(redisson.getKeys().countExists(STREAM_KEY)).isZero();

        // A producer adds work while the group is still missing; it must not be skipped.
        stream().add(StreamAddArgs.entry("payload", "queued-while-group-missing"));

        awaitProcessed("queued-while-group-missing");
        assertThat(groupNames()).containsExactly(GROUP);

        // Consumption keeps working for later entries.
        stream().add(StreamAddArgs.entry("payload", "after-recovery"));
        awaitProcessed("after-recovery");
    }

    @Test
    void emptyRedisWithNoStreamAtAllIsRecoveredByTheNextPoll() throws Exception {
        consumer = new RecordingConsumer(redisson);
        consumer.init();
        redisson.getKeys().flushall();

        // No entries yet: recovery alone must recreate stream + group so XREADGROUP stops failing.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (groupNames().isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertThat(groupNames()).containsExactly(GROUP);
    }

    private RStream<String, String> stream() {
        return redisson.getStream(STREAM_KEY, StringCodec.INSTANCE);
    }

    private List<String> groupNames() {
        if (redisson.getKeys().countExists(STREAM_KEY) == 0) {
            return List.of();
        }
        return stream().listGroups().stream().map(g -> g.getName()).toList();
    }

    private void awaitProcessed(String payload) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!consumer.processed.contains(payload) && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertThat(consumer.processed).contains(payload);
    }

    private static final class RecordingConsumer extends AbstractStreamConsumer<String> {
        private final List<String> processed = new CopyOnWriteArrayList<>();

        private RecordingConsumer(RedissonClient redissonClient) {
            super(redissonClient, STREAM_KEY, GROUP, false, Duration.ofMinutes(2), 20,
                    Duration.ofSeconds(30),
                    new MessageObservationSupport(ObservationRegistry.NOOP, new RequestIdAccessor()));
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
            processed.add(payload);
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
