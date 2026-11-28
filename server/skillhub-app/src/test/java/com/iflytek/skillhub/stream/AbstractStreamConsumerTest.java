package com.iflytek.skillhub.stream;

import com.iflytek.skillhub.observability.MessageObservationSupport;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.observation.ObservationRegistry;
import io.lettuce.core.RedisBusyException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.mockito.ArgumentCaptor;
import org.redisson.api.AutoClaimResult;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamCreateGroupParams;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.client.RedisException;
import org.springframework.data.redis.RedisSystemException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    void readGroupMissingGroupRecreatesGroupAndRecoversOnNextPoll() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        TestConsumer consumer = new TestConsumer(stream);
        StreamMessageId messageId = new StreamMessageId(30, 0);
        when(stream.readGroup(eq("scan-group"), anyString(), org.mockito.ArgumentMatchers.<StreamReadGroupArgs>any()))
                .thenThrow(nogroupError("XREADGROUP"))
                .thenReturn(Map.of(messageId, Map.of("payload", "after-recovery")));

        // The failing poll still propagates for the loop's error visibility; the group is
        // recreated between polls, not swallowed inside this call.
        assertThatThrownBy(consumer::consumeAvailableMessages)
                .isInstanceOf(RedisException.class)
                .hasMessageContaining("NOGROUP");
        ArgumentCaptor<StreamCreateGroupArgs> recreateArgs =
                ArgumentCaptor.forClass(StreamCreateGroupArgs.class);
        verify(stream).createGroup(recreateArgs.capture());
        // StreamCreateGroupArgs exposes no getters; the concrete params carry the values.
        assertThat(recreateArgs.getValue()).isInstanceOf(StreamCreateGroupParams.class);
        StreamCreateGroupParams recreateParams = (StreamCreateGroupParams) recreateArgs.getValue();
        assertThat(recreateParams.getName()).isEqualTo("scan-group");
        // XGROUP CREATE accepts only a concrete id, "$" or "0"; Redis rejects "-" (StreamMessageId.MIN)
        // with "ERR Invalid stream ID". "0" starts at the beginning so stranded entries are redelivered.
        assertThat(recreateParams.getId().toString()).isEqualTo("0");
        assertThat(recreateParams.isMakeStream()).isTrue();

        // The next poll delivers again, covering entries stranded while the group was missing.
        consumer.consumeAvailableMessages();
        verify(stream).ack("scan-group", messageId);
    }

    @Test
    void autoClaimMissingGroupRecreatesGroupAndPropagates() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        when(stream.autoClaim(eq("scan-group"), anyString(), anyLong(), eq(java.util.concurrent.TimeUnit.MILLISECONDS),
                eq(StreamMessageId.MIN), anyInt()))
                .thenThrow(nogroupError("XAUTOCLAIM"));
        TestConsumer consumer = new TestConsumer(stream);

        assertThatThrownBy(consumer::reclaimPendingMessages)
                .isInstanceOf(RedisException.class)
                .hasMessageContaining("NOGROUP");
        verify(stream).createGroup(any(StreamCreateGroupArgs.class));
    }

    @Test
    void concurrentBusyGroupDuringRecoveryIsTreatedAsRecovered() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        when(stream.readGroup(eq("scan-group"), anyString(), org.mockito.ArgumentMatchers.<StreamReadGroupArgs>any()))
                .thenThrow(nogroupError("XREADGROUP"));
        doThrow(new RedisException("BUSYGROUP Consumer Group name already exists"))
                .when(stream).createGroup(any(StreamCreateGroupArgs.class));
        TestConsumer consumer = new TestConsumer(stream);

        // Another instance recreated the group first: the original NOGROUP still propagates,
        // the BUSYGROUP from our own create attempt is not raised again.
        assertThatThrownBy(consumer::consumeAvailableMessages)
                .isInstanceOf(RedisException.class)
                .hasMessageContaining("NOGROUP");
        verify(stream).createGroup(any(StreamCreateGroupArgs.class));
    }

    @Test
    void missingGroupRecoveryIsThrottledWhileRecreationFails() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        when(stream.readGroup(eq("scan-group"), anyString(), org.mockito.ArgumentMatchers.<StreamReadGroupArgs>any()))
                .thenThrow(nogroupError("XREADGROUP"));
        doThrow(new RedisException("ERR group creation rejected"))
                .when(stream).createGroup(any(StreamCreateGroupArgs.class));
        TestConsumer consumer = new TestConsumer(stream);

        // Repeated NOGROUP polls within the retry interval must not hot-loop XGROUP CREATE.
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(consumer::consumeAvailableMessages)
                    .isInstanceOf(RedisException.class)
                    .hasMessageContaining("NOGROUP");
        }
        verify(stream, times(1)).createGroup(any(StreamCreateGroupArgs.class));
    }

    @Test
    void missingGroupRecoveryNeverResetsExistingGroupOrStreamEntries() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        when(stream.readGroup(eq("scan-group"), anyString(), org.mockito.ArgumentMatchers.<StreamReadGroupArgs>any()))
                .thenThrow(nogroupError("XREADGROUP"));
        doThrow(new RedisException("BUSYGROUP Consumer Group name already exists"))
                .when(stream).createGroup(any(StreamCreateGroupArgs.class));
        TestConsumer consumer = new TestConsumer(stream);

        assertThatThrownBy(consumer::consumeAvailableMessages)
                .isInstanceOf(RedisException.class);

        verify(stream, never()).removeGroup(anyString());
        verify(stream, never()).ack(anyString(), any(StreamMessageId.class));
        verify(stream, never()).remove(any(StreamMessageId.class));
    }

    @Test
    void otherRedisErrorsPropagateWithoutGroupRecovery() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        when(stream.readGroup(eq("scan-group"), anyString(), org.mockito.ArgumentMatchers.<StreamReadGroupArgs>any()))
                .thenThrow(new RedisException("MISCONF Redis is configured to preserve AOF"));
        TestConsumer consumer = new TestConsumer(stream);

        assertThatThrownBy(consumer::consumeAvailableMessages)
                .isInstanceOf(RedisException.class)
                .hasMessageContaining("MISCONF");
        verify(stream, never()).createGroup(any(StreamCreateGroupArgs.class));
    }

    @Test
    void businessExceptionMentioningNogroupDoesNotRecreateGroup() {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        when(stream.readGroup(eq("scan-group"), anyString(), org.mockito.ArgumentMatchers.<StreamReadGroupArgs>any()))
                .thenThrow(new IllegalStateException("NOGROUP seen in unrelated business error"));
        TestConsumer consumer = new TestConsumer(stream);

        assertThatThrownBy(consumer::consumeAvailableMessages)
                .isInstanceOf(IllegalStateException.class);
        verify(stream, never()).createGroup(any(StreamCreateGroupArgs.class));
    }

    @Test
    void detectsMissingGroupOnlyOnRedisCommandFailures() {
        assertThat(AbstractStreamConsumer.isConsumerGroupMissing(nogroupError("XREADGROUP"))).isTrue();
        assertThat(AbstractStreamConsumer.isConsumerGroupMissing(
                new RuntimeException("wrapped", nogroupError("XAUTOCLAIM")))).isTrue();
        // Staging shape: Spring Data Redis wrapper carrying the server message itself.
        assertThat(AbstractStreamConsumer.isConsumerGroupMissing(
                new RedisSystemException(nogroupError("XREADGROUP").getMessage(), new RuntimeException("cause")))).isTrue();
        assertThat(AbstractStreamConsumer.isConsumerGroupMissing(
                new IllegalStateException(nogroupError("XREADGROUP").getMessage()))).isFalse();
        assertThat(AbstractStreamConsumer.isConsumerGroupMissing(
                new RedisException("MISCONF Redis is configured to preserve AOF"))).isFalse();
    }

    @Test
    void backoffDelayGrowsExponentiallyFromOneSecondAndCapsAtThirtySeconds() {
        assertThat(AbstractStreamConsumer.backoffDelayMillis(1)).isEqualTo(1_000L);
        assertThat(AbstractStreamConsumer.backoffDelayMillis(2)).isEqualTo(2_000L);
        assertThat(AbstractStreamConsumer.backoffDelayMillis(3)).isEqualTo(4_000L);
        assertThat(AbstractStreamConsumer.backoffDelayMillis(5)).isEqualTo(16_000L);
        assertThat(AbstractStreamConsumer.backoffDelayMillis(6)).isEqualTo(30_000L);
        assertThat(AbstractStreamConsumer.backoffDelayMillis(1_000)).isEqualTo(30_000L);
        assertThat(AbstractStreamConsumer.backoffDelayMillis(Integer.MAX_VALUE)).isEqualTo(30_000L);
    }

    @Test
    void consumeLoopBacksOffOnConsecutiveFailuresAndResetsAfterSuccess() throws Exception {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        AtomicInteger polls = new AtomicInteger();
        when(stream.readGroup(eq("scan-group"), anyString(), org.mockito.ArgumentMatchers.<StreamReadGroupArgs>any()))
                .thenAnswer(invocation -> {
                    int call = polls.incrementAndGet();
                    if (call <= 3 || call == 5) {
                        throw new IllegalStateException("redis down");
                    }
                    Thread.sleep(2); // stands in for the 2s XREADGROUP block once Redis is back
                    return Map.of();
                });
        TestConsumer consumer = new TestConsumer(stream);
        consumer.stopAfterBackoffs(4);

        consumer.init();
        assertThat(consumer.backoffsDone.await(10, TimeUnit.SECONDS)).isTrue();
        consumer.shutdown();

        // 3 failures grow 1s -> 2s -> 4s; the successful poll (call 4) resets, so call 5 waits 1s again.
        assertThat(consumer.backoffDelays.subList(0, 4)).containsExactly(1_000L, 2_000L, 4_000L, 1_000L);
    }

    @Test
    void consumeLoopLogsFirstFailureInFullThenAtMostOneSummaryPerMinute() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(TestConsumer.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        try {
            @SuppressWarnings("unchecked")
            RStream<String, String> stream = mock(RStream.class);
            when(stream.readGroup(eq("scan-group"), anyString(),
                    org.mockito.ArgumentMatchers.<StreamReadGroupArgs>any()))
                    .thenThrow(new IllegalStateException("redis down"));
            TestConsumer consumer = new TestConsumer(stream);
            // Virtual time: failures land at t=0,1,3,7,15,31,61s (cumulative 1,2,4,8,16,30s backoffs).
            consumer.stopAfterBackoffs(7);

            consumer.init();
            assertThat(consumer.backoffsDone.await(10, TimeUnit.SECONDS)).isTrue();
            consumer.shutdown();

            List<ILoggingEvent> errors = appender.list.stream()
                    .filter(event -> event.getLevel() == Level.ERROR)
                    .toList();
            assertThat(errors).hasSize(2);
            assertThat(errors.get(0).getFormattedMessage())
                    .startsWith("Failed while consuming stream messages: stream=scan-stream");
            assertThat(errors.get(0).getThrowableProxy()).isNotNull();
            // Failures 2..6 (t=1..31s) were suppressed; failure 7 at t=61s emits the summary.
            assertThat(errors.get(1).getFormattedMessage())
                    .contains("Still failing while consuming stream messages")
                    .contains("consecutiveFailures=7")
                    .contains("suppressed=5")
                    .contains("redis down");
            assertThat(errors.get(1).getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void consumeLoopLogsRecoveryWhenPollingSucceedsAgain() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(TestConsumer.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        TestConsumer consumer = null;
        try {
            @SuppressWarnings("unchecked")
            RStream<String, String> stream = mock(RStream.class);
            AtomicInteger polls = new AtomicInteger();
            when(stream.readGroup(eq("scan-group"), anyString(),
                    org.mockito.ArgumentMatchers.<StreamReadGroupArgs>any()))
                    .thenAnswer(invocation -> {
                        if (polls.incrementAndGet() <= 2) {
                            throw new IllegalStateException("redis down");
                        }
                        Thread.sleep(2);
                        return Map.of();
                    });
            consumer = new TestConsumer(stream);

            consumer.init();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline && appender.list.stream().noneMatch(
                    event -> event.getFormattedMessage().contains("Resumed consuming stream messages"))) {
                Thread.sleep(5);
            }

            assertThat(appender.list.stream()
                    .filter(event -> event.getFormattedMessage().contains("Resumed consuming stream messages"))
                    .map(ILoggingEvent::getFormattedMessage))
                    .singleElement()
                    .asString()
                    .contains("after 2 consecutive failures");
        } finally {
            if (consumer != null) {
                consumer.shutdown();
            }
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void emptyRedisNogroupIsRecoveredAndConsumptionResumesWithoutHotLooping() throws Exception {
        @SuppressWarnings("unchecked")
        RStream<String, String> stream = mock(RStream.class);
        StreamMessageId messageId = new StreamMessageId(40, 0);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicInteger polls = new AtomicInteger();
        when(stream.readGroup(eq("scan-group"), anyString(), org.mockito.ArgumentMatchers.<StreamReadGroupArgs>any()))
                .thenAnswer(invocation -> {
                    // Redis flushed: NOGROUP on every poll until the group has been recreated.
                    if (polls.incrementAndGet() == 1) {
                        throw nogroupError("XREADGROUP");
                    }
                    Thread.sleep(2);
                    return Map.of(messageId, Map.of("payload", "queued-before-recovery"));
                });
        when(stream.ack("scan-group", messageId)).thenAnswer(invocation -> {
            delivered.countDown();
            return 1L;
        });
        TestConsumer consumer = new TestConsumer(stream);

        consumer.init();
        assertThat(delivered.await(10, TimeUnit.SECONDS)).isTrue();
        consumer.shutdown();

        // One create at startup (init) and exactly one recovery create after the NOGROUP poll,
        // and the failed poll was followed by a backoff rather than an immediate retry.
        ArgumentCaptor<StreamCreateGroupArgs> creates = ArgumentCaptor.forClass(StreamCreateGroupArgs.class);
        verify(stream, times(2)).createGroup(creates.capture());
        StreamCreateGroupParams recovery = (StreamCreateGroupParams) creates.getAllValues().get(1);
        assertThat(recovery.isMakeStream()).isTrue();
        assertThat(recovery.getId().toString()).isEqualTo("0");
        assertThat(consumer.backoffDelays.get(0)).isEqualTo(1_000L);
        verify(stream, org.mockito.Mockito.atLeastOnce()).ack("scan-group", messageId);
    }

    private static RedisException nogroupError(String command) {
        return new RedisException(
                "NOGROUP No such key 'scan-stream' or consumer group 'scan-group' in " + command + " with GROUP option");
    }

    private static class TestConsumer extends AbstractStreamConsumer<String> {
        private final RStream<String, String> stream;
        private final RequestIdAccessor requestIdAccessor;
        private boolean fail;
        private boolean defer;
        private boolean deferred;
        private String processedRequestId;
        private final List<Long> backoffDelays = new CopyOnWriteArrayList<>();
        private final CountDownLatch backoffsDone = new CountDownLatch(1);
        private int stopAfterBackoffs = Integer.MAX_VALUE;
        // Virtual clock: backoff sleeps advance it instead of waiting.
        private volatile long virtualNowMillis = 1_000_000L;

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

        private void stopAfterBackoffs(int count) {
            this.stopAfterBackoffs = count;
        }

        @Override
        long currentTimeMillis() {
            return virtualNowMillis;
        }

        @Override
        void backoffSleep(long millis) {
            backoffDelays.add(millis);
            virtualNowMillis += millis;
            if (backoffDelays.size() >= stopAfterBackoffs) {
                // Ends the loop deterministically right after this backoff.
                backoffsDone.countDown();
                shutdown();
            }
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
