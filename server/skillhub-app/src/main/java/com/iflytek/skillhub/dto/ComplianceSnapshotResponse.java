package com.iflytek.skillhub.dto;

import java.util.List;

public record ComplianceSnapshotResponse(
        String schemaVersion,
        List<ComplianceMappingResponse> items,
        String digest
) {}
