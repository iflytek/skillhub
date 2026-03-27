package com.iflytek.skillhub.infra.repository;

import com.iflytek.skillhub.domain.auth.PasswordResetRequest;
import com.iflytek.skillhub.domain.auth.PasswordResetRequestRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class PasswordResetRequestRepositoryAdapter implements PasswordResetRequestRepository {
    private final JpaPasswordResetRequestRepository jpaRepository;

    public PasswordResetRequestRepositoryAdapter(JpaPasswordResetRequestRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PasswordResetRequest save(PasswordResetRequest request) {
        return jpaRepository.save(request);
    }

    @Override
    public Optional<PasswordResetRequest> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<PasswordResetRequest> findAllPendingNotExpired(Instant now) {
        return jpaRepository.findAllPendingNotExpired(now);
    }

    @Override
    @Transactional
    public void invalidatePendingRequests(String userId) {
        jpaRepository.invalidatePendingRequests(userId);
    }
}
