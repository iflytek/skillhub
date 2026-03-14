package com.iflytek.skillhub.auth.repository;

import com.iflytek.skillhub.auth.entity.ApiToken;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {
    Optional<ApiToken> findByTokenHash(String tokenHash);
    List<ApiToken> findByUserId(String userId);
    List<ApiToken> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(String userId);
    Page<ApiToken> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(String userId, Pageable pageable);
    boolean existsByUserIdAndRevokedAtIsNullAndNameIgnoreCase(String userId, String name);
}
