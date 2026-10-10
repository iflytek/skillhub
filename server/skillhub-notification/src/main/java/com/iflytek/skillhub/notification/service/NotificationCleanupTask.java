package com.iflytek.skillhub.notification.service;

import com.iflytek.skillhub.notification.domain.NotificationRepository;
import com.iflytek.skillhub.notification.domain.NotificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class NotificationCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupTask.class);

    private final NotificationRepository notificationRepository;
    private final Clock clock;
    private final int readRetentionDays;
    private final int unreadRetentionDays;

    public NotificationCleanupTask(NotificationRepository notificationRepository,
                                    Clock clock,
                                    @Value("${skillhub.notification.cleanup.read-retention-days:30}") int readRetentionDays,
                                    @Value("${skillhub.notification.cleanup.unread-retention-days:90}") int unreadRetentionDays) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
        this.readRetentionDays = readRetentionDays;
        this.unreadRetentionDays = unreadRetentionDays;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void cleanup() {
        Instant now = Instant.now(clock);
        int readDeleted = notificationRepository.deleteByStatusAndCreatedAtBefore(
                NotificationStatus.READ, now.minus(Duration.ofDays(readRetentionDays)));
        int unreadDeleted = notificationRepository.deleteByStatusAndCreatedAtBefore(
                NotificationStatus.UNREAD, now.minus(Duration.ofDays(unreadRetentionDays)));
        log.info("Notification cleanup: deleted {} read, {} unread", readDeleted, unreadDeleted);
    }
}
