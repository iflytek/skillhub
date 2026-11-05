package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.notification.email.*;
import jakarta.persistence.*;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationEmailPreferencePersistence implements NotificationEmailPreferenceRepository {
    private final EntityManager entityManager;
    public NotificationEmailPreferencePersistence(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
    public List<NotificationEmailPreference> findByUserIdOrderByCategoryAsc(String id) {
        return entityManager
                .createQuery("select p from NotificationEmailPreference p where p.userId=:id order by p.category",
                        NotificationEmailPreference.class)
                .setParameter("id", id)
                .getResultList();
    }
    public List<NotificationEmailPreference> lockAll(String id) {
        var rows = entityManager
                           .createQuery(
                                   "select p from NotificationEmailPreference p where p.userId=:id order by p.category",
                                   NotificationEmailPreference.class)
                           .setParameter("id", id)
                           .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                           .getResultList();
        rows.forEach(row -> entityManager.refresh(row, LockModeType.PESSIMISTIC_WRITE));
        return rows;
    }
    public NotificationEmailPreference save(NotificationEmailPreference preference) {
        return entityManager.merge(preference);
    }
}
