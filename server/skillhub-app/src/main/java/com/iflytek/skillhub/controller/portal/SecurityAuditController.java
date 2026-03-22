package com.iflytek.skillhub.controller.portal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityAudit;
import com.iflytek.skillhub.domain.security.SecurityAuditRepository;
import com.iflytek.skillhub.domain.security.SecurityFinding;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SecurityAuditResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/v1/skills/{skillId}/versions/{versionId}/security-audit")
public class SecurityAuditController extends BaseApiController {

    private final SecurityAuditRepository securityAuditRepository;
    private final ObjectMapper objectMapper;

    public SecurityAuditController(SecurityAuditRepository securityAuditRepository,
                                   ApiResponseFactory responseFactory,
                                   ObjectMapper objectMapper) {
        super(responseFactory);
        this.securityAuditRepository = securityAuditRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<List<SecurityAuditResponse>> getSecurityAudits(
            @PathVariable Long skillId,
            @PathVariable Long versionId,
            @RequestParam(required = false) String scannerType) {

        List<SecurityAudit> audits;
        if (scannerType != null && !scannerType.isBlank()) {
            ScannerType type = ScannerType.fromValue(scannerType);
            audits = securityAuditRepository
                    .findLatestActiveByVersionIdAndScannerType(versionId, type)
                    .map(List::of)
                    .orElse(List.of());
        } else {
            audits = securityAuditRepository.findLatestActiveByVersionId(versionId);
        }

        List<SecurityAuditResponse> responses = audits.stream()
                .map(this::toResponse)
                .toList();
        return ok("security_audit.found", responses);
    }

    private SecurityAuditResponse toResponse(SecurityAudit audit) {
        return new SecurityAuditResponse(
                audit.getId(),
                audit.getScanId(),
                audit.getScannerType().getValue(),
                audit.getVerdict(),
                audit.getIsSafe(),
                audit.getMaxSeverity(),
                audit.getFindingsCount(),
                deserializeFindings(audit.getFindings()),
                audit.getScanDurationSeconds(),
                audit.getScannedAt(),
                audit.getCreatedAt()
        );
    }

    private List<SecurityFinding> deserializeFindings(String findingsJson) {
        if (findingsJson == null || findingsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(findingsJson, new TypeReference<List<SecurityFinding>>() {
            });
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }
}
