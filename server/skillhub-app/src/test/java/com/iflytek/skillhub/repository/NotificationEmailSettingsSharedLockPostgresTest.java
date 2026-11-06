package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.auth.settings.SystemSetting;
import com.iflytek.skillhub.auth.settings.SystemSettingRepository;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.notification.email.NotificationEmailPolicy;
import com.iflytek.skillhub.notification.email.delivery.NotificationEmailCancellation;
import com.iflytek.skillhub.service.NotificationEmailSettingsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationEmailSettingsSharedLockPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
    @Autowired SystemSettingRepository settings;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    NotificationEmailSettingsService service;
    TransactionTemplate tx;

    @BeforeEach
    void seed() {
        tx = new TransactionTemplate(manager);
        jdbc.update("DELETE FROM system_setting WHERE setting_key = ?", NotificationEmailSettingsService.SETTING_KEY);
        tx.executeWithoutResult(status
                -> settings.saveAndFlush(new SystemSetting(NotificationEmailSettingsService.SETTING_KEY,
                        Map.of("enabled", true, "digestWindowMinutes", 60, "publicBaseUrl", "https://example.test"))));
        service = new NotificationEmailSettingsService(settings, em, false, 60, "https://example.test",
                "smtp.example.test", "noreply@example.test", mock(AuditLogService.class),
                mock(NotificationEmailCancellation.class), java.time.Clock.systemUTC());
    }

    @Test
    void twoReadGuardsRunTogetherAndOffWriterWaitsForBoth() throws Exception {
        CountDownLatch readersInside = new CountDownLatch(2), releaseFirst = new CountDownLatch(1),
                       releaseSecond = new CountDownLatch(1), writerStarting = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger(), secondPid = new AtomicInteger(), writerPid = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(3)) {
            var first = pool.submit(() -> guardedReader(firstPid, readersInside, releaseFirst));
            var second = pool.submit(() -> guardedReader(secondPid, readersInside, releaseSecond));
            try {
                assertThat(readersInside.await(10, TimeUnit.SECONDS)).isTrue();
                var writer = pool.submit(() -> tx.execute(status -> {
                    writerPid.set(pid());
                    writerStarting.countDown();
                    return service.update(false, 60, "https://example.test", 0, "admin");
                }));
                await(writerStarting);
                int blocker = awaitAnyBlocker(writerPid.get(), Set.of(firstPid.get(), secondPid.get()));
                // PostgreSQL may expose one MultiXact member at a time. Release that member,
                // then prove the writer is still blocked by the other, rather than assuming both are listed.
                if (blocker == firstPid.get()) {
                    releaseFirst.countDown();
                    assertThat(first.get(10, TimeUnit.SECONDS).enabled()).isTrue();
                    awaitBlockedBy(writerPid.get(), Set.of(secondPid.get()));
                    releaseSecond.countDown();
                    assertThat(second.get(10, TimeUnit.SECONDS).enabled()).isTrue();
                } else {
                    releaseSecond.countDown();
                    assertThat(second.get(10, TimeUnit.SECONDS).enabled()).isTrue();
                    awaitBlockedBy(writerPid.get(), Set.of(firstPid.get()));
                    releaseFirst.countDown();
                    assertThat(first.get(10, TimeUnit.SECONDS).enabled()).isTrue();
                }
                assertThat(writer.get(10, TimeUnit.SECONDS).enabled()).isFalse();
                NotificationEmailPolicy after = tx.execute(status -> service.lockCurrent());
                assertThat(after.enabled()).isFalse();
                assertThat(after.version()).isEqualTo(1);
            } finally {
                releaseFirst.countDown();
                releaseSecond.countDown();
            }
        }
    }

    private NotificationEmailPolicy guardedReader(
            AtomicInteger backend, CountDownLatch inside, CountDownLatch release) {
        return tx.execute(status -> {
            backend.set(pid());
            var policy = service.lockCurrent();
            assertThat(em.getLockMode(
                               settings.findBySettingKey(NotificationEmailSettingsService.SETTING_KEY).orElseThrow()))
                    .isEqualTo(LockModeType.PESSIMISTIC_READ);
            inside.countDown();
            await(release);
            return policy;
        });
    }

    @Test
    void guardWaitsForOffWriterThenRefreshesPreviouslyCachedSetting() throws Exception {
        CountDownLatch cached = new CountDownLatch(1), writerHolding = new CountDownLatch(1),
                       readerStarting = new CountDownLatch(1), releaseWriter = new CountDownLatch(1);
        AtomicInteger readerPid = new AtomicInteger(), writerPid = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var reader = pool.submit(() -> tx.execute(status -> {
                readerPid.set(pid());
                assertThat(service.current().enabled()).isTrue();
                cached.countDown();
                await(writerHolding);
                readerStarting.countDown();
                return service.lockCurrent();
            }));
            var writer = pool.submit(() -> {
                await(cached);
                return tx.execute(status -> {
                    writerPid.set(pid());
                    var off = service.update(false, 60, "https://example.test", 0, "admin");
                    writerHolding.countDown();
                    await(releaseWriter);
                    return off;
                });
            });
            try {
                await(readerStarting);
                awaitBlockedBy(readerPid.get(), Set.of(writerPid.get()));
                releaseWriter.countDown();
                assertThat(writer.get(10, TimeUnit.SECONDS).enabled()).isFalse();
                var refreshed = reader.get(10, TimeUnit.SECONDS);
                assertThat(refreshed.enabled()).isFalse();
                assertThat(refreshed.version()).isEqualTo(1);
            } finally {
                releaseWriter.countDown();
            }
        }
    }

    @Test
    void guardWaitingForReenableSeesCommittedAdmissionWatermark() throws Exception {
        var enabledAt = java.time.Instant.parse("2026-10-22T12:00:00Z");
        service = new NotificationEmailSettingsService(settings, em, false, 60, "https://example.test",
                "smtp.example.test", "noreply@example.test", mock(AuditLogService.class),
                mock(NotificationEmailCancellation.class), java.time.Clock.fixed(enabledAt, java.time.ZoneOffset.UTC));
        CountDownLatch cached = new CountDownLatch(1), writerHolding = new CountDownLatch(1),
                       readerStarting = new CountDownLatch(1), releaseWriter = new CountDownLatch(1);
        AtomicInteger readerPid = new AtomicInteger(), writerPid = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var reader = pool.submit(() -> tx.execute(status -> {
                readerPid.set(pid());
                assertThat(service.current().enabledSince()).isNull();
                cached.countDown();
                await(writerHolding);
                readerStarting.countDown();
                return service.lockCurrent();
            }));
            var writer = pool.submit(() -> {
                await(cached);
                return tx.execute(status -> {
                    writerPid.set(pid());
                    var off = service.update(false, 60, "https://example.test", 0, "admin");
                    var on = service.update(true, 60, "https://example.test", off.version(), "admin");
                    writerHolding.countDown();
                    await(releaseWriter);
                    return on;
                });
            });
            try {
                await(readerStarting);
                awaitBlockedBy(readerPid.get(), Set.of(writerPid.get()));
                releaseWriter.countDown();
                assertThat(writer.get(10, TimeUnit.SECONDS).enabledSince()).isEqualTo(enabledAt);
                var refreshed = reader.get(10, TimeUnit.SECONDS);
                assertThat(refreshed.enabled()).isTrue();
                assertThat(refreshed.version()).isEqualTo(2);
                assertThat(refreshed.enabledSince()).isEqualTo(enabledAt);
            } finally {
                releaseWriter.countDown();
            }
        }
    }

    private int pid() {
        return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }
    private int awaitAnyBlocker(int backend, Set<Integer> candidates) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            var actual = jdbc.queryForList("SELECT unnest(pg_blocking_pids(?))", Integer.class, backend);
            for (int blocker : actual)
                if (candidates.contains(blocker))
                    return blocker;
            Thread.sleep(20);
        }
        throw new AssertionError("Writer was not blocked by either shared policy reader");
    }
    private void awaitBlockedBy(int pid, Set<Integer> blockers) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        List<Integer> actual = List.of();
        while (System.nanoTime() < deadline) {
            actual = jdbc.queryForList("SELECT unnest(pg_blocking_pids(?))", Integer.class, pid);
            if (actual.containsAll(blockers))
                return;
            Thread.sleep(20);
        }
        assertThat(actual).as("Actual PostgreSQL lock blockers for backend %s", pid).containsAll(blockers);
    }
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS))
                throw new AssertionError("Barrier timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
