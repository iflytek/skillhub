package com.iflytek.skillhub.auth.merge;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountMergeRequestRepository extends JpaRepository<AccountMergeRequest, Long> {

    Optional<AccountMergeRequest> findByIdAndPrimaryUserId(Long id, String primaryUserId);

    boolean existsBySecondaryUserIdAndStatus(String secondaryUserId, String status);
}
