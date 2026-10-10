package com.iflytek.skillhub.service.authoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.authoring.validation.ValidationEvent;
import com.iflytek.skillhub.domain.authoring.validation.ValidationEventType;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-memory SSE hub for validation runs. Each subscriber tracks the last sequence
 * number it saw, so replay (Last-Event-ID / afterSeq) and live delivery can be
 * interleaved without duplicates. The hub is per-instance by design: the current
 * deployment runs one backend node, and remote clients always have the polling
 * endpoint as a fallback.
 */
@Component
public class ValidationEventBroadcaster {

    /** SSE connection lifetime cap; clients reconnect with Last-Event-ID on expiry. */
    static final long EMITTER_TIMEOUT_MS = 30L * 60 * 1000;

    private final Map<Long, CopyOnWriteArrayList<EmitterSession>> sessionsByRun = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public ValidationEventBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Registers a live emitter and replays already-persisted events through it. The
     * session cursor makes replay and concurrent live delivery duplicate-free and
     * order-preserving: every send fires only for sequence numbers the session has
     * not delivered yet.
     *
     * @param runId     the run to subscribe to
     * @param lastSeq   the subscriber's last seen sequence number (Last-Event-ID)
     * @param replay    persisted events after {@code lastSeq}, oldest first
     */
    public SseEmitter subscribe(Long runId, int lastSeq, List<ValidationEvent> replay) {
        EmitterSession session = new EmitterSession(lastSeq);
        sessionsByRun.computeIfAbsent(runId, key -> new CopyOnWriteArrayList<>()).add(session);
        Runnable detach = () -> detach(runId, session);
        session.emitter().onCompletion(detach);
        session.emitter().onTimeout(detach);
        session.emitter().onError(error -> detach.run());

        boolean terminal = false;
        for (ValidationEvent event : replay) {
            if (!session.send(event.getSeq(), envelope(event))) {
                detach(runId, session);
                return session.emitter();
            }
            if (event.getEventType() == ValidationEventType.RUN_FINISHED) {
                terminal = true;
            }
        }
        if (terminal) {
            // run already finished: close the stream so clients stop reconnecting
            session.emitter().complete();
        }
        return session.emitter();
    }

    /** Pushes one persisted event to every live subscriber of the run. */
    public void publish(Long runId, ValidationEvent event) {
        List<EmitterSession> sessions = sessionsByRun.get(runId);
        if (sessions == null) {
            return;
        }
        String envelope = envelope(event);
        for (EmitterSession session : sessions) {
            if (!session.send(event.getSeq(), envelope)) {
                detach(runId, session);
            }
        }
    }

    /** Sends the terminal event and completes all subscribers of a finished run. */
    public void completeRun(Long runId, ValidationEvent terminalEvent) {
        publish(runId, terminalEvent);
        List<EmitterSession> sessions = sessionsByRun.remove(runId);
        if (sessions == null) {
            return;
        }
        for (EmitterSession session : sessions) {
            session.emitter().complete();
        }
    }

    private void detach(Long runId, EmitterSession session) {
        List<EmitterSession> sessions = sessionsByRun.get(runId);
        if (sessions != null) {
            sessions.remove(session);
        }
    }

    private String envelope(ValidationEvent event) {
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        Map<String, Object> envelope = Map.of(
                "seq", event.getSeq(),
                "type", event.getEventType().name(),
                "phase", event.getPhase() == null ? "" : event.getPhase(),
                "payload", payload,
                "createdAt", event.getCreatedAt().toString());
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (IOException exception) {
            return "{\"seq\":" + event.getSeq() + ",\"type\":\"" + event.getEventType() + "\"}";
        }
    }

    /** One subscriber connection with its own delivery cursor and serialized writes. */
    static final class EmitterSession {

        private final SseEmitter emitter;
        private int lastSeq;

        EmitterSession(int lastSeq) {
            this.emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
            this.lastSeq = lastSeq;
        }

        SseEmitter emitter() {
            return emitter;
        }

        int lastSeq() {
            return lastSeq;
        }

        /** Sends unless the event predates this session's cursor; false means the pipe broke. */
        synchronized boolean send(int seq, String envelope) {
            if (seq <= lastSeq) {
                return true;
            }
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(seq))
                        .data(envelope, MediaType.APPLICATION_JSON));
                lastSeq = seq;
                return true;
            } catch (IOException | IllegalStateException exception) {
                return false;
            }
        }
    }
}
