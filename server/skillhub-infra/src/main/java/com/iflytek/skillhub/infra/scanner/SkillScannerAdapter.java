package com.iflytek.skillhub.infra.scanner;

import com.iflytek.skillhub.domain.security.*;
import com.iflytek.skillhub.infra.http.HttpClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class SkillScannerAdapter implements SecurityScanner {

    private static final Logger log = LoggerFactory.getLogger(SkillScannerAdapter.class);
    private static final String SCANNER_TYPE = "skill-scanner";
    private static final String MODE_LOCAL = "local";

    private final SkillScannerService skillScannerService;
    private final String scanMode;

    public SkillScannerAdapter(SkillScannerService skillScannerService, String scanMode) {
        this.skillScannerService = skillScannerService;
        this.scanMode = scanMode;
        log.info("SkillScannerAdapter initialized with mode={}", scanMode);
    }

    @Override
    public SecurityScanResponse scan(SecurityScanRequest request) {
        log.info("Starting security scan for versionId={}, mode={}", request.skillVersionId(), scanMode);

        try {
            SkillScannerApiResponse apiResponse;
            if (MODE_LOCAL.equalsIgnoreCase(scanMode)) {
                apiResponse = skillScannerService.scanDirectory(request.skillPackagePath());
            } else {
                apiResponse = skillScannerService.scanUpload(Path.of(request.skillPackagePath()));
            }

            return mapToResponse(apiResponse);
        } catch (HttpClientException e) {
            log.error("Security scan failed for versionId={}: {}", request.skillVersionId(), e.getMessage());
            throw new SecurityScanException("Security scan failed", e);
        }
    }

    @Override
    public boolean isHealthy() {
        return skillScannerService.isHealthy();
    }

    @Override
    public String getScannerType() {
        return SCANNER_TYPE;
    }

    private SecurityScanResponse mapToResponse(SkillScannerApiResponse apiResponse) {
        SecurityVerdict verdict = mapVerdict(apiResponse.isSafe(), apiResponse.maxSeverity());
        List<SecurityFinding> findings = mapFindings(apiResponse.findings());

        return new SecurityScanResponse(
                apiResponse.scanId(),
                verdict,
                apiResponse.findingsCount() != null ? apiResponse.findingsCount() : 0,
                apiResponse.maxSeverity(),
                findings,
                apiResponse.scanDurationSeconds() != null ? apiResponse.scanDurationSeconds() : 0.0
        );
    }

    private SecurityVerdict mapVerdict(Boolean isSafe, String maxSeverity) {
        if (Boolean.TRUE.equals(isSafe)) {
            return SecurityVerdict.SAFE;
        }
        if (maxSeverity == null) {
            return SecurityVerdict.SUSPICIOUS;
        }
        return switch (maxSeverity.toUpperCase()) {
            case "CRITICAL" -> SecurityVerdict.BLOCKED;
            case "HIGH" -> SecurityVerdict.DANGEROUS;
            case "MEDIUM" -> SecurityVerdict.SUSPICIOUS;
            default -> SecurityVerdict.SUSPICIOUS;
        };
    }

    private List<SecurityFinding> mapFindings(List<SkillScannerApiResponse.Finding> apiFindings) {
        if (apiFindings == null) {
            return Collections.emptyList();
        }
        return apiFindings.stream()
                .map(f -> new SecurityFinding(
                        f.ruleId(),
                        f.severity(),
                        f.category(),
                        f.title(),
                        f.message(),
                        f.location() != null ? f.location().file() : null,
                        f.location() != null ? f.location().line() : null,
                        f.codeSnippet()
                ))
                .toList();
    }
}
