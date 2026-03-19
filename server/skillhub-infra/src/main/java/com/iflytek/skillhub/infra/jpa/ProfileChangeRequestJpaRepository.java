package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.user.ProfileChangeRequest;
import com.iflytek.skillhub.domain.user.ProfileChangeRequestRepository;
import com.iflytek.skillhub.domain.user.ProfileChangeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * JPA implementation of {@link ProfileChangeRequestRepository}.
 * Spring Data derives query methods from method names automatically.
 */
@Repository
public interface ProfileChangeRequestJpaRepository
        extends JpaRepository<ProfileChangeRequest, Long>, ProfileChangeRequestRepository {

    @Override
    List<ProfileChangeRequest> findByUserIdAndStatus(String userId, ProfileChangeStatus status);

    @Override
    Page<ProfileChangeRequest> findByStatusOrderByCreatedAtDesc(ProfileChangeStatus status, Pageable pageable);

    @Override
    Optional<ProfileChangeRequest> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
            String userId, Collection<ProfileChangeStatus> statuses);
}
