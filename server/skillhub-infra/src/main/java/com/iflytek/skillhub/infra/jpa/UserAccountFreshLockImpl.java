package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.user.UserAccount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.hibernate.Session;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lock the actual database row, then refresh any preloaded account from the persistence context.
 * The existing H2 test profile inherits a PostgreSQL Hibernate dialect, so row-lock SQL must use
 * the connection's product rather than that dialect. Production PostgreSQL keeps NO KEY UPDATE.
 */
public class UserAccountFreshLockImpl implements UserAccountFreshLock {
    private final EntityManager entityManager;

    public UserAccountFreshLockImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public Optional<UserAccount> findLockedFreshById(String id) {
        String database = entityManager.unwrap(Session.class)
                                  .doReturningWork(connection -> connection.getMetaData().getDatabaseProductName());
        String lock = "PostgreSQL".equalsIgnoreCase(database) ? " FOR NO KEY UPDATE" : " FOR UPDATE";
        var rows = entityManager.createNativeQuery("SELECT id FROM user_account WHERE id = :id" + lock)
                           .setParameter("id", id)
                           .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        UserAccount user = entityManager.find(UserAccount.class, id);
        if (user != null) {
            // The native lock remains held by this transaction; avoid a second dialect-derived lock.
            entityManager.refresh(user, LockModeType.NONE);
        }
        return Optional.ofNullable(user);
    }
}
