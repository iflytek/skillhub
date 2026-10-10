package com.iflytek.skillhub.dto;

public record ComplianceEvidenceResponse(
        String type,
        String path,
        String url,
        String sha256
) {}
