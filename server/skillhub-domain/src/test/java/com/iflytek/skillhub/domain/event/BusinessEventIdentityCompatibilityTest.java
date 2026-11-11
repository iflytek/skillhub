package com.iflytek.skillhub.domain.event;

import com.iflytek.skillhub.domain.skill.SkillVisibility;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BusinessEventIdentityCompatibilityTest {
    @Test
    void snapshotConstructorsKeepProducerTimeAndGenerateOneStableIdentity() throws Exception {
        var time = Instant.parse("2026-01-01T00:00:00Z");
        var snapshot = new BusinessEventSnapshot(1L, SkillVisibility.PUBLIC, "actor", "user", "web", time);
        List<SkillBusinessEvent> events = List.of(
                new SkillPublishedEvent(1L, 2L, "actor", snapshot),
                new SkillVersionYankedEvent(1L, 2L, "actor", true, snapshot),
                new ReviewApprovedEvent(3L, 1L, 2L, "actor", "author", snapshot),
                new ReviewRejectedEvent(3L, 1L, 2L, "actor", "author", "reason", snapshot),
                new PromotionApprovedEvent(3L, 1L, "actor", "author", snapshot),
                new PromotionRejectedEvent(3L, 1L, "actor", "author", "reason", snapshot));
        for (var event : events) {
            assertThat(event.snapshot()).isSameAs(snapshot);
            assertThat(event.getClass().getMethod("occurredAt").invoke(event)).isEqualTo(time);
            var accessor = event.getClass().getMethod("eventId");
            assertThat((String) accessor.invoke(event)).isNotBlank().isEqualTo(accessor.invoke(event));
        }
    }

    @Test
    void emailIdentityConstructorDoesNotInventABusinessSnapshot() {
        var time = Instant.parse("2026-01-01T00:00:00Z");
        var event = new SkillPublishedEvent(1L, 2L, "actor", "replayed-event", time);
        assertThat(event.snapshot()).isNull();
        assertThat(event.eventId()).isEqualTo("replayed-event");
        assertThat(event.occurredAt()).isEqualTo(time);
        var next = new SkillPublishedEvent(1L, 2L, "actor");
        assertThat(next.snapshot()).isNull();
        assertThat(next.eventId()).isNotEqualTo(new SkillPublishedEvent(1L, 2L, "actor").eventId());
    }
}
