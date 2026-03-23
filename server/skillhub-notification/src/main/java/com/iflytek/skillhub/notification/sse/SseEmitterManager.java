package com.iflytek.skillhub.notification.sse;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseEmitterManager {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterManager.class);
    private static final long SSE_TIMEOUT = 10 * 60_000L;
    private static final long HEARTBEAT_INTERVAL = 30_000L;
    private static final int MAX_EMITTERS_PER_USER = 5;
    private static final int MAX_TOTAL_EMITTERS = 1000;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<TrackedEmitter>> emitters = new ConcurrentHashMap<>();
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final Function<String, SseEmitter> emitterFactory;

    public SseEmitterManager() {
        this(userId -> new SseEmitter(SSE_TIMEOUT));
    }

    SseEmitterManager(Function<String, SseEmitter> emitterFactory) {
        this.emitterFactory = emitterFactory;
    }

    public SseEmitter register(String userId) {
        if (totalCount.get() >= MAX_TOTAL_EMITTERS) {
            throw new IllegalStateException("SSE connection limit reached");
        }

        CopyOnWriteArrayList<TrackedEmitter> userEmitters = emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        if (userEmitters.size() >= MAX_EMITTERS_PER_USER) {
            TrackedEmitter oldest = userEmitters.get(0);
            cleanup(userId, userEmitters, oldest);
            try {
                oldest.emitter().complete();
            } catch (IllegalStateException ex) {
                log.debug("Emitter already completed during eviction for user {}", userId);
            }
        }

        TrackedEmitter trackedEmitter = new TrackedEmitter(emitterFactory.apply(userId));
        userEmitters.add(trackedEmitter);
        totalCount.incrementAndGet();

        Runnable cleanup = () -> cleanup(userId, userEmitters, trackedEmitter);
        trackedEmitter.emitter().onCompletion(cleanup);
        trackedEmitter.emitter().onTimeout(cleanup);
        trackedEmitter.emitter().onError(e -> cleanup.run());

        try {
            trackedEmitter.emitter().send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            cleanup.run();
        }

        return trackedEmitter.emitter();
    }

    public void push(String userId, Object data) {
        CopyOnWriteArrayList<TrackedEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) return;

        for (TrackedEmitter trackedEmitter : userEmitters) {
            try {
                trackedEmitter.emitter().send(SseEmitter.event().name("notification").data(data));
            } catch (IOException e) {
                log.debug("Failed to push to user {}, removing emitter", userId);
                cleanup(userId, userEmitters, trackedEmitter);
            }
        }
    }

    @Scheduled(fixedRate = HEARTBEAT_INTERVAL)
    public void heartbeat() {
        emitters.forEach((userId, userEmitters) -> {
            for (TrackedEmitter trackedEmitter : userEmitters) {
                try {
                    trackedEmitter.emitter().send(SseEmitter.event().comment("ping"));
                } catch (IOException e) {
                    log.debug("Heartbeat failed for user {}", userId);
                    cleanup(userId, userEmitters, trackedEmitter);
                }
            }
        });
    }

    int totalEmitters() {
        return totalCount.get();
    }

    int emittersForUser(String userId) {
        return emitters.getOrDefault(userId, new CopyOnWriteArrayList<>()).size();
    }

    public static long defaultTimeoutMillis() {
        return SSE_TIMEOUT;
    }

    public static long heartbeatIntervalMillis() {
        return HEARTBEAT_INTERVAL;
    }

    private void cleanup(String userId,
                         CopyOnWriteArrayList<TrackedEmitter> userEmitters,
                         TrackedEmitter trackedEmitter) {
        if (!trackedEmitter.markCleaned()) {
            return;
        }
        userEmitters.remove(trackedEmitter);
        totalCount.decrementAndGet();
        if (userEmitters.isEmpty()) {
            emitters.remove(userId, userEmitters);
        }
    }

    private record TrackedEmitter(SseEmitter emitter, AtomicBoolean cleaned) {
        private TrackedEmitter(SseEmitter emitter) {
            this(emitter, new AtomicBoolean(false));
        }

        boolean markCleaned() {
            return cleaned.compareAndSet(false, true);
        }
    }
}
