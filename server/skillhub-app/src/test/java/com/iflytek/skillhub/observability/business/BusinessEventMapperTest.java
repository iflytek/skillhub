package com.iflytek.skillhub.observability.business;

import com.iflytek.skillhub.domain.event.*;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import io.opentelemetry.api.common.AttributeKey;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessEventMapperTest {
    private final BusinessEventMapper mapper = new BusinessEventMapper(new BusinessLogsProperties());
    private final BusinessEventSnapshot snapshot = new BusinessEventSnapshot(
            7L, SkillVisibility.PRIVATE, "reviewer-opaque", "user", "review", Instant.parse("2026-10-01T00:00:00Z"));

    @Test
    void rejectionExportsOnlyExplicitAllowlistNeverReasonOrSubmitter() {
        var record = mapper.map(new ReviewRejectedEvent(3L, 1L, 2L, "reviewer-opaque", "secret-email",
                "token=secret signed-url IP UA content", snapshot)).orElseThrow();
        assertThat(record.timestamp()).isEqualTo(snapshot.occurredAt());
        assertThat(record.attributes().asMap().keySet()).extracting(AttributeKey::getKey).containsExactlyInAnyOrder(
                "skillhub.event.name", "skillhub.skill.id", "skillhub.version.id", "skillhub.namespace.id",
                "skillhub.visibility", "skillhub.source", "skillhub.actor.type", "user.id", "log.record.uid",
                "skillhub.review.id");
        assertThat(record.attributes().toString()).doesNotContain("secret", "signed-url", "content", "submitter");
        assertThat(record.attributes().get(AttributeKey.stringKey("user.id"))).isEqualTo("reviewer-opaque");
    }

    @Test
    void anonymousHasNoUserIdAndMappingHasStableUid() {
        var anonymous = new BusinessEventSnapshot(7L, SkillVisibility.PUBLIC, null, "user", "download", snapshot.occurredAt());
        var record = mapper.map(new SkillDownloadedEvent(1L, 2L, anonymous)).orElseThrow();
        assertThat(record.attributes().get(AttributeKey.stringKey("user.id"))).isNull();
        assertThat(record.attributes().get(AttributeKey.stringKey("skillhub.actor.type"))).isEqualTo("user");
        assertThat(record.attributes().get(AttributeKey.stringKey("log.record.uid")))
                .isEqualTo(record.uid()).matches("[0-9a-f-]{36}");
    }

    @Test
    void namespaceAndEventAllowlistFailClosedAndMissingSnapshotIsNotEnrichedFromDatabase() {
        var properties = new BusinessLogsProperties();
        properties.setNamespaceAllowlist(Set.of(8L));
        assertThat(new BusinessEventMapper(properties).map(new SkillPublishedEvent(1L, 2L, "actor", snapshot))).isEmpty();
        properties.setNamespaceAllowlist(Set.of());
        properties.setEventAllowlist(Set.of("skill.download.prepared"));
        assertThat(new BusinessEventMapper(properties).map(new SkillPublishedEvent(1L, 2L, "actor", snapshot))).isEmpty();
        assertThat(mapper.map(new SkillPublishedEvent(1L, 2L, "actor"))).isEmpty();
    }

    @Test
    void userValuesAreLengthBoundedAndSystemDoesNotExportServiceAccountAsHuman() {
        var system = new BusinessEventSnapshot(7L, SkillVisibility.PRIVATE, "x".repeat(1000), "system", "auto_publish", snapshot.occurredAt());
        var record = mapper.map(new SkillPublishedEvent(1L, 2L, "ignored", system)).orElseThrow();
        assertThat(record.attributes().get(AttributeKey.stringKey("user.id"))).isNull();
        var human = new BusinessEventSnapshot(7L, SkillVisibility.PRIVATE, "x".repeat(1000), "user", "review", snapshot.occurredAt());
        assertThat(mapper.map(new SkillPublishedEvent(1L, 2L, "ignored", human)).orElseThrow()
                .attributes().get(AttributeKey.stringKey("user.id"))).hasSize(256);
    }

    @Test
    void unknownEventAndNullContextFieldsAreNeverSerializedOrEnriched() {
        record SensitiveEvent(Long skillId, BusinessEventSnapshot snapshot, String password) implements SkillBusinessEvent {}
        assertThat(mapper.map(new SensitiveEvent(1L, snapshot, "must-never-appear"))).isEmpty();
        var missingSource = new BusinessEventSnapshot(7L, SkillVisibility.PRIVATE, "actor", "user", null, snapshot.occurredAt());
        assertThat(mapper.map(new SkillPublishedEvent(1L, 2L, "actor", missingSource))).isEmpty();
        assertThat(mapper.map(new SkillVersionYankedEvent(1L, 2L, "actor", false, snapshot))).isEmpty();
        assertThat(mapper.map(new SkillStatusChangedEvent(1L, com.iflytek.skillhub.domain.skill.SkillStatus.ACTIVE,
                com.iflytek.skillhub.domain.skill.SkillStatus.ACTIVE, snapshot))).isEmpty();
    }

    @Test
    void reservedBuiltInPublisherIsSystemEvenThroughOrdinaryPrivilegedPublishPath() {
        var skill = new com.iflytek.skillhub.domain.skill.Skill(7L, "not-exported", "builtin-skill-publisher", SkillVisibility.PUBLIC);
        var captured = BusinessEventSnapshot.capture(skill, "builtin-skill-publisher", "user", "auto_publish");
        assertThat(captured.actorType()).isEqualTo("system");
        var record = mapper.map(new SkillPublishedEvent(1L, 2L, "builtin-skill-publisher", captured)).orElseThrow();
        assertThat(record.attributes().get(AttributeKey.stringKey("user.id"))).isNull();
    }
}
