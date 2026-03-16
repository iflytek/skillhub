package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.governance.UserNotification;
import com.iflytek.skillhub.domain.governance.UserNotificationRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationJpaRepository extends JpaRepository<UserNotification, Long>, UserNotificationRepository {
    List<UserNotification> findByUserIdOrderByCreatedAtDesc(String userId);
}
