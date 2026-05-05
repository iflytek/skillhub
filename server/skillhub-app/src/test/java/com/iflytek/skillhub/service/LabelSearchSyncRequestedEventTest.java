package com.iflytek.skillhub.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LabelSearchSyncRequestedEventTest {

    @Test
    void recordFieldsAreAccessible() {
        LabelSearchSyncRequestedEvent event = new LabelSearchSyncRequestedEvent(List.of(1L, 2L, 3L));

        assertThat(event.skillIds()).containsExactly(1L, 2L, 3L);
    }
}
