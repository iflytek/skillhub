package com.iflytek.skillhub.auth.merge;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA repository for pending account-merge requests between two platform identities.
 */
@Repository
public interface AccountMergeRequestRepository extends JpaRepository<AccountMergeRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountMergeRequest> findByIdAndPrimaryUserId(Long id, String primaryUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from AccountMergeRequest request where request.id = :id")
    Optional<AccountMergeRequest> findLockedById(@Param("id") Long id);

    Optional<AccountMergeRequest> findBySecondaryUserIdAndStatus(String secondaryUserId, String status);

}
