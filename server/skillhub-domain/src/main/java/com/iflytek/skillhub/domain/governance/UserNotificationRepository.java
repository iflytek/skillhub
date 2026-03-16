package com.iflytek.skillhub.domain.governance;

import java.util.List;
import java.util.Optional;

public interface UserNotificationRepository {
    UserNotification save(UserNotification notification);
    Optional<UserNotification> findById(Long id);
    List<UserNotification> findByUserIdOrderByCreatedAtDesc(String userId);
}
