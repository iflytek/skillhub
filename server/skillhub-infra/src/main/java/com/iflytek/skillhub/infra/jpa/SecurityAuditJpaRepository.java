package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityAudit;
import com.iflytek.skillhub.domain.security.SecurityAuditRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecurityAuditJpaRepository extends JpaRepository<SecurityAudit, Long>, SecurityAuditRepository {

    @Override
    Optional<SecurityAudit> findBySkillVersionId(Long skillVersionId);

    @Override
    Optional<SecurityAudit> findByScanId(String scanId);

    @Override
    boolean existsBySkillVersionId(Long skillVersionId);

    @Override
    @Query("""
            SELECT sa FROM SecurityAudit sa
            WHERE sa.skillVersionId = :versionId
              AND sa.scannerType = :scannerType
              AND sa.deletedAt IS NULL
            ORDER BY sa.createdAt DESC
            LIMIT 1
            """)
    Optional<SecurityAudit> findLatestActiveByVersionIdAndScannerType(
            @Param("versionId") Long skillVersionId,
            @Param("scannerType") ScannerType scannerType);

    @Override
    @Query("""
            SELECT sa FROM SecurityAudit sa
            WHERE sa.skillVersionId = :versionId
              AND sa.deletedAt IS NULL
              AND sa.createdAt = (
                  SELECT MAX(sa2.createdAt) FROM SecurityAudit sa2
                  WHERE sa2.skillVersionId = sa.skillVersionId
                    AND sa2.scannerType = sa.scannerType
                    AND sa2.deletedAt IS NULL
              )
            ORDER BY sa.scannerType
            """)
    List<SecurityAudit> findLatestActiveByVersionId(@Param("versionId") Long skillVersionId);

    @Override
    @Query("""
            SELECT sa FROM SecurityAudit sa
            WHERE sa.skillVersionId = :versionId
              AND sa.deletedAt IS NULL
            ORDER BY sa.createdAt DESC
            """)
    List<SecurityAudit> findAllActiveBySkillVersionId(@Param("versionId") Long skillVersionId);

    @Override
    default List<SecurityAudit> saveAll(List<SecurityAudit> audits) {
        return saveAllAndFlush(audits);
    }
}
