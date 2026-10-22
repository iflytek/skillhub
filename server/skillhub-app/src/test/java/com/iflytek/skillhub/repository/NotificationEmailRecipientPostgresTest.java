package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.iflytek.skillhub.domain.user.*;
import com.iflytek.skillhub.infra.jpa.NotificationEmailContactPersistence;
import com.iflytek.skillhub.notification.email.*;
import com.iflytek.skillhub.notification.email.delivery.NotificationEmailCancellation;
import com.iflytek.skillhub.service.*;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mail.SimpleMailMessage;
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
class NotificationEmailRecipientPostgresTest {
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
    @Autowired UserAccountRepository users;
    @Autowired com.iflytek.skillhub.auth.settings.SystemSettingRepository settingsRepository;
    @Autowired EntityManager em;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    String create() {
        String id = "email-" + UUID.randomUUID();
        new TransactionTemplate(manager).executeWithoutResult(
                s -> users.save(new UserAccount(id, "name", "a@example.test", null)));
        return id;
    }
    @Test
    void lockRefreshSeesCommittedEmailRevisionDespitePreloadedAccount() throws Exception {
        assertThat(em.getEntityManagerFactory().unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class)
                .getJdbcServices().getDialect()).isInstanceOf(org.hibernate.dialect.PostgreSQLDialect.class);
        String id = create();
        var tx = new TransactionTemplate(manager);
        CountDownLatch loaded = new CountDownLatch(1), changed = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var stale = pool.submit(() -> tx.execute(s -> {
                var account = users.findById(id).orElseThrow();
                assertThat(account.getEmailRevision()).isZero();
                loaded.countDown();
                await(changed);
                return users.findLockedFreshById(id).orElseThrow().getEmailRevision();
            }));
            var writer = pool.submit(() -> {
                await(loaded);
                tx.executeWithoutResult(s -> {
                    var account = users.findLockedFreshById(id).orElseThrow();
                    account.setEmail("b@example.test");
                    users.save(account);
                });
                changed.countDown();
            });
            writer.get(15, TimeUnit.SECONDS);
            assertThat(stale.get(15, TimeUnit.SECONDS)).isEqualTo(1);
        }
    }
    @Test
    void concurrentConfirmConsumesCodeExactlyOnceAndAddressRoundTripInvalidatesProof() throws Exception {
        String id = create();
        var sender = mock(JavaMailSender.class);
        AtomicReference<String> code = new AtomicReference<>();
        doAnswer(i -> {
            SimpleMailMessage msg = i.getArgument(0);
            code.set(msg.getText().replaceAll("(?s).*code: ([0-9]{6}).*", "$1"));
            return null;
        })
                .when(sender)
                .send(any(SimpleMailMessage.class));
        var service = service(sender);
        service.requestCode(id, "en");
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> confirm = () -> {
                await(start);
                try {
                    service.verify(id, code.get());
                    return true;
                } catch (com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException invalid) {
                    return false;
                }
            };
            var a = pool.submit(confirm);
            var b = pool.submit(confirm);
            start.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(service.current(id)).isPresent();
        var tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(s -> {
            var account = users.findLockedFreshById(id).orElseThrow();
            account.setEmail("b@example.test");
            users.save(account);
        });
        tx.executeWithoutResult(s -> {
            var account = users.findLockedFreshById(id).orElseThrow();
            account.setEmail("a@example.test");
            users.save(account);
        });
        assertThat(service.current(id)).isEmpty();
    }
    @Test
    void smtpWaitHoldsNoAccountLock() throws Exception {
        String id = create();
        var sender = mock(JavaMailSender.class);
        CountDownLatch sending = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(i -> {
            sending.countDown();
            await(release);
            return null;
        })
                .when(sender)
                .send(any(SimpleMailMessage.class));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var sendingTask = pool.submit(() -> service(sender).requestCode(id, "en"));
            await(sending);
            var writer = pool.submit(() -> new TransactionTemplate(manager).execute(s -> {
                var account = users.findLockedFreshById(id).orElseThrow();
                account.setEmail("b@example.test");
                return users.save(account).getEmailRevision();
            }));
            try {
                assertThat(writer.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            } finally {
                release.countDown();
            }
            sendingTask.get(15, TimeUnit.SECONDS);
        }
    }
    @Test
    void policySeedPersistsJsonAndDoesNotOverwriteDatabaseChoice() {
        var cancellation = mock(NotificationEmailCancellation.class);
        var service = new NotificationEmailSettingsService(settingsRepository, em, false, 15, "https://example.test",
                "smtp.example.test", "noreply@example.test",
                mock(com.iflytek.skillhub.domain.audit.AuditLogService.class), cancellation);
        var tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> service.run(null));
        assertThat(service.current().enabled()).isFalse();
        assertThat(service.current().digestWindowMinutes()).isEqualTo(15);
        var updated = tx.execute(status -> service.update(true, 30, "https://example.test", 0, "admin"));
        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.enabled()).isTrue();
        tx.executeWithoutResult(status -> service.run(null));
        assertThat(service.current().enabled()).isTrue();
        assertThatThrownBy(() -> tx.execute(status -> service.update(false, 15, "https://example.test", 0, "admin")))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainConflictException.class);
    }
    @Test
    void legacyMixedCaseAndWhitespaceEmailSurvivesCanonicalNoOpSync() {
        String id = create();
        // A pre-existing legacy row bypasses the new constructor's normalization on purpose.
        jdbc.update("UPDATE user_account SET email = ? WHERE id = ?", " A@Example.test ", id);
        var sender = mock(JavaMailSender.class);
        AtomicReference<String> code = new AtomicReference<>();
        doAnswer(invocation -> {
            SimpleMailMessage message = invocation.getArgument(0);
            assertThat(message.getTo()).containsExactly("a@example.test");
            code.set(message.getText().replaceAll("(?s).*code: ([0-9]{6}).*", "$1"));
            return null;
        })
                .when(sender)
                .send(any(SimpleMailMessage.class));
        var service = service(sender);
        service.requestCode(id, "en");
        assertThat(service.verify(id, code.get()).verified()).isTrue();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            var account = users.findLockedFreshById(id).orElseThrow();
            account.setEmail("a@example.test");
            assertThat(account.getEmailRevision()).isZero();
            users.save(account);
        });
        assertThat(service.current(id)).isPresent();
        assertThat(service.current(id).orElseThrow().email()).isEqualTo("a@example.test");
    }
    private NotificationEmailContactService service(JavaMailSender sender) {
        var settings = mock(NotificationEmailSettingsService.class);
        when(settings.lockCurrent()).thenReturn(new NotificationEmailPolicy(true, 15, "https://example.test", 0));
        when(settings.current()).thenReturn(new NotificationEmailPolicy(true, 15, "https://example.test", 0));
        when(settings.smtpConfigured()).thenReturn(true);
        return new NotificationEmailContactService(users, new NotificationEmailContactPersistence(em),
                mock(NotificationEmailPreferenceRepository.class), mock(NotificationEmailPreferenceService.class),
                settings, sender, new BCryptPasswordEncoder(4), manager, Clock.systemUTC(),
                mock(NotificationEmailCancellation.class), "noreply@example.test", "SkillHub");
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
