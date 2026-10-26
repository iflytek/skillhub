package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.notification.email.*;
import jakarta.persistence.*;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Account lock serializes creation; refresh avoids a stale contact from an earlier read. */
@Repository
public class NotificationEmailContactPersistence implements NotificationEmailContactRepository {
    private final EntityManager entityManager;
    public NotificationEmailContactPersistence(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
    public Optional<NotificationEmailContact> findById(String id) {
        return Optional.ofNullable(entityManager.find(NotificationEmailContact.class, id));
    }
    public NotificationEmailContact lockOrCreate(String id) {
        entityManager
                .createNativeQuery(
                        "INSERT INTO notification_email_contact(user_id) VALUES (:id) ON CONFLICT (user_id) DO NOTHING")
                .setParameter("id", id)
                .executeUpdate();
        var contact = entityManager.find(NotificationEmailContact.class, id, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(contact, LockModeType.PESSIMISTIC_WRITE);
        return contact;
    }
    public NotificationEmailContact save(NotificationEmailContact contact) {
        return entityManager.merge(contact);
    }
}
