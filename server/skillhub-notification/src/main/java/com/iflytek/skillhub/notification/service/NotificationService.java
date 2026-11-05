package com.iflytek.skillhub.notification.service;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.notification.domain.*;
import com.iflytek.skillhub.notification.email.delivery.*;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceService preferenceService;
    private final Clock clock;
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private TransactionTemplate isolatedTransaction;
    private ObjectProvider<NotificationEmailEnqueuer> emailEnqueuers;

    @Autowired
    public NotificationService(NotificationRepository repository, NotificationPreferenceService preferences,
            Clock clock, PlatformTransactionManager transactionManager,
            ObjectProvider<NotificationEmailEnqueuer> emailEnqueuers) {
        this(repository, preferences, clock);
        this.isolatedTransaction = new TransactionTemplate(transactionManager);
        this.isolatedTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.emailEnqueuers = emailEnqueuers;
    }

    public NotificationService(NotificationRepository notificationRepository,
            NotificationPreferenceService preferenceService, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.preferenceService = preferenceService;
        this.clock = clock;
    }

    /** Legacy direct callers get one new occurrence; listeners pass stable source event identities explicitly. */
    public void create(String recipientId, NotificationCategory category, String eventType, String title,
            String bodyJson, String entityType, Long entityId) {
        create(recipientId, category, eventType, title, bodyJson, entityType, entityId,
                java.util.UUID.randomUUID().toString(), clock.instant());
    }

    public void create(String recipientId, NotificationCategory category, String eventType, String title,
            String bodyJson, String entityType, Long entityId, String eventId, Instant occurredAt) {
        try {
            Runnable write = () -> {
                if (preferenceService.isEnabled(recipientId, category, NotificationChannel.IN_APP))
                    notificationRepository.save(new Notification(
                            recipientId, category, eventType, title, bodyJson, entityType, entityId, occurredAt));
            };
            if (isolatedTransaction == null)
                write.run();
            else
                isolatedTransaction.executeWithoutResult(status -> write.run());
        } catch (RuntimeException ex) {
            log.error("In-app notification branch failed: type={} error={}", eventType, ex.getClass().getSimpleName());
        }
        try {
            if (emailEnqueuers != null) {
                var email = emailEnqueuers.getIfAvailable();
                if (email != null)
                    email.enqueue(new NotificationEmailCommand(recipientId, category, eventType,
                            eventType + ":" + eventId, occurredAt, bodyJson, entityType, entityId));
            }
        } catch (RuntimeException ex) {
            log.error("Notification email inbox failed: type={} error={}", eventType, ex.getClass().getSimpleName());
        }
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(String recipientId, NotificationCategory category, Pageable pageable) {
        if (category != null) {
            return notificationRepository.findByRecipientIdAndCategory(recipientId, category, pageable);
        }
        return notificationRepository.findByRecipientId(recipientId, pageable);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String recipientId) {
        return notificationRepository.countByRecipientIdAndStatus(recipientId, NotificationStatus.UNREAD);
    }

    @Transactional
    public void markRead(Long notificationId, String userId) {
        Notification notification =
                notificationRepository.findById(notificationId)
                        .orElseThrow(() -> new DomainNotFoundException("error.notification.notFound", notificationId));
        if (!notification.getRecipientId().equals(userId)) {
            throw new DomainForbiddenException("error.notification.noPermission");
        }
        notification.markRead(Instant.now(clock));
        notificationRepository.save(notification);
    }

    @Transactional
    public int markAllRead(String userId) {
        return notificationRepository.markAllReadByRecipientId(userId, Instant.now(clock));
    }

    @Transactional
    public void deleteRead(Long notificationId, String userId) {
        int deleted = notificationRepository.deleteByIdAndRecipientIdAndStatus(
                notificationId, userId, NotificationStatus.READ);
        if (deleted == 0) {
            throw new DomainBadRequestException("error.notification.readNotFound", notificationId);
        }
    }
}
