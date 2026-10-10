package com.iflytek.skillhub.domain.security;

import java.util.List;

public record SecurityScanResponse(
        String scanId,
        SecurityVerdict verdict,
        int findingsCount,
        String maxSeverity,
        List<SecurityFinding> findings,
        double scanDurationSeconds
) {
}
