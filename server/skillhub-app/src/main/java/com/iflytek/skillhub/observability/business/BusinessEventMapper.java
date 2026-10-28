package com.iflytek.skillhub.observability.business;

import com.iflytek.skillhub.domain.event.*;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Sole data boundary. Never reflect/serialize an event or look up an entity here. */
public final class BusinessEventMapper {
    public static final Set<String> EVENT_NAMES = Set.of("skill.publication.effective", "skill.download.prepared",
            "skill.review.approved", "skill.review.rejected", "skill.promotion.approved", "skill.promotion.rejected",
            "skill.status.changed", "skill.visibility.changed", "skill.version.yanked");
    private static final Set<String> SOURCES = Set.of("auto_publish", "private_confirm", "review", "promotion",
            "download", "governance", "visibility");
    private final Set<String> events;
    private final Set<Long> namespaces;

    BusinessEventMapper(BusinessLogsProperties properties) {
        events = Set.copyOf(properties.getEventAllowlist());
        namespaces = Set.copyOf(properties.getNamespaceAllowlist());
    }

    public Optional<MappedRecord> map(SkillBusinessEvent event) {
        BusinessEventSnapshot snapshot = event.snapshot();
        if (snapshot == null || snapshot.namespaceId() == null || snapshot.visibility() == null
                || snapshot.occurredAt() == null || event.skillId() == null
                || snapshot.namespaceId() <= 0 || event.skillId() <= 0
                || snapshot.source() == null || snapshot.actorType() == null
                || !SOURCES.contains(snapshot.source())
                || !Set.of("user", "system").contains(snapshot.actorType())
                || (!namespaces.isEmpty() && !namespaces.contains(snapshot.namespaceId()))) {
            return Optional.empty();
        }
        AttributesBuilder attributes = Attributes.builder();
        String name;
        switch (event) {
            case SkillPublishedEvent e -> { name = "skill.publication.effective"; id(attributes, "version", e.versionId()); }
            case SkillDownloadedEvent e -> { name = "skill.download.prepared"; id(attributes, "version", e.versionId()); }
            case ReviewApprovedEvent e -> { name = "skill.review.approved"; id(attributes, "version", e.versionId()); id(attributes, "review", e.reviewId()); }
            case ReviewRejectedEvent e -> { name = "skill.review.rejected"; id(attributes, "version", e.versionId()); id(attributes, "review", e.reviewId()); }
            case PromotionApprovedEvent e -> { name = "skill.promotion.approved"; id(attributes, "promotion", e.promotionId()); }
            case PromotionRejectedEvent e -> { name = "skill.promotion.rejected"; id(attributes, "promotion", e.promotionId()); }
            case SkillStatusChangedEvent e -> {
                if (e.oldStatus() == null || e.newStatus() == null || e.oldStatus() == e.newStatus()) { return Optional.empty(); }
                name = "skill.status.changed";
                attributes.put("skillhub.status.old", e.oldStatus().name()).put("skillhub.status.new", e.newStatus().name());
            }
            case SkillVisibilityChangedEvent e -> {
                if (e.previousVisibility() == null || e.visibility() == null || e.previousVisibility() == e.visibility()) { return Optional.empty(); }
                name = "skill.visibility.changed";
                attributes.put("skillhub.visibility.old", e.previousVisibility().name()).put("skillhub.visibility.new", e.visibility().name());
            }
            case SkillVersionYankedEvent e -> {
                if (!e.wasPublished()) { return Optional.empty(); }
                name = "skill.version.yanked";
                id(attributes, "version", e.versionId());
            }
            default -> { return Optional.empty(); }
        }
        if (!events.contains(name)) { return Optional.empty(); }
        String uid = UUID.randomUUID().toString();
        attributes.put("skillhub.event.name", name).put("skillhub.skill.id", event.skillId())
                .put("skillhub.namespace.id", snapshot.namespaceId()).put("skillhub.visibility", snapshot.visibility().name())
                .put("skillhub.actor.type", snapshot.actorType()).put("skillhub.source", snapshot.source())
                .put("log.record.uid", uid);
        if ("user".equals(snapshot.actorType()) && snapshot.actorId() != null && !snapshot.actorId().isBlank()) {
            attributes.put("user.id", bounded(snapshot.actorId()));
        }
        return Optional.of(new MappedRecord(snapshot.occurredAt(), attributes.build(), uid));
    }

    static String bounded(String value) {
        return value.length() <= 256 ? value : value.substring(0, 256);
    }

    private static void id(AttributesBuilder attributes, String kind, Long value) {
        if (value != null) { attributes.put("skillhub." + kind + ".id", value); }
    }

    public record MappedRecord(Instant timestamp, Attributes attributes, String uid) {}
}
