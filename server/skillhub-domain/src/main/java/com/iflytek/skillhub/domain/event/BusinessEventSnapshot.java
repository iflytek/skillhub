package com.iflytek.skillhub.domain.event;

import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import java.time.Instant;

/**
 * Immutable producer context, never an entity or a request object. No telemetry dependency.
 * The timestamp is event occurrence (immediately before publication), not transaction commit time.
 */
public record BusinessEventSnapshot(Long namespaceId, SkillVisibility visibility, String actorId,
                                    String actorType, String source, Instant occurredAt) {
    public static BusinessEventSnapshot capture(Skill skill, String actorId, String source) {
        return capture(skill, actorId, "user", source);
    }

    public static BusinessEventSnapshot capture(Skill skill, String actorId, String actorType, String source) {
        // BuiltinSkillInitializer reserves this identity and verifies isSystemAccount before publishing.
        // It uses the ordinary privileged publish path; never mislabel that service account as human.
        if ("builtin-skill-publisher".equals(actorId)) { actorType = "system"; }
        return new BusinessEventSnapshot(skill.getNamespaceId(), skill.getVisibility(), actorId,
                actorType, source, Instant.now());
    }
}
