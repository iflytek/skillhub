package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.governance.UserNotification;
import com.iflytek.skillhub.domain.governance.UserNotificationRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA-backed repository for notifications shown in the governance inbox.
 */
public interface UserNotificationJpaRepository extends JpaRepository<UserNotification, Long>, UserNotificationRepository {
    List<UserNotification> findByUserIdOrderByCreatedAtDesc(String userId);
    Page<UserNotification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
