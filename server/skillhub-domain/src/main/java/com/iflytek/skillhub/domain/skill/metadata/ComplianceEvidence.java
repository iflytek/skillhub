package com.iflytek.skillhub.domain.skill.metadata;

/**
 * Normalized evidence reference attached to one compliance mapping.
 */
public record ComplianceEvidence(
        String type,
        String path,
        String url,
        String sha256
) {}
