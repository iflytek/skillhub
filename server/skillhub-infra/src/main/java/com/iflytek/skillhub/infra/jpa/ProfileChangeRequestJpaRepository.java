package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.user.ProfileChangeRequest;
import com.iflytek.skillhub.domain.user.ProfileChangeRequestRepository;
import com.iflytek.skillhub.domain.user.ProfileChangeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA implementation of {@link ProfileChangeRequestRepository}.
 * Spring Data derives query methods from method names automatically.
 */
@Repository
public interface ProfileChangeRequestJpaRepository
        extends JpaRepository<ProfileChangeRequest, Long>, ProfileChangeRequestRepository {

    @Override
    List<ProfileChangeRequest> findByUserIdAndStatus(String userId, ProfileChangeStatus status);
}
