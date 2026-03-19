package com.iflytek.skillhub.notification.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SseEmitterManager {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterManager.class);
    private static final long SSE_TIMEOUT = 60_000L;
    private static final int MAX_EMITTERS_PER_USER = 5;
    private static final int MAX_TOTAL_EMITTERS = 1000;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final AtomicInteger totalCount = new AtomicInteger(0);

    public SseEmitter register(String userId) {
        if (totalCount.get() >= MAX_TOTAL_EMITTERS) {
            throw new IllegalStateException("SSE connection limit reached");
        }

        CopyOnWriteArrayList<SseEmitter> userEmitters = emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        if (userEmitters.size() >= MAX_EMITTERS_PER_USER) {
            SseEmitter oldest = userEmitters.remove(0);
            oldest.complete();
            totalCount.decrementAndGet();
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        userEmitters.add(emitter);
        totalCount.incrementAndGet();

        Runnable cleanup = () -> {
            userEmitters.remove(emitter);
            totalCount.decrementAndGet();
            if (userEmitters.isEmpty()) {
                emitters.remove(userId);
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            cleanup.run();
        }

        return emitter;
    }

    public void push(String userId, Object data) {
        CopyOnWriteArrayList<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) return;

        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(data));
            } catch (IOException e) {
                log.debug("Failed to push to user {}, removing emitter", userId);
            }
        }
    }

    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        emitters.forEach((userId, userEmitters) -> {
            for (SseEmitter emitter : userEmitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException e) {
                    log.debug("Heartbeat failed for user {}", userId);
                }
            }
        });
    }
}
