package com.iflytek.skillhub.domain.skill.metadata;

import java.util.List;

/**
 * Version-scoped mapping from a skill to an external compliance or security standard.
 */
public record ComplianceMapping(
        String standard,
        String version,
        String controlId,
        String title,
        List<ComplianceEvidence> evidence
) {}
