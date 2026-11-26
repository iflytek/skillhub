package com.iflytek.skillhub.auth.repository;

import com.iflytek.skillhub.auth.entity.ApiToken;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for personal API tokens and token listings scoped to one user.
 */
@Repository
public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {
    @Query(value = "SELECT * FROM api_token WHERE id = :id AND user_id = :userId AND revoked_at IS NULL FOR UPDATE",
            nativeQuery = true)
    Optional<ApiToken> findUnrevokedForUpdate(@Param("id") Long id, @Param("userId") String userId);

    @Modifying
    @Query("update ApiToken t set t.lastUsedAt = :at where t.id = :id")
    void updateLastUsedAt(@Param("id") Long id, @Param("at") Instant at);

    Optional<ApiToken> findByTokenHash(String tokenHash);
    List<ApiToken> findByUserId(String userId);
    List<ApiToken> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(String userId);
    Page<ApiToken> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(String userId, Pageable pageable);
    @Query("""
            select t from ApiToken t
            where t.userId = :userId and t.revokedAt is null
              and (:query = '' or locate(lower(:query), lower(t.name)) > 0)
              and (:status = 'ALL'
                   or (:status = 'ACTIVE' and (t.expiresAt is null or t.expiresAt > :now))
                   or (:status = 'EXPIRED' and t.expiresAt <= :now))
            order by t.createdAt desc, t.id desc
            """)
    Page<ApiToken> searchActiveTokens(@Param("userId") String userId,
                                     @Param("query") String query,
                                     @Param("status") String status,
                                     @Param("now") Instant now,
                                     Pageable pageable);

    boolean existsByUserIdAndRevokedAtIsNullAndNameIgnoreCase(String userId, String name);
    Optional<ApiToken> findByUserIdAndNameIgnoreCaseAndRevokedAtIsNull(String userId, String name);
}
