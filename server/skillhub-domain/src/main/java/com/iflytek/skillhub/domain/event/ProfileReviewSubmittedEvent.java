package com.iflytek.skillhub.domain.event;

import java.util.List;

public record ProfileReviewSubmittedEvent(Long profileReviewId, String submitterId, List<String> fields) {

    public ProfileReviewSubmittedEvent {
        fields = List.copyOf(fields);
    }
}
