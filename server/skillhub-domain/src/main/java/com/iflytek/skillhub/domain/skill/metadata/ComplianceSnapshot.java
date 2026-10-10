package com.iflytek.skillhub.domain.skill.metadata;

import java.util.List;

/**
 * Immutable normalized compliance metadata bound to a single skill version.
 */
public record ComplianceSnapshot(
        String schemaVersion,
        List<ComplianceMapping> items,
        String digest
) {
    public static final String CURRENT_SCHEMA_VERSION = "1.0";
}
