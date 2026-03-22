package com.iflytek.skillhub.infra.scanner;

import com.iflytek.skillhub.domain.security.SecurityFinding;
import com.iflytek.skillhub.domain.security.SecurityScanRequest;
import com.iflytek.skillhub.domain.security.SecurityScanResponse;
import com.iflytek.skillhub.domain.security.SecurityScanner;
import com.iflytek.skillhub.domain.security.SecurityVerdict;
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
    private final ScanOptions scanOptions;

    public SkillScannerAdapter(SkillScannerService skillScannerService, String scanMode, ScanOptions scanOptions) {
        this.skillScannerService = skillScannerService;
        this.scanMode = scanMode;
        this.scanOptions = scanOptions;
    }

    @Override
    public SecurityScanResponse scan(SecurityScanRequest request) {
        log.info("Starting security scan for versionId={}, mode={}", request.skillVersionId(), scanMode);
        try {
            SkillScannerApiResponse apiResponse = MODE_LOCAL.equalsIgnoreCase(scanMode)
                    ? skillScannerService.scanDirectory(request.skillPackagePath(), scanOptions)
                    : skillScannerService.scanUpload(Path.of(request.skillPackagePath()), scanOptions);
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
        return new SecurityScanResponse(
                apiResponse.scanId(),
                mapVerdict(apiResponse.isSafe(), apiResponse.maxSeverity()),
                apiResponse.findingsCount() != null ? apiResponse.findingsCount() : 0,
                apiResponse.maxSeverity(),
                mapFindings(apiResponse.findings()),
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
                .map(finding -> new SecurityFinding(
                        finding.ruleId(),
                        finding.severity(),
                        finding.category(),
                        finding.title(),
                        finding.message(),
                        finding.location() != null ? finding.location().file() : null,
                        finding.location() != null ? finding.location().line() : null,
                        finding.codeSnippet()
                ))
                .toList();
    }
}
