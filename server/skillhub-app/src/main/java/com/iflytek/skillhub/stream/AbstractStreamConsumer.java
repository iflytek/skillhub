package com.iflytek.skillhub.stream;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractStreamConsumer<T> implements StreamListener<String, MapRecord<String, String, String>> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private static final String FIELD_RETRY_COUNT = "retryCount";
    private static final int MAX_RETRY_COUNT = 3;

    private final RedisConnectionFactory connectionFactory;
    private final String streamKey;
    private final String groupName;
    private final String consumerName;
    private StringRedisTemplate redisTemplate;

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;

    protected AbstractStreamConsumer(RedisConnectionFactory connectionFactory,
                                     String streamKey,
                                     String groupName) {
        this.connectionFactory = connectionFactory;
        this.streamKey = streamKey;
        this.groupName = groupName;
        this.consumerName = consumerPrefix() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @PostConstruct
    public void init() {
        if (connectionFactory == null) {
            return;
        }
        this.redisTemplate = createRedisTemplate();
        initializeStreamAndGroup();
        startConsumer();
    }

    @PreDestroy
    public void shutdown() {
        if (container != null) {
            container.stop();
        }
    }

    private void initializeStreamAndGroup() {
        try {
            StringRedisTemplate template = redisTemplate();
            if (Boolean.FALSE.equals(template.hasKey(streamKey))) {
                template.opsForStream().add(streamKey, Map.of("_init", "true"));
            }
            try {
                template.opsForStream().createGroup(streamKey, ReadOffset.from("0"), groupName);
            } catch (Exception e) {
                if (e.getMessage() == null || !e.getMessage().contains("BUSYGROUP")) {
                    log.warn("Failed to create consumer group: stream={}, group={}", streamKey, groupName, e);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Redis Stream consumer", e);
        }
    }

    private void startConsumer() {
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(2))
                        .build();

        container = StreamMessageListenerContainer.create(connectionFactory, options);
        Subscription ignored = container.receive(
                Consumer.from(groupName, consumerName),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                this
        );
        container.start();
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        T payload = parsePayload(message.getId().getValue(), message.getValue());
        if (payload == null) {
            acknowledge(message);
            return;
        }

        int retryCount = parseRetryCount(message.getValue());
        try {
            markProcessing(payload);
            processBusiness(payload);
            markCompleted(payload);
            acknowledge(message);
        } catch (Exception e) {
            handleFailure(payload, retryCount, e);
            acknowledge(message);
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

    protected StringRedisTemplate createRedisTemplate() {
        return new StringRedisTemplate(connectionFactory);
    }

    protected void acknowledge(MapRecord<String, String, String> message) {
        redisTemplate().opsForStream().acknowledge(streamKey, groupName, message.getId());
    }

    private StringRedisTemplate redisTemplate() {
        if (redisTemplate == null) {
            redisTemplate = createRedisTemplate();
        }
        return redisTemplate;
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
