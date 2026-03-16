package com.iflytek.skillhub.domain.security;

import java.util.Optional;

public interface SecurityAuditRepository {
    SecurityAudit save(SecurityAudit audit);
    Optional<SecurityAudit> findBySkillVersionId(Long skillVersionId);
    Optional<SecurityAudit> findByScanId(String scanId);
    boolean existsBySkillVersionId(Long skillVersionId);
}
