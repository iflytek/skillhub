package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.notification.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceJpaRepository extends JpaRepository<NotificationPreference, Long>, NotificationPreferenceRepository {
    List<NotificationPreference> findByUserId(String userId);
    Optional<NotificationPreference> findByUserIdAndCategoryAndChannel(
            String userId, NotificationCategory category, NotificationChannel channel);
}
