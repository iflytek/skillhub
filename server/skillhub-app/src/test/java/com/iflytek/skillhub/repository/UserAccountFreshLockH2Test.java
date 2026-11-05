package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/** The real H2 test database must execute the fresh-lock adapter under existing test configuration. */
@DataJpaTest
@ActiveProfiles("test")
class UserAccountFreshLockH2Test {
    @Autowired UserAccountRepository users;
    @Autowired EntityManager em;

    @Test
    void freshLockReadsAndRefreshesOnActualH2Database() {
        String database = em.unwrap(org.hibernate.Session.class)
                                  .doReturningWork(connection -> connection.getMetaData().getDatabaseProductName());
        assertThat(database).isEqualTo("H2");
        String id = "h2-fresh-" + UUID.randomUUID();
        users.save(new UserAccount(id, "H2", "a@example.test", null));
        em.flush();
        em.clear();
        UserAccount locked = users.findLockedFreshById(id).orElseThrow();
        assertThat(locked.getEmailRevision()).isZero();
        locked.setEmail("b@example.test");
        users.save(locked);
        em.flush();
        em.clear();
        UserAccount reloaded = users.findLockedFreshById(id).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo("b@example.test");
        assertThat(reloaded.getEmailRevision()).isEqualTo(1);
    }
}
