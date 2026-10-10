package com.iflytek.skillhub.dto;

import java.util.List;

public record ComplianceMappingResponse(
        String standard,
        String version,
        String controlId,
        String title,
        List<ComplianceEvidenceResponse> evidence
) {}
