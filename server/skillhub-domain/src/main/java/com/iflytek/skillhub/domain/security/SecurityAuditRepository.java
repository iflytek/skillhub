package com.iflytek.skillhub.domain.security;

import java.util.List;
import java.util.Optional;

public interface SecurityAuditRepository {
    SecurityAudit save(SecurityAudit audit);

    List<SecurityAudit> saveAll(List<SecurityAudit> audits);

    Optional<SecurityAudit> findBySkillVersionId(Long skillVersionId);

    Optional<SecurityAudit> findByScanId(String scanId);

    boolean existsBySkillVersionId(Long skillVersionId);

    /**
     * Find the latest active audit for a version + scanner type combination.
     */
    Optional<SecurityAudit> findLatestActiveByVersionIdAndScannerType(Long skillVersionId, ScannerType scannerType);

    /**
     * Find all active audits for a version (all scanner types, latest per type).
     */
    List<SecurityAudit> findLatestActiveByVersionId(Long skillVersionId);

    /**
     * Find all active audits for a version (all records, all types).
     */
    List<SecurityAudit> findAllActiveBySkillVersionId(Long skillVersionId);

    /**
     * Physically delete all audit records for a version (used during hard delete).
     */
    void deleteBySkillVersionId(Long skillVersionId);
}
