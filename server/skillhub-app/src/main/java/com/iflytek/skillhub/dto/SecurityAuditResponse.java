package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.security.SecurityFinding;
import com.iflytek.skillhub.domain.security.SecurityVerdict;

import java.time.Instant;
import java.util.List;

public record SecurityAuditResponse(
        Long id,
        String scanId,
        String scannerType,
        SecurityVerdict verdict,
        Boolean isSafe,
        String maxSeverity,
        Integer findingsCount,
        List<SecurityFinding> findings,
        Double scanDurationSeconds,
        Instant scannedAt,
        Instant createdAt
) {
}
