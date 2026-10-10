package com.iflytek.skillhub.auth.settings;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExternalRoleGrantRuleRepository extends JpaRepository<ExternalRoleGrantRule, Long> {
    List<ExternalRoleGrantRule> findAllByOrderByCreatedAtDesc();

    boolean existsByProviderCodeAndNormalizedEmailAndStatus(
            String providerCode, String normalizedEmail, ExternalRoleGrantRule.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rule from ExternalRoleGrantRule rule where rule.providerCode = :provider "
            + "and rule.normalizedEmail = :email and rule.status = :status")
    Optional<ExternalRoleGrantRule> lockByIdentityAndStatus(
            @Param("provider") String provider,
            @Param("email") String email,
            @Param("status") ExternalRoleGrantRule.Status status);
}
