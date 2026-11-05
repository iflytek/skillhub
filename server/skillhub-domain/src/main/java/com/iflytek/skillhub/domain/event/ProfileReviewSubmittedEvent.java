package com.iflytek.skillhub.domain.event;

import java.util.List;

public record ProfileReviewSubmittedEvent(Long profileReviewId, String submitterId, List<String> fields, String eventId, java.time.Instant occurredAt) {
    public ProfileReviewSubmittedEvent(Long profileReviewId, String submitterId, List<String> fields) {
        this(profileReviewId,submitterId,fields,java.util.UUID.randomUUID().toString(),java.time.Instant.now());
    }


    public ProfileReviewSubmittedEvent {
        fields = List.copyOf(fields);
        java.util.Objects.requireNonNull(eventId);
        java.util.Objects.requireNonNull(occurredAt);
    }
}
