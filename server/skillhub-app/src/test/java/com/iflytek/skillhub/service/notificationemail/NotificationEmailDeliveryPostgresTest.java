package com.iflytek.skillhub.service.notificationemail;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.infra.jpa.*;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.email.*;
import com.iflytek.skillhub.notification.email.delivery.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class NotificationEmailDeliveryPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager manager;
    static TransactionTemplate tx;
    JdbcNotificationEmailDeliveryRepository repository;
    NotificationEmailPolicyProvider policy;
    NotificationEmailRecipientProvider recipients;
    NotificationEmailPreferenceService preferences;
    NotificationEmailEligibility eligibility;
    Clock clock = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC);
    @BeforeAll
    static void schema() {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        manager = new DataSourceTransactionManager(ds);
        tx = new TransactionTemplate(manager);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V121__notification_email_delivery.sql"))
                .execute(ds);
    }
    @BeforeEach
    void setup() {
        jdbc.execute("TRUNCATE notification_email_item,notification_email_delivery RESTART IDENTITY CASCADE");
        repository = new JdbcNotificationEmailDeliveryRepository(jdbc);
        policy = mock(NotificationEmailPolicyProvider.class);
        recipients = mock(NotificationEmailRecipientProvider.class);
        preferences = mock(NotificationEmailPreferenceService.class);
        eligibility = mock(NotificationEmailEligibility.class);
        when(policy.lockCurrent()).thenReturn(new NotificationEmailPolicy(true, 15, "https://example.test", 0));
        when(recipients.lockCurrent(anyString()))
                .thenAnswer(inv
                        -> Optional.of(
                                new NotificationEmailRecipient(inv.getArgument(0), "same@example.test", 0, "en")));
        when(preferences.lockCurrent(anyString()))
                .thenReturn(Map.of(NotificationCategory.PUBLISH, NotificationEmailMode.DIGEST,
                        NotificationCategory.REVIEW, NotificationEmailMode.DIGEST));
        when(eligibility.eligible(any())).thenReturn(true);
    }
    NotificationEmailCommand command(String user, String key, NotificationCategory category) {
        return new NotificationEmailCommand(user, category, "REVIEW_APPROVED", key, clock.instant(),
                "{\"skillId\":1,\"reason\":\"PRIVATE\"}", "SKILL", 1L);
    }
    NotificationEmailInboxService inbox() {
        return new NotificationEmailInboxService(
                policy, recipients, preferences, repository, eligibility, new ObjectMapper(), clock);
    }
    void enqueue(NotificationEmailCommand command) {
        tx.executeWithoutResult(s -> inbox().enqueue(command));
    }
    NotificationEmailDelivery batch() {
        return repository.list(null, 0, 100).getFirst();
    }
    NotificationEmailDelivery claim(String token) {
        return tx.execute(s
                -> repository.claim(clock.instant().plusSeconds(901), clock.instant().plusSeconds(1000), token)
                        .orElseThrow());
    }
    NotificationEmailDispatcher dispatcher(NotificationEmailSender sender) {
        return new NotificationEmailDispatcher(repository, policy, recipients, preferences, eligibility, sender,
                new NotificationEmailDeliveryProperties(),
                Clock.fixed(clock.instant().plusSeconds(901), ZoneOffset.UTC), manager);
    }

    @Test
    void fifteenEventsAcrossCategoriesAreOneBatchAndDuplicateInstancesDoNotAddItems() {
        for (int i = 0; i < 12; i++) enqueue(command("user", "p" + i, NotificationCategory.PUBLISH));
        for (int i = 0; i < 3; i++) enqueue(command("user", "r" + i, NotificationCategory.REVIEW));
        enqueue(command("user", "r0", NotificationCategory.REVIEW));
        assertThat(repository.count(null)).isEqualTo(1);
        assertThat(repository.items(batch().id())).hasSize(15);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE "
                                   + "table_name='notification_email_item' AND column_name LIKE '%body%'",
                           Long.class))
                .isZero();
    }

    @Test
    void sameEmailDifferentAccountsNeverShareBatch() {
        enqueue(command("one", "x", NotificationCategory.PUBLISH));
        enqueue(command("two", "x", NotificationCategory.PUBLISH));
        assertThat(repository.count(null)).isEqualTo(2);
    }

    @Test
    void replayWindowRejectsOldAndFutureEventsButIncludesExactSeventhDay() {
        var cmd = command("u", "valid", NotificationCategory.REVIEW);
        enqueue(new NotificationEmailCommand("u", cmd.category(), cmd.eventType(), "boundary",
                clock.instant().minus(Duration.ofDays(7)), null, "SKILL", 1L));
        enqueue(new NotificationEmailCommand("u", cmd.category(), cmd.eventType(), "old",
                clock.instant().minus(Duration.ofDays(7)).minusMillis(1), null, "SKILL", 1L));
        enqueue(new NotificationEmailCommand(
                "u", cmd.category(), cmd.eventType(), "future", clock.instant().plusSeconds(61), null, "SKILL", 1L));
        assertThat(repository.items(batch().id())).hasSize(1);
    }

    @Test
    void lateArrivalDoesNotReopenClaimedBatch() {
        enqueue(command("u", "one", NotificationCategory.PUBLISH));
        var closed = claim("a");
        enqueue(command("u", "two", NotificationCategory.REVIEW));
        assertThat(repository.count(null)).isEqualTo(2);
        assertThat(repository.items(closed.id())).hasSize(1);
    }

    @Test
    void twoWorkersClaimExactlyOnceAndExpiredClaimCanBeReclaimed() throws Exception {
        enqueue(command("u", "one", NotificationCategory.PUBLISH));
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> call = () -> {
                start.await();
                return tx.execute(s
                        -> repository.claim(clock.instant().plusSeconds(901), clock.instant().plusSeconds(902),
                                             UUID.randomUUID().toString())
                                        .isPresent()
                                ? 1
                                : 0);
            };
            var a = pool.submit(call);
            var b = pool.submit(call);
            start.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS) + b.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        }
        tx.executeWithoutResult(s -> repository.recoverExpired(clock.instant().plusSeconds(903)));
        assertThat(repository.find(batch().id()).orElseThrow().status()).isEqualTo("RETRY");
    }

    @Test
    void expiredSendingBecomesUnknownAndOldTokenCannotConfirm() {
        enqueue(command("u", "one", NotificationCategory.PUBLISH));
        var claim = claim("old");
        tx.executeWithoutResult(s
                -> repository.authorize(
                        claim.id(), "old", clock.instant().plusSeconds(901), clock.instant().plusSeconds(902)));
        tx.executeWithoutResult(s -> repository.recoverExpired(clock.instant().plusSeconds(903)));
        assertThat(repository.find(claim.id()).orElseThrow().status()).isEqualTo("UNKNOWN");
        Boolean finished = tx.execute(
                s -> repository.finish(claim.id(), "old", "SENT", "ACCEPTED", clock.instant(), clock.instant()));
        assertThat(finished).isFalse();
        var reclaimed = tx.execute(
                s -> repository.claim(clock.instant().plusSeconds(1000), clock.instant().plusSeconds(1100), "new"));
        assertThat(reclaimed).isEmpty();
    }

    @Test
    void smtpRunsOutsideTransactionAndSummaryHasOnlySafeMetadata() {
        for (int i = 0; i < 55; i++) {
            enqueue(command("u", "item" + i, i % 2 == 0 ? NotificationCategory.REVIEW : NotificationCategory.PUBLISH));
        }
        var claim = claim("send");
        NotificationEmailSender sender = (recipient, locale, id, subject, text) -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(text)
                    .contains("PUBLISH: 27", "REVIEW: 28", "/dashboard/notifications/email/" + id)
                    .doesNotContain("PRIVATE", "reason", "skillId", "same@example.test");
            assertThat(text.lines().filter(line -> line.contains(" · ")).count()).isEqualTo(50);
            // A separate transaction obtains the row lock while SMTP is still executing.
            tx.executeWithoutResult(s -> {
                jdbc.execute("SET LOCAL lock_timeout='500ms'");
                assertThat(repository.lock(id).status()).isEqualTo("SENDING");
            });
            return NotificationEmailSender.Result.ACCEPTED;
        };
        dispatcher(sender).process(claim);
        assertThat(repository.find(claim.id()).orElseThrow().status()).isEqualTo("SENT");
    }

    @Test
    void currentRevisionOrPermissionChangesSuppressBeforeSmtp() {
        enqueue(command("u", "one", NotificationCategory.PUBLISH));
        var claim = claim("x");
        when(recipients.lockCurrent("u"))
                .thenReturn(Optional.of(new NotificationEmailRecipient("u", "new@example.test", 2, "en")));
        var sender = mock(NotificationEmailSender.class);
        dispatcher(sender).process(claim);
        verifyNoInteractions(sender);
        assertThat(batch().status()).isEqualTo("SUPPRESSED");
    }

    @Test
    void temporaryFailureRetriesAndEveryAttemptRechecksEligibility() {
        enqueue(command("u", "one", NotificationCategory.PUBLISH));
        var claim = claim("x");
        var sender = mock(NotificationEmailSender.class);
        when(sender.send(any(), any(), anyLong(), any(), any()))
                .thenReturn(NotificationEmailSender.Result.TEMPORARY_FAILURE);
        dispatcher(sender).process(claim);
        assertThat(batch().status()).isEqualTo("RETRY");
        when(eligibility.eligible(any())).thenReturn(false);
        var next = tx.execute(s
                -> repository.claim(clock.instant().plusSeconds(1100), clock.instant().plusSeconds(1300), "y")
                        .orElseThrow());
        dispatcher(sender).process(next);
        assertThat(batch().status()).isEqualTo("SUPPRESSED");
        verify(sender, times(1)).send(any(), any(), anyLong(), any(), any());
    }

    @Test
    void permanentFailureAndUnknownDoNotAutomaticallyRetry() {
        when(preferences.lockCurrent("u"))
                .thenReturn(Map.of(NotificationCategory.PUBLISH, NotificationEmailMode.IMMEDIATE));
        for (var outcome :
                List.of(NotificationEmailSender.Result.PERMANENT_FAILURE, NotificationEmailSender.Result.UNKNOWN)) {
            enqueue(command("u", outcome.name(), NotificationCategory.PUBLISH));
            var claim = claim(outcome.name());
            dispatcher((a, b, c, d, e) -> outcome).process(claim);
        }
        assertThat(repository.list(null, 0, 100))
                .extracting(NotificationEmailDelivery::status)
                .containsExactlyInAnyOrder("FAILED", "UNKNOWN");
    }

    @Test
    void cancellingOneCategoryPersistsAcrossReenableAndPreservesOtherCategory() {
        enqueue(command("u", "one", NotificationCategory.PUBLISH));
        enqueue(command("u", "two", NotificationCategory.REVIEW));
        tx.executeWithoutResult(s
                -> new JdbcNotificationEmailCancellation(jdbc).cancelCategories(
                        "u", Set.of(NotificationCategory.PUBLISH)));
        var sent = new ArrayList<String>();
        dispatcher((a, b, c, d, e) -> {
            sent.add(e);
            return NotificationEmailSender.Result.ACCEPTED;
        }).process(claim("x"));
        assertThat(sent).singleElement().asString().contains("REVIEW: 1").doesNotContain("PUBLISH:");
    }

    @Test
    void cleanupNeverDeletesUnfinishedQueue() {
        enqueue(command("u", "one", NotificationCategory.PUBLISH));
        tx.executeWithoutResult(
                s -> repository.cleanup(clock.instant().plusSeconds(999999), clock.instant().plusSeconds(999999)));
        assertThat(repository.count(null)).isEqualTo(1);
    }

    @Test
    void immediateEventsHaveIndependentBatchesAndFrequencyChangesDoNotMoveOldItems() {
        enqueue(command("u", "digest", NotificationCategory.PUBLISH));
        when(preferences.lockCurrent("u"))
                .thenReturn(Map.of(NotificationCategory.PUBLISH, NotificationEmailMode.IMMEDIATE));
        for (int i = 0; i < 3; i++) enqueue(command("u", "instant" + i, NotificationCategory.PUBLISH));
        assertThat(repository.list(null, 0, 100))
                .extracting(NotificationEmailDelivery::kind)
                .containsExactlyInAnyOrder("DIGEST", "IMMEDIATE", "IMMEDIATE", "IMMEDIATE");
    }

    @Test
    void retryExhaustionStopsAtConfiguredLimit() {
        enqueue(command("u", "one", NotificationCategory.PUBLISH));
        var claimed = claim("last");
        jdbc.update("UPDATE notification_email_delivery SET attempts=4 WHERE id=?", claimed.id());
        dispatcher((a, b, c, d, e) -> NotificationEmailSender.Result.TEMPORARY_FAILURE).process(claimed);
        assertThat(batch().status()).isEqualTo("FAILED");
        assertThat(batch().attempts()).isEqualTo(5);
        assertThat(batch().resultCode()).isEqualTo("RETRY_EXHAUSTED");
    }

    @Test
    void concurrentSealingAndInboxAttachmentCannotAppendToSealedMembers() throws Exception {
        enqueue(command("u", "first", NotificationCategory.PUBLISH));
        long id = batch().id();
        CountDownLatch locked = new CountDownLatch(1), attempting = new CountDownLatch(1),
                       release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var sealing = pool.submit(() -> tx.execute(status -> {
                repository.lock(id);
                locked.countDown();
                try {
                    assertThat(attempting.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                var claimed =
                        repository.claim(clock.instant().plusSeconds(901), clock.instant().plusSeconds(1000), "seal");
                release.countDown();
                return claimed;
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var adding = pool.submit(() -> {
                attempting.countDown();
                enqueue(command("u", "late", NotificationCategory.REVIEW));
            });
            assertThat(sealing.get(10, TimeUnit.SECONDS)).isPresent();
            adding.get(10, TimeUnit.SECONDS);
        }
        assertThat(repository.items(id)).hasSize(1);
        assertThat(repository.count(null)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_email_item", Long.class)).isEqualTo(2);
    }

    private NotificationEmailPolicyProvider lockingPolicy() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS test_email_policy (id INTEGER PRIMARY KEY,enabled BOOLEAN NOT NULL)");
        jdbc.update(
                "INSERT INTO test_email_policy(id,enabled) VALUES(1,true) ON CONFLICT(id) DO UPDATE SET enabled=true");
        return new NotificationEmailPolicyProvider() {
            public NotificationEmailPolicy current() {
                return read(false);
            }
            public NotificationEmailPolicy lockCurrent() {
                return read(true);
            }
            private NotificationEmailPolicy read(boolean lock) {
                Boolean enabled = jdbc.queryForObject(
                        "SELECT enabled FROM test_email_policy WHERE id=1" + (lock ? " FOR UPDATE" : ""),
                        Boolean.class);
                return new NotificationEmailPolicy(Boolean.TRUE.equals(enabled), 15, "https://example.test", 0);
            }
        };
    }

    @Test
    void disableTransactionThatWinsPolicyLockPreventsAuthorization() throws Exception {
        enqueue(command("u", "one", NotificationCategory.PUBLISH));
        var claimed = claim("send");
        policy = lockingPolicy();
        var sender = mock(NotificationEmailSender.class);
        CountDownLatch locked = new CountDownLatch(1), attempting = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var disable = pool.submit(() -> tx.execute(status -> {
                policy.lockCurrent();
                locked.countDown();
                try {
                    assertThat(attempting.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                jdbc.update("UPDATE test_email_policy SET enabled=false WHERE id=1");
                new JdbcNotificationEmailCancellation(jdbc).cancelAll();
                return null;
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var sending = pool.submit(() -> {
                attempting.countDown();
                dispatcher(sender).process(claimed);
            });
            disable.get(10, TimeUnit.SECONDS);
            sending.get(10, TimeUnit.SECONDS);
        }
        verifyNoInteractions(sender);
        assertThat(batch().status()).isEqualTo("SUPPRESSED");
    }

    @Test
    void authorizationThatCommitsFirstCannotBeRevokedDuringSmtp() throws Exception {
        enqueue(command("u", "one", NotificationCategory.PUBLISH));
        var claimed = claim("send");
        policy = lockingPolicy();
        CountDownLatch sending = new CountDownLatch(1), release = new CountDownLatch(1);
        NotificationEmailSender sender = (a, b, c, d, e) -> {
            sending.countDown();
            try {
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
            return NotificationEmailSender.Result.ACCEPTED;
        };
        try (var pool = Executors.newSingleThreadExecutor()) {
            var worker = pool.submit(() -> dispatcher(sender).process(claimed));
            assertThat(sending.await(10, TimeUnit.SECONDS)).isTrue();
            tx.executeWithoutResult(status -> {
                policy.lockCurrent();
                jdbc.update("UPDATE test_email_policy SET enabled=false WHERE id=1");
                new JdbcNotificationEmailCancellation(jdbc).cancelAll();
            });
            assertThat(batch().status()).isEqualTo("SENDING");
            release.countDown();
            worker.get(10, TimeUnit.SECONDS);
        }
        assertThat(batch().status()).isEqualTo("SENT");
    }
}
