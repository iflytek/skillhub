package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA-backed user-account repository that provides filtered admin search over account records.
 */
@Repository
public interface UserAccountJpaRepository
        extends JpaRepository<UserAccount, String>, JpaSpecificationExecutor<UserAccount>, UserAccountRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserAccount user where user.id = :id")
    Optional<UserAccount> findLockedById(@Param("id") String id);

    @Override
    @Query("""
        SELECT u
        FROM UserAccount u
        WHERE (:status IS NULL OR u.status = :status)
          AND (
            :keyword IS NULL
            OR lower(u.displayName) LIKE lower(concat('%', :keyword, '%'))
            OR lower(coalesce(u.email, '')) LIKE lower(concat('%', :keyword, '%'))
            OR lower(u.id) LIKE lower(concat('%', :keyword, '%'))
          )
        """)
    Page<UserAccount> search(@Param("keyword") String keyword,
                             @Param("status") UserStatus status,
                             Pageable pageable);
}
