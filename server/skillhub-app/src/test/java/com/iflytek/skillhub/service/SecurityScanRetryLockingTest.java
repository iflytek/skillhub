package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.infra.jpa.SkillVersionJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class SecurityScanRetryLockingTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private SkillVersionJpaRepository skillVersionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lockReadSeesStateCommittedWhileWaitingInsteadOfCachedPreflightEntity() throws Exception {
        Fixture fixture = persistFailedVersion();
        Long versionId = fixture.versionId();
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondAboutToLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> transactions.executeWithoutResult(status -> {
                SkillVersion version = skillVersionRepository.findByIdForUpdate(versionId).orElseThrow();
                version.setStatus(SkillVersionStatus.SCANNING);
                firstLocked.countDown();
                await(releaseFirst);
            }));

            assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> transactions.execute(status -> {
                assertThat(skillVersionRepository.findStatusByIdAndSkillId(versionId, fixture.skillId()))
                        .contains(SkillVersionStatus.SCAN_FAILED);
                secondAboutToLock.countDown();
                return skillVersionRepository.findByIdForUpdate(versionId).orElseThrow().getStatus();
            }));

            assertThat(secondAboutToLock.await(10, TimeUnit.SECONDS)).isTrue();
            releaseFirst.countDown();
            first.get();
            assertThat(second.get()).isEqualTo(SkillVersionStatus.SCANNING);
        }
    }

    private Fixture persistFailedVersion() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            UserAccount user = new UserAccount("retry-lock-user", "Retry Lock User", null, null);
            entityManager.persist(user);
            Namespace namespace = new Namespace("retry-lock", "Retry Lock", user.getId());
            entityManager.persist(namespace);
            entityManager.flush();
            Skill skill = new Skill(namespace.getId(), "retry-lock", user.getId(), SkillVisibility.PRIVATE);
            entityManager.persist(skill);
            entityManager.flush();
            SkillVersion version = new SkillVersion(skill.getId(), "1.0.0", user.getId());
            version.setStatus(SkillVersionStatus.SCAN_FAILED);
            entityManager.persist(version);
            entityManager.flush();
            return new Fixture(version.getId(), skill.getId());
        });
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent retry test");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent retry test interrupted", e);
        }
    }

    private record Fixture(Long versionId, Long skillId) {
    }
}
