package com.iflytek.skillhub.controller.portal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.security.SecurityAudit;
import com.iflytek.skillhub.domain.security.SecurityAuditRepository;
import com.iflytek.skillhub.domain.security.SecurityFinding;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SecurityAuditResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public ApiResponse<SecurityAuditResponse> getSecurityAudit(
            @PathVariable Long skillId,
            @PathVariable Long versionId) {

        SecurityAudit audit = securityAuditRepository.findBySkillVersionId(versionId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "security_audit.not_found", versionId));

        List<SecurityFinding> findings = deserializeFindings(audit.getFindings());

        SecurityAuditResponse response = new SecurityAuditResponse(
                audit.getId(),
                audit.getScanId(),
                audit.getScannerType(),
                audit.getVerdict(),
                audit.getIsSafe(),
                audit.getMaxSeverity(),
                audit.getFindingsCount(),
                findings,
                audit.getScanDurationSeconds(),
                audit.getScannedAt(),
                audit.getCreatedAt()
        );

        return ok("security_audit.found", response);
    }

    private List<SecurityFinding> deserializeFindings(String findingsJson) {
        if (findingsJson == null || findingsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(findingsJson, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
