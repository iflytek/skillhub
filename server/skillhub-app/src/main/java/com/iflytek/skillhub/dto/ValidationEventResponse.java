package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.authoring.validation.ValidationEvent;
import java.time.Instant;
import java.util.Map;

/** One persisted validation event (log line, tool call, finding, phase marker...). */
public record ValidationEventResponse(
        Long id,
        Long runId,
        Integer seq,
        String type,
        String phase,
        Map<String, Object> payload,
        Instant createdAt
) {

    public static ValidationEventResponse from(ValidationEvent event) {
        return new ValidationEventResponse(
                event.getId(),
                event.getRunId(),
                event.getSeq(),
                event.getEventType().name(),
                event.getPhase(),
                event.getPayload() == null ? Map.of() : event.getPayload(),
                event.getCreatedAt());
    }
}
