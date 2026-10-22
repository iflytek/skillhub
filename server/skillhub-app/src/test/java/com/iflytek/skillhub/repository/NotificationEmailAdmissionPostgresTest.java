package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.iflytek.skillhub.auth.settings.*;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.user.*;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.infra.jpa.*;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.email.*;
import com.iflytek.skillhub.notification.email.delivery.NotificationEmailCancellation;
import com.iflytek.skillhub.service.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import com.iflytek.skillhub.notification.email.delivery.*;
import com.iflytek.skillhub.service.notificationemail.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
class NotificationEmailAdmissionPostgresTest {
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
    @Autowired SystemSettingRepository settingsRepository;
    @Autowired UserAccountRepository accounts;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    final Instant initial = Instant.parse("2026-10-22T10:00:00Z");
    final MutableClock clock = new MutableClock();
    TransactionTemplate tx;
    NotificationEmailSettingsService settings;
    NotificationEmailContactPersistence contacts;
    NotificationEmailPreferencePersistence preferences;
    NotificationEmailContactService recipients;
    NotificationEmailAdmissionService admission;
    NotificationEmailPreferenceService modes;
    String user;

    @BeforeEach
    void initialize() {
        clock.now = initial;
        tx = new TransactionTemplate(manager);
        jdbc.update("DELETE FROM system_setting WHERE setting_key = ?", NotificationEmailSettingsService.SETTING_KEY);
        var cancellation = mock(NotificationEmailCancellation.class);
        settings = new NotificationEmailSettingsService(settingsRepository, em, true, 60, "https://example.test",
                "smtp.example.test", "noreply@example.test", mock(AuditLogService.class), cancellation, clock);
        contacts = new NotificationEmailContactPersistence(em);
        preferences = new NotificationEmailPreferencePersistence(em);
        modes = new NotificationEmailPreferenceService(preferences);
        recipients = new NotificationEmailContactService(accounts, contacts, preferences, modes, settings,
                mock(JavaMailSender.class), new BCryptPasswordEncoder(4), manager, clock, cancellation,
                "noreply@example.test", "SkillHub");
        admission = new NotificationEmailAdmissionService(settings, recipients, preferences);
        user = "admission-" + UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            settings.run(null);
            accounts.save(new UserAccount(user, "Name", "a@example.test", null));
        });
    }

    @Test
    void platformOffOnDoesNotAdmitDelayedEventsFromDisabledInterval() {
        verifyAndEnable();
        assertThat(watermark()).contains(initial);
        clock.now = initial.plusSeconds(3600);
        updatePolicy(false, 60);
        assertThat(watermark()).isEmpty();
        clock.now = initial.plusSeconds(7200);
        updatePolicy(true, 60);
        assertThat(watermark()).contains(clock.now);
        assertThat(initial.plusSeconds(5400).isBefore(watermark().orElseThrow())).isTrue();
        // The boundary itself remains admissible; the delivery inbox enforces this comparison.
        assertThat(clock.now.isBefore(watermark().orElseThrow())).isFalse();
        clock.now = initial.plusSeconds(10800);
        updatePolicy(true, 120);
        tx.executeWithoutResult(status -> settings.run(null));
        assertThat(watermark()).contains(initial.plusSeconds(7200));
    }

    @Test
    void categoryOffOnAndAccountReactivationAdvanceIndependentBaselines() {
        verifyAndEnable();
        clock.now = initial.plusSeconds(60);
        setMode(NotificationEmailMode.IMMEDIATE);
        assertThat(watermark()).contains(initial);
        clock.now = initial.plusSeconds(120);
        setMode(NotificationEmailMode.OFF);
        assertThat(watermark()).isEmpty();
        clock.now = initial.plusSeconds(180);
        setMode(NotificationEmailMode.DIGEST);
        assertThat(watermark()).contains(clock.now);
        // A name change does not change admission eligibility.
        tx.executeWithoutResult(status -> {
            var account = accounts.findLockedFreshById(user).orElseThrow();
            account.setDisplayName("Renamed");
            accounts.save(account);
        });
        assertThat(watermark()).contains(initial.plusSeconds(180));
        clock.now = initial.plusSeconds(240);
        tx.executeWithoutResult(status -> {
            settings.lockCurrent();
            accounts.findLockedFreshById(user).orElseThrow();
            recipients.markActive(user);
        });
        assertThat(watermark()).contains(initial.plusSeconds(240));
        assertThat(recipients.current(user).orElseThrow().eligibleSince()).isEqualTo(initial.plusSeconds(240));
    }

    @Test
    void verificationIsRequiredAndLegacyPolicyBaselineIsBackfilledExactlyOnce() {
        assertThat(watermark()).isEmpty();
        jdbc.update("UPDATE system_setting SET value_json = ?::jsonb WHERE setting_key = ?",
                "{\"enabled\":true,\"digestWindowMinutes\":60,\"publicBaseUrl\":\"https://example.test\"}",
                NotificationEmailSettingsService.SETTING_KEY);
        clock.now = initial.plusSeconds(60);
        tx.executeWithoutResult(status -> settings.run(null));
        assertThat(settings.current().enabledSince()).isEqualTo(clock.now);
        clock.now = initial.plusSeconds(120);
        tx.executeWithoutResult(status -> settings.run(null));
        assertThat(settings.current().enabledSince()).isEqualTo(initial.plusSeconds(60));
        verifyAndEnable();
        assertThat(watermark()).contains(initial.plusSeconds(120));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"GLOBAL", "CATEGORY", "ACCOUNT"})
    void inboxGuardsCoverAllocatedIdUntilCommitBeforeCancellationCutoff(String scope) throws Exception {
        verifyAndEnable();
        var cancellation = new JdbcNotificationEmailCancellation(jdbc);
        var settingsWithBarrier = new NotificationEmailSettingsService(settingsRepository, em, true, 60,
                "https://example.test", "smtp.example.test", "noreply@example.test",
                mock(AuditLogService.class), cancellation, clock);
        var recipientWithBarrier = new NotificationEmailContactService(accounts, contacts, preferences, modes,
                settingsWithBarrier, mock(JavaMailSender.class), new BCryptPasswordEncoder(4), manager, clock,
                cancellation, "noreply@example.test", "SkillHub");
        String barrierKey = "GLOBAL".equals(scope) ? "GLOBAL" : "USER:" + user + ":PUBLISH";
        var repository = spy(new JdbcNotificationEmailDeliveryRepository(jdbc));
        var eligibility = mock(NotificationEmailEligibility.class);
        when(eligibility.eligible(any())).thenReturn(true);
        var inbox = new NotificationEmailInboxService(settingsWithBarrier, recipients, modes, repository,
                eligibility, new com.fasterxml.jackson.databind.ObjectMapper(), clock, admission);
        var proxy = new org.springframework.aop.framework.ProxyFactory(inbox);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var transactionalInbox = (NotificationEmailEnqueuer) proxy.getProxy();
        CountDownLatch inserted = new CountDownLatch(1), releaseCommit = new CountDownLatch(1),
                       writerStarting = new CountDownLatch(1);
        AtomicLong itemId = new AtomicLong();
        AtomicInteger writerPid = new AtomicInteger(), inboxPid = new AtomicInteger();
        doAnswer(call -> {
            var result = call.callRealMethod();
            inboxPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
            itemId.set(jdbc.queryForObject("SELECT id FROM notification_email_item WHERE recipient_id=? AND event_key=?",
                    Long.class, user, "before-off"));
            inserted.countDown();
            await(releaseCommit);
            return result;
        }).when(repository).addItem(anyLong(), any(), any(), any());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var source = pool.submit(() -> transactionalInbox.enqueue(new NotificationEmailCommand(user,
                    NotificationCategory.PUBLISH, "REVIEW_APPROVED", "before-off", initial, "{}", "SKILL", 1L)));
            try {
                await(inserted);
                // This insertion is not yet committed, but its sequence number has already been allocated.
                assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_email_item WHERE id=?",
                        Long.class, itemId.get())).isZero();
                var off = pool.submit(() -> tx.execute(status -> {
                    writerPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    writerStarting.countDown();
                    switch (scope) {
                        case "GLOBAL" -> settingsWithBarrier.update(false, 60, "https://example.test", 0, "admin");
                        case "CATEGORY" -> recipientWithBarrier.updatePreferences(user, new NotificationEmailPreferenceUpdateRequest(
                                List.of(new NotificationEmailPreferenceResponse("PUBLISH", "OFF"))));
                        case "ACCOUNT" -> {
                            // Exact AdminUser stop lock order; role/audit collaborators are irrelevant to this race.
                            settingsWithBarrier.lockCurrent();
                            var account = accounts.findLockedFreshById(user).orElseThrow();
                            account.setStatus(UserStatus.DISABLED);
                            accounts.save(account);
                            cancellation.cancelCategories(user, Set.of(NotificationCategory.values()));
                        }
                        default -> throw new AssertionError(scope);
                    }
                    return true;
                }));
                await(writerStarting);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean blocked = false;
                while (System.nanoTime() < deadline) {
                    if (jdbc.queryForList("SELECT unnest(pg_blocking_pids(?))", Integer.class, writerPid.get())
                            .contains(inboxPid.get())) {
                        blocked = true;
                        break;
                    }
                    Thread.sleep(20);
                }
                assertThat(blocked).as("%s cancellation waits for actual inbox insertion transaction", scope).isTrue();
                releaseCommit.countDown();
                source.get(10, TimeUnit.SECONDS);
                assertThat(off.get(10, TimeUnit.SECONDS)).isTrue();
                Long cutoff = jdbc.queryForObject("SELECT through_item_id FROM notification_email_cancel_barrier "
                        + "WHERE scope_key=?", Long.class, barrierKey);
                assertThat(cutoff).isGreaterThanOrEqualTo(itemId.get());
                clock.now = initial.plusSeconds(60);
                tx.executeWithoutResult(status -> {
                    switch (scope) {
                        case "GLOBAL" -> settingsWithBarrier.update(true, 60, "https://example.test", 1, "admin");
                        case "CATEGORY" -> recipientWithBarrier.updatePreferences(user, new NotificationEmailPreferenceUpdateRequest(
                                List.of(new NotificationEmailPreferenceResponse("PUBLISH", "DIGEST"))));
                        case "ACCOUNT" -> {
                            settingsWithBarrier.lockCurrent();
                            var account = accounts.findLockedFreshById(user).orElseThrow();
                            account.setStatus(UserStatus.ACTIVE);
                            accounts.save(account);
                            recipientWithBarrier.markActive(user);
                        }
                        default -> throw new AssertionError(scope);
                    }
                });
                assertThat(jdbc.queryForObject("SELECT through_item_id FROM notification_email_cancel_barrier "
                        + "WHERE scope_key=?", Long.class, barrierKey)).isEqualTo(cutoff);
                reset(repository);
                transactionalInbox.enqueue(new NotificationEmailCommand(user, NotificationCategory.PUBLISH,
                        "REVIEW_APPROVED", "after-on", clock.now, "{}", "SKILL", 1L));
                assertThat(jdbc.queryForObject("SELECT id FROM notification_email_item WHERE recipient_id=? "
                        + "AND event_key='after-on'", Long.class, user)).isGreaterThan(cutoff);
            } finally {
                releaseCommit.countDown();
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS))
                throw new AssertionError("Barrier timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private void verifyAndEnable() {
        tx.executeWithoutResult(status -> {
            settings.lockCurrent();
            accounts.findLockedFreshById(user).orElseThrow();
            var contact = contacts.lockOrCreate(user);
            contact.challenge("fixture", "hash", "a@example.test", 0, "en", clock.now);
            contact.consume(clock.now);
            contacts.save(contact);
        });
        setMode(NotificationEmailMode.DIGEST);
    }
    private void setMode(NotificationEmailMode mode) {
        tx.executeWithoutResult(status -> recipients.updatePreferences(user,
                new NotificationEmailPreferenceUpdateRequest(List.of(
                        new NotificationEmailPreferenceResponse(NotificationCategory.PUBLISH.name(), mode.name())))));
    }
    private void updatePolicy(boolean enabled, int minutes) {
        tx.executeWithoutResult(status -> settings.update(enabled, minutes, "https://example.test",
                settings.current().version(), "admin"));
    }
    private Optional<Instant> watermark() {
        return tx.execute(status -> {
            settings.lockCurrent();
            recipients.lockCurrent(user);
            modes.lockCurrent(user);
            return admission.currentUnderLocks(user, NotificationCategory.PUBLISH);
        });
    }
    private class MutableClock extends Clock {
        Instant now;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
