package com.iflytek.skillhub.domain.governance;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Domain repository contract for user-facing governance notifications.
 */
public interface UserNotificationRepository {
    UserNotification save(UserNotification notification);
    Optional<UserNotification> findById(Long id);
    List<UserNotification> findByUserIdOrderByCreatedAtDesc(String userId);
    Page<UserNotification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    long countByUserIdAndStatus(String userId, UserNotificationStatus status);
}
