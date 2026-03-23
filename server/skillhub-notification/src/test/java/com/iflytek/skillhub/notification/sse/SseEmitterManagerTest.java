package com.iflytek.skillhub.notification.sse;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterManagerTest {

    private Queue<TestEmitter> emitters;
    private SseEmitterManager manager;

    @BeforeEach
    void setUp() {
        emitters = new ArrayDeque<>();
        manager = new SseEmitterManager(userId -> {
            TestEmitter emitter = emitters.remove();
            emitter.registerUser(userId);
            return emitter;
        });
    }

    @Test
    void register_shouldReturnEmitter() {
        emitters.add(new TestEmitter());

        SseEmitter emitter = manager.register("user-1");

        assertNotNull(emitter);
        assertEquals(1, manager.totalEmitters());
        assertEquals(1, manager.emittersForUser("user-1"));
    }

    @Test
    void defaultTimeout_shouldOutliveManyHeartbeats() {
        assertTrue(SseEmitterManager.defaultTimeoutMillis() >= 10 * 60_000L);
        assertTrue(SseEmitterManager.defaultTimeoutMillis() > SseEmitterManager.heartbeatIntervalMillis() * 2);
    }

    @Test
    void register_shouldKeepAccurateCountWhenEvictingOldestEmitter() {
        for (int i = 0; i < 6; i++) {
            emitters.add(new TestEmitter());
        }

        for (int i = 0; i < 6; i++) {
            manager.register("user-evict");
        }

        assertEquals(5, manager.totalEmitters());
        assertEquals(5, manager.emittersForUser("user-evict"));
    }

    @Test
    void register_shouldTolerateEmitterThatThrowsDuringEvictionCompletion() {
        TestEmitter oldest = new TestEmitter();
        oldest.throwOnComplete();
        emitters.add(oldest);
        for (int i = 0; i < 5; i++) {
            emitters.add(new TestEmitter());
        }

        for (int i = 0; i < 5; i++) {
            manager.register("user-evict");
        }

        assertDoesNotThrow(() -> manager.register("user-evict"));
        assertEquals(5, manager.totalEmitters());
        assertEquals(5, manager.emittersForUser("user-evict"));
    }

    @Test
    void push_shouldRemoveEmitterWhenSendFails() {
        TestEmitter healthy = new TestEmitter();
        TestEmitter broken = new TestEmitter();
        broken.failAfterConnected();
        emitters.add(healthy);
        emitters.add(broken);
        manager.register("user-1");
        manager.register("user-1");

        manager.push("user-1", "payload");

        assertEquals(1, manager.totalEmitters());
        assertEquals(1, manager.emittersForUser("user-1"));
    }

    @Test
    void heartbeat_shouldRemoveEmitterWhenSendFails() {
        TestEmitter healthy = new TestEmitter();
        TestEmitter broken = new TestEmitter();
        broken.failAfterConnected();
        emitters.add(healthy);
        emitters.add(broken);
        manager.register("user-1");
        manager.register("user-1");

        manager.heartbeat();

        assertEquals(1, manager.totalEmitters());
        assertEquals(1, manager.emittersForUser("user-1"));
    }

    @Test
    void cleanup_shouldBeIdempotent() {
        TestEmitter emitter = new TestEmitter();
        emitters.add(emitter);
        manager.register("user-1");

        emitter.fireError();
        emitter.fireError();

        assertEquals(0, manager.totalEmitters());
        assertEquals(0, manager.emittersForUser("user-1"));
    }

    @Test
    void push_shouldDoNothingForUnregisteredUser() {
        assertDoesNotThrow(() -> manager.push("unknown-user", "some-data"));
        assertEquals(0, manager.totalEmitters());
    }

    @Test
    void register_multipleUsers_shouldTrackSeparately() {
        emitters.add(new TestEmitter());
        emitters.add(new TestEmitter());

        SseEmitter emitter1 = manager.register("user-1");
        SseEmitter emitter2 = manager.register("user-2");

        assertNotNull(emitter1);
        assertNotNull(emitter2);
        assertEquals(2, manager.totalEmitters());
        assertEquals(1, manager.emittersForUser("user-1"));
        assertEquals(1, manager.emittersForUser("user-2"));
    }

    private static final class TestEmitter extends SseEmitter {
        private final AtomicInteger errorCallbacks = new AtomicInteger(0);
        private Runnable completionCallback = () -> {};
        private Runnable timeoutCallback = () -> {};
        private java.util.function.Consumer<Throwable> errorCallback = error -> {};
        private String userId;
        private boolean failAfterConnected;
        private boolean throwOnComplete;
        private int sendCount;

        private TestEmitter() {
            super(60_000L);
        }

        void registerUser(String userId) {
            this.userId = userId;
        }

        void failAfterConnected() {
            this.failAfterConnected = true;
        }

        void throwOnComplete() {
            this.throwOnComplete = true;
        }

        void fireError() {
            errorCallback.accept(new IOException("boom-" + userId + "-" + errorCallbacks.incrementAndGet()));
        }

        @Override
        public synchronized void onCompletion(Runnable callback) {
            this.completionCallback = callback;
        }

        @Override
        public synchronized void onTimeout(Runnable callback) {
            this.timeoutCallback = callback;
        }

        @Override
        public synchronized void onError(java.util.function.Consumer<Throwable> callback) {
            this.errorCallback = callback;
        }

        @Override
        public void complete() {
            if (throwOnComplete) {
                throw new IllegalStateException("already complete");
            }
            completionCallback.run();
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sendCount++;
            if (failAfterConnected && sendCount > 1) {
                throw new IOException("send failed");
            }
        }
    }
}
