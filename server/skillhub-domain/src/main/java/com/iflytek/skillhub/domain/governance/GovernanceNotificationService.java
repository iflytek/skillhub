package com.iflytek.skillhub.domain.governance;

import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists and manages governance notifications delivered to end users.
 */
@Service
public class GovernanceNotificationService {

    private final UserNotificationRepository userNotificationRepository;
    private final Clock clock;

    public GovernanceNotificationService(UserNotificationRepository userNotificationRepository, Clock clock) {
        this.userNotificationRepository = userNotificationRepository;
        this.clock = clock;
    }

    @Transactional
    public UserNotification notifyUser(String userId,
                                       String category,
                                       String entityType,
                                       Long entityId,
                                       String title,
                                       String bodyJson) {
        Instant createdAt = Instant.now(clock);
        return userNotificationRepository.save(new UserNotification(
                userId,
                category,
                entityType,
                entityId,
                title,
                bodyJson,
                createdAt
        ));
    }

    @Transactional(readOnly = true)
    public List<UserNotification> listNotifications(String userId) {
        return userNotificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public UserNotification markRead(Long notificationId, String userId) {
        UserNotification notification = userNotificationRepository.findById(notificationId)
                .orElseThrow(() -> new DomainNotFoundException("error.notification.notFound", notificationId));
        if (!notification.getUserId().equals(userId)) {
            throw new DomainForbiddenException("error.notification.noPermission");
        }
        notification.markRead(Instant.now(clock));
        return userNotificationRepository.save(notification);
    }
}
