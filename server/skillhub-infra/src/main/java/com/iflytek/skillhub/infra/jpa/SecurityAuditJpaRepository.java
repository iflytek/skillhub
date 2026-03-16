package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.security.SecurityAudit;
import com.iflytek.skillhub.domain.security.SecurityAuditRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SecurityAuditJpaRepository extends JpaRepository<SecurityAudit, Long>, SecurityAuditRepository {

    @Override
    Optional<SecurityAudit> findBySkillVersionId(Long skillVersionId);

    @Override
    Optional<SecurityAudit> findByScanId(String scanId);

    @Override
    boolean existsBySkillVersionId(Long skillVersionId);
}
