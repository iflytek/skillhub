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
import java.util.Map;

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
        log.info("Scanner API raw response: scanId={}, skillName={}, isSafe={}, maxSeverity={}, findingsCount={}, duration={}s",
                apiResponse.scanId(), apiResponse.skillName(), apiResponse.isSafe(),
                apiResponse.maxSeverity(), apiResponse.findingsCount(), apiResponse.scanDurationSeconds());

        SecurityScanResponse response = new SecurityScanResponse(
                apiResponse.scanId(),
                mapVerdict(apiResponse.isSafe(), apiResponse.maxSeverity()),
                apiResponse.findingsCount() != null ? apiResponse.findingsCount() : 0,
                apiResponse.maxSeverity(),
                mapFindings(apiResponse.findings()),
                apiResponse.scanDurationSeconds() != null ? apiResponse.scanDurationSeconds() : 0.0
        );

        log.info("Mapped response: scanId={}, verdict={}, findingsCount={}, maxSeverity={}",
                response.scanId(), response.verdict(), response.findingsCount(), response.maxSeverity());
        if (!response.findings().isEmpty()) {
            for (SecurityFinding f : response.findings()) {
                log.debug("Mapped finding: ruleId={}, severity={}, category={}, filePath={}, lineNumber={}",
                        f.ruleId(), f.severity(), f.category(), f.filePath(), f.lineNumber());
            }
        }

        return response;
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
                        finding.description(),
                        finding.filePath(),
                        finding.lineNumber(),
                        finding.snippet(),
                        finding.remediation(),
                        finding.analyzer(),
                        finding.metadata() != null ? finding.metadata() : Map.of()
                ))
                .toList();
    }
}
