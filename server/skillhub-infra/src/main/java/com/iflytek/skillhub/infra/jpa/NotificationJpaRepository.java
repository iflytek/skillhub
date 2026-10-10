package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.notification.domain.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface NotificationJpaRepository extends JpaRepository<Notification, Long>, NotificationRepository {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndCategoryOrderByCreatedAtDesc(String recipientId, NotificationCategory category, Pageable pageable);

    long countByRecipientIdAndStatus(String recipientId, NotificationStatus status);

    @Override
    default Page<Notification> findByRecipientId(String recipientId, Pageable pageable) {
        return findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
    }

    @Override
    default Page<Notification> findByRecipientIdAndCategory(String recipientId, NotificationCategory category, Pageable pageable) {
        return findByRecipientIdAndCategoryOrderByCreatedAtDesc(recipientId, category, pageable);
    }

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.status = 'READ', n.readAt = :readAt WHERE n.recipientId = :recipientId AND n.status = 'UNREAD'")
    int markAllReadByRecipientId(String recipientId, Instant readAt);

    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.recipientId = :recipientId AND n.status = :status")
    int deleteByIdAndRecipientIdAndStatus(Long id, String recipientId, NotificationStatus status);

    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.status = :status AND n.createdAt < :before")
    int deleteByStatusAndCreatedAtBefore(NotificationStatus status, Instant before);
}
