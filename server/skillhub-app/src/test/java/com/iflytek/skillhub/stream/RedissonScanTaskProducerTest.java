package com.iflytek.skillhub.stream;

import com.iflytek.skillhub.domain.security.ScanTask;
import com.iflytek.skillhub.observability.MessageObservationSupport;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.client.codec.StringCodec;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamAddParams;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedissonScanTaskProducerTest {

    @Test
    void publishScanTask_writesExpectedFieldsToConfiguredStream() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        @SuppressWarnings("unchecked")
        RStream<String, String> typedStream = (RStream<String, String>) (RStream<?, ?>) stream;
        RedissonClient redissonClient = mock(RedissonClient.class);
        doReturn(typedStream).when(redissonClient).getStream("skillhub:scan:requests", StringCodec.INSTANCE);
        when(stream.add(any())).thenReturn(new StreamMessageId(1, 0));
        RequestIdAccessor requestIdAccessor = new RequestIdAccessor();
        RedissonScanTaskProducer producer = new RedissonScanTaskProducer(
                redissonClient,
                "skillhub:scan:requests",
                new MessageObservationSupport(ObservationRegistry.NOOP, requestIdAccessor)
        );

        try (RequestIdAccessor.Scope ignored = requestIdAccessor.open("request-stream-1")) {
            producer.publishScanTask(new ScanTask(
                    "task-1",
                    42L,
                    "/tmp/skill",
                    null,
                    "publisher-1",
                    1711260000000L,
                    Map.of("scannerType", "skill-scanner")
            ));
        }

        verify(redissonClient).getStream("skillhub:scan:requests", StringCodec.INSTANCE);
        ArgumentCaptor<StreamAddArgs<String, String>> argsCaptor = ArgumentCaptor.forClass(StreamAddArgs.class);
        verify(stream).add(argsCaptor.capture());
        assertThat(argsCaptor.getValue()).isInstanceOf(StreamAddParams.class);
        @SuppressWarnings("unchecked")
        StreamAddParams<String, String> params = (StreamAddParams<String, String>) argsCaptor.getValue();
        assertThat(params.getEntries())
                .containsEntry(MessageObservationSupport.REQUEST_ID_FIELD, "request-stream-1");
    }
}
