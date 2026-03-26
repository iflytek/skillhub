package com.iflytek.skillhub.stream;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.iflytek.skillhub.domain.security.ScanTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.client.codec.StringCodec;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedissonScanTaskProducerLoggingTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RedissonScanTaskProducer.class);
    private ListAppender<ILoggingEvent> appender;

    @AfterEach
    void tearDown() {
        if (appender != null) {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void publishScanTask_logsBundleKeyPresence() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        @SuppressWarnings("unchecked")
        RStream<String, String> typedStream = (RStream<String, String>) (RStream<?, ?>) stream;
        RedissonClient redissonClient = mock(RedissonClient.class);
        doReturn(typedStream).when(redissonClient).getStream("skillhub:scan:requests", StringCodec.INSTANCE);
        when(stream.add(any())).thenReturn(new StreamMessageId(1, 0));
        RedissonScanTaskProducer producer = new RedissonScanTaskProducer(redissonClient, "skillhub:scan:requests");
        attachAppender();

        producer.publishScanTask(new ScanTask(
                "task-1",
                42L,
                null,
                "packages/8/42/bundle.zip",
                "publisher-1",
                1711260000000L,
                Map.of("scannerType", "skill-scanner")
        ));

        assertThat(loggedMessages()).anyMatch(message -> message.contains(
                "Published scan task: taskId=task-1, versionId=42, bundleKey=packages/8/42/bundle.zip, hasSkillPath=false"
        ));
    }

    private void attachAppender() {
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    private java.util.List<String> loggedMessages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
