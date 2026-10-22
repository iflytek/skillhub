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
    NotificationEmailAdmissionProvider admission;
    Clock clock = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC);
    @BeforeAll
    static void schema() {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE notification_email_contact(user_id VARCHAR(128) PRIMARY KEY)");
        manager = new DataSourceTransactionManager(ds);
        tx = new TransactionTemplate(manager);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V121__notification_email_delivery.sql"))
                .execute(ds);
    }
    @BeforeEach
    void setup() {
        jdbc.execute(
                "TRUNCATE notification_email_item,notification_email_delivery,notification_email_cancel_barrier RESTART IDENTITY CASCADE");
        repository = new JdbcNotificationEmailDeliveryRepository(jdbc);
        policy = mock(NotificationEmailPolicyProvider.class);
        recipients = mock(NotificationEmailRecipientProvider.class);
        preferences = mock(NotificationEmailPreferenceService.class);
        eligibility = mock(NotificationEmailEligibility.class);
        admission = mock(NotificationEmailAdmissionProvider.class);
        when(admission.currentUnderLocks(anyString(), any()))
                .thenReturn(Optional.of(clock.instant().minus(Duration.ofDays(7))));
        when(policy.lockCurrent()).thenReturn(new NotificationEmailPolicy(true, 15, "https://example.test", 0));
        when(recipients.lockCurrent(anyString()))
                .thenAnswer(inv
                        -> Optional.of(
                                new NotificationEmailRecipient(inv.getArgument(0), "same@example.test", 0, "en")));
        when(preferences.lockCurrent(anyString()))
                .thenReturn(Map.of(NotificationCategory.PUBLISH, NotificationEmailMode.DIGEST,
                        NotificationCategory.REVIEW, NotificationEmailMode.DIGEST));
        doReturn(true).when(eligibility).eligible(any());
    }
    NotificationEmailCommand command(String user, String key, NotificationCategory category) {
        return new NotificationEmailCommand(user, category, "REVIEW_APPROVED", key, clock.instant(),
                "{\"skillId\":1,\"reason\":\"PRIVATE\"}", "SKILL", 1L);
    }
    NotificationEmailInboxService inbox() {
        return new NotificationEmailInboxService(
                policy, recipients, preferences, repository, eligibility, new ObjectMapper(), clock, admission);
    }
    void enqueue(NotificationEmailCommand command) {
        tx.executeWithoutResult(s -> {
            jdbc.update("INSERT INTO notification_email_contact(user_id) VALUES(?) ON CONFLICT DO NOTHING",
                    command.recipientId());
            inbox().enqueue(command);
        });
    }
    NotificationEmailDelivery batch() {
        return repository.list(null, 0, 100).getFirst();
    }
    NotificationEmailDelivery claim(String token) {
        NotificationEmailDelivery claimed = tx.execute(s
                -> repository.claim(clock.instant().plusSeconds(901), clock.instant().plusSeconds(1000), token)
                        .orElseThrow());
        for (int page = 0; page < 30 && "DIGEST".equals(claimed.kind())
                && (!claimed.recoveryRoot() || !claimed.sourcesComplete() || claimed.previewItemIds().isBlank());
                page++) {
            dispatcher((a, b, c, d, e) -> { throw new AssertionError("preparation cannot send"); }).process(claimed);
            var current = repository.find(claimed.id()).orElseThrow();
            if ("SUPPRESSED".equals(current.status()))
                return current;
            claimed = tx.execute(s
                    -> repository.claim(clock.instant().plusSeconds(901), clock.instant().plusSeconds(1000), token)
                            .orElseThrow());
        }
        return claimed;
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
        assertThat(repository.itemPage(batch().id(), 0, 100)).hasSize(15);
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
        assertThat(repository.itemPage(batch().id(), 0, 100)).hasSize(1);
    }

    @Test
    void lateArrivalDoesNotReopenClaimedBatch() {
        enqueue(command("u", "one", NotificationCategory.PUBLISH));
        var closed = claim("a");
        enqueue(command("u", "two", NotificationCategory.REVIEW));
        assertThat(repository.count(null)).isEqualTo(2);
        assertThat(repository.itemPage(closed.id(), 0, 100)).hasSize(1);
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
                    .contains("Original notification records: 55", "Publishing (preview)", "Reviews (preview)",
                            "/dashboard/notifications/email/" + id)
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
        assertThat(sent)
                .singleElement()
                .asString()
                .contains("Reviews (preview)")
                .doesNotContain("Publishing (preview)");
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
    void retryWindowExpiresWithoutAnotherSmtpAttempt() {
        enqueue(command("u", "one", NotificationCategory.PUBLISH));
        var claimed = claim("last");
        jdbc.update("UPDATE notification_email_delivery SET retry_started_at=?,retry_deadline=? WHERE id=?",
                java.sql.Timestamp.from(clock.instant().minusSeconds(86400)), java.sql.Timestamp.from(clock.instant()),
                claimed.id());
        var sender = mock(NotificationEmailSender.class);
        dispatcher(sender).process(repository.find(claimed.id()).orElseThrow());
        assertThat(repository.find(claimed.id()).orElseThrow().status()).isEqualTo("FAILED");
        assertThat(repository.find(claimed.id()).orElseThrow().resultCode()).isEqualTo("RETRY_WINDOW_EXPIRED");
        verifyNoInteractions(sender);
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
        assertThat(repository.itemPage(id, 0, 100)).hasSize(1);
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
    private void backlog(String user, int windows, int perWindow) {
        tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO notification_email_contact(user_id) VALUES(?) ON CONFLICT DO NOTHING", user);
            for (int window = 0; window < windows; window++) {
                var row =
                        repository.openBatch(user, "DIGEST", "backlog-" + window, 0, clock.instant(), clock.instant());
                for (int item = 0; item < perWindow; item++)
                    repository.addItem(row.id(),
                            command(user, "window-" + window + "-" + item, NotificationCategory.PUBLISH), 1L,
                            clock.instant());
            }
        });
    }

    private NotificationEmailDelivery rawClaim(Instant now) {
        return tx.execute(
                status -> repository.claim(now, now.plusSeconds(120), UUID.randomUUID().toString()).orElseThrow());
    }

    private NotificationEmailDispatcher at(Instant now, NotificationEmailSender sender) {
        return new NotificationEmailDispatcher(repository, policy, recipients, preferences, eligibility, sender,
                new NotificationEmailDeliveryProperties(), Clock.fixed(now, ZoneOffset.UTC), manager);
    }

    @Test
    void thousandOverdueWindowsAreSealedInBoundedPagesAndSendOneRootWithoutMovingItems() {
        backlog("u", 1000, 1);
        var sender = mock(NotificationEmailSender.class);
        when(sender.send(any(), any(), anyLong(), any(), any())).thenReturn(NotificationEmailSender.Result.ACCEPTED);
        var worker = dispatcher(sender);
        var first = rawClaim(clock.instant().plusSeconds(901));
        worker.process(first);
        assertThat(jdbc.queryForObject(
                           "SELECT count(*) FROM notification_email_delivery WHERE status='MERGED'", Long.class))
                .isEqualTo(100);
        assertThat(repository.find(first.id()).orElseThrow().status()).isEqualTo("PREPARING");
        verifyNoInteractions(sender);
        tx.executeWithoutResult(status
                -> repository.cleanup(clock.instant().plusSeconds(9999999), clock.instant().plusSeconds(9999999)));
        assertThat(repository.count(null)).isEqualTo(1000);
        for (int page = 0; page < 15 && !"SENT".equals(repository.find(first.id()).orElseThrow().status()); page++)
            worker.process(rawClaim(clock.instant().plusSeconds(901)));
        var root = repository.find(first.id()).orElseThrow();
        assertThat(root.status()).isEqualTo("SENT");
        assertThat(root.itemCount()).isEqualTo(1000);
        assertThat(jdbc.queryForObject(
                           "SELECT count(*) FROM notification_email_delivery WHERE status='MERGED'", Long.class))
                .isEqualTo(999);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT delivery_id) FROM notification_email_item", Long.class))
                .isEqualTo(1000);
        assertThat(repository.itemPage(root.id(), 0, 21)).hasSize(21);
        verify(sender, times(1))
                .send(any(), any(), eq(root.id()), any(), contains("Original notification records: 1000"));
    }

    @Test
    void oldClaimedSourceCannotAuthorizeAfterRootExistsEvenBeforeItsPageIsSealed() {
        backlog("u", 150, 1);
        var now = clock.instant().plusSeconds(901);
        var first = rawClaim(now);
        // Claim the last source to ensure the first merge page has not yet reached it.
        var last = repository.list(null, 0, 1).getFirst();
        jdbc.update(
                "UPDATE notification_email_delivery SET status='CLAIMED',lease_token='old',lease_until=? WHERE id=?",
                java.sql.Timestamp.from(now.plusSeconds(120)), last.id());
        var legacy = repository.find(last.id()).orElseThrow();
        var sender = mock(NotificationEmailSender.class);
        dispatcher(sender).process(first);
        assertThat(repository.find(last.id()).orElseThrow().status()).isEqualTo("CLAIMED");
        dispatcher(sender).process(legacy);
        verifyNoInteractions(sender);
        assertThat(repository.find(last.id()).orElseThrow().status()).isEqualTo("CLAIMED");
        assertThat(repository.activeRoot("u")).isPresent();
    }

    @Test
    void lateEligibleItemSurvivesBoundedEmptyPagesAndCancelledSourcesNeverReappear() {
        backlog("u", 1, 350);
        when(eligibility.eligible(any())).thenAnswer(inv -> ((NotificationEmailItem) inv.getArgument(0)).id() == 350);
        var sender = mock(NotificationEmailSender.class);
        var worker = dispatcher(sender);
        var first = rawClaim(clock.instant().plusSeconds(901));
        worker.process(first);
        var root = repository.find(first.id()).orElseThrow();
        assertThat(root.status()).isEqualTo("PREPARING");
        assertThat(root.itemCursor()).isEqualTo(100);
        verifyNoInteractions(sender);
        for (int page = 0; page < 3; page++) worker.process(rawClaim(clock.instant().plusSeconds(901)));
        assertThat(repository.find(root.id()).orElseThrow().previewItemIds()).isEqualTo("350");
        tx.executeWithoutResult(status
                -> new JdbcNotificationEmailCancellation(jdbc).cancelCategories(
                        "u", Set.of(NotificationCategory.PUBLISH)));
        doReturn(true).when(eligibility).eligible(any());
        worker.process(rawClaim(clock.instant().plusSeconds(901)));
        assertThat(repository.find(root.id()).orElseThrow().status()).isEqualTo("SUPPRESSED");
        verifyNoInteractions(sender);
    }

    @Test
    void temporaryOutageAddsLaterWindowsToSameRootWithoutExtendingFirstDeadline() {
        backlog("u", 1, 1);
        var first = claim("initial");
        dispatcher((a, b, c, d, e) -> NotificationEmailSender.Result.TEMPORARY_FAILURE).process(first);
        var failed = repository.find(first.id()).orElseThrow();
        assertThat(failed.retryDeadline()).isEqualTo(clock.instant().plusSeconds(901 + 86400));
        tx.executeWithoutResult(status -> {
            for (int hour = 1; hour <= 3; hour++) {
                var row = repository.openBatch(
                        "u", "DIGEST", "later-" + hour, 0, clock.instant().plusSeconds(hour * 3600), clock.instant());
                repository.addItem(
                        row.id(), command("u", "later-" + hour, NotificationCategory.PUBLISH), 1L, clock.instant());
            }
        });
        Instant recovered = clock.instant().plusSeconds(4 * 3600);
        var sender = mock(NotificationEmailSender.class);
        when(sender.send(any(), any(), anyLong(), any(), any())).thenReturn(NotificationEmailSender.Result.ACCEPTED);
        var worker = at(recovered, sender);
        worker.process(rawClaim(recovered));
        var root = repository.find(first.id()).orElseThrow();
        assertThat(root.id()).isEqualTo(first.id());
        assertThat(root.itemCount()).isEqualTo(4);
        assertThat(root.retryDeadline()).isEqualTo(failed.retryDeadline());
        worker.process(rawClaim(recovered));
        verify(sender, times(1))
                .send(any(), any(), eq(first.id()), any(), contains("Original notification records: 4"));
        assertThat(repository.count("SENT")).isEqualTo(1);
        assertThat(repository.count("MERGED")).isEqualTo(3);
    }

    @Test
    void mergedSourcesAreCancelledAndCleanupRetainsWholeGroupUntilTerminalAndReplaySafe() {
        backlog("u", 4, 1);
        var first = rawClaim(clock.instant().plusSeconds(901));
        var worker = dispatcher(mock(NotificationEmailSender.class));
        worker.process(first);
        assertThat(repository.count("MERGED")).isEqualTo(3);
        tx.executeWithoutResult(status
                -> new JdbcNotificationEmailCancellation(jdbc).cancelCategories(
                        "u", Set.of(NotificationCategory.PUBLISH)));
        tx.executeWithoutResult(status -> repository.drainCancelledItems());
        assertThat(jdbc.queryForObject(
                           "SELECT count(*) FROM notification_email_item WHERE status='SUPPRESSED'", Long.class))
                .isEqualTo(4);
        worker.process(rawClaim(clock.instant().plusSeconds(901)));
        assertThat(repository.find(first.id()).orElseThrow().status()).isEqualTo("SUPPRESSED");
        // A recent source keeps the root and all event deduplication facts, despite its old creation time.
        jdbc.update("UPDATE notification_email_item SET enqueued_at=? WHERE id=4",
                java.sql.Timestamp.from(clock.instant().plusSeconds(1000)));
        tx.executeWithoutResult(
                status -> repository.cleanup(clock.instant().plusSeconds(902), clock.instant().plusSeconds(902)));
        assertThat(repository.count(null)).isEqualTo(4);
        tx.executeWithoutResult(
                status -> repository.cleanup(clock.instant().plusSeconds(1001), clock.instant().plusSeconds(1001)));
        assertThat(repository.count(null)).isZero();
    }

    @Test
    void knownTemporarySourceContributesEarliestDeadlineAndExpiredSourceDoesNotRevive() {
        backlog("u", 3, 1);
        Instant now = clock.instant().plusSeconds(901);
        jdbc.update("UPDATE notification_email_delivery SET retry_started_at=?,retry_deadline=? WHERE id=2",
                java.sql.Timestamp.from(now.minusSeconds(100)), java.sql.Timestamp.from(now.plusSeconds(100)));
        jdbc.update("UPDATE notification_email_delivery SET retry_started_at=?,retry_deadline=? WHERE id=3",
                java.sql.Timestamp.from(now.minusSeconds(86500)), java.sql.Timestamp.from(now.minusSeconds(1)));
        var first = rawClaim(now);
        dispatcher(mock(NotificationEmailSender.class)).process(first);
        var root = repository.find(first.id()).orElseThrow();
        assertThat(root.retryDeadline()).isEqualTo(now.plusSeconds(100));
        assertThat(root.itemCount()).isEqualTo(2);
        assertThat(repository.find(3).orElseThrow().status()).isEqualTo("FAILED");
        assertThat(repository.find(3).orElseThrow().mergedIntoDeliveryId()).isNull();
    }

    @Test
    void immediateAndDigestRoundsAlternateAndOneUserCannotConsumeWholeRound() {
        backlog("u", 1, 1);
        tx.executeWithoutResult(status -> {
            for (int item = 0; item < 10; item++) {
                var row =
                        repository.openBatch("u", "IMMEDIATE", "instant-" + item, 0, clock.instant(), clock.instant());
                repository.addItem(
                        row.id(), command("u", "instant-" + item, NotificationCategory.PUBLISH), 1L, clock.instant());
            }
        });
        var sender = mock(NotificationEmailSender.class);
        when(sender.send(any(), any(), anyLong(), any(), any())).thenReturn(NotificationEmailSender.Result.ACCEPTED);
        var worker = dispatcher(sender);
        worker.dispatch(); // digest preparation, one user work item only
        assertThat(repository.count("SENT")).isZero();
        worker.dispatch(); // immediate turn
        assertThat(repository.count("SENT")).isEqualTo(1);
        worker.dispatch(); // prepared digest turn, despite more immediate work
        assertThat(repository.find(1).orElseThrow().status()).isEqualTo("SENT");
        verify(sender, times(2)).send(any(), any(), anyLong(), any(), any());
    }

    @Test
    void delayedEventsFromDisabledIntervalAreRejectedButExactReenableBoundaryIsAccepted() {
        Instant enabled = clock.instant().minusSeconds(60);
        when(admission.currentUnderLocks(eq("u"), any())).thenReturn(Optional.of(enabled));
        enqueue(new NotificationEmailCommand("u", NotificationCategory.PUBLISH, "SKILL_PUBLISHED", "disabled",
                enabled.minusSeconds(1), null, "SKILL", 1L));
        assertThat(repository.count(null)).isZero();
        enqueue(new NotificationEmailCommand(
                "u", NotificationCategory.PUBLISH, "SKILL_PUBLISHED", "boundary", enabled, null, "SKILL", 1L));
        assertThat(repository.count(null)).isEqualTo(1);
        // Watermark changes only affect new admission, not existing SMTP recovery work.
        var claimed = claim("admitted");
        when(admission.currentUnderLocks(eq("u"), any())).thenReturn(Optional.of(clock.instant().plusSeconds(1000)));
        dispatcher((a, b, c, d, e) -> NotificationEmailSender.Result.ACCEPTED).process(claimed);
        assertThat(repository.find(claimed.id()).orElseThrow().status()).isEqualTo("SENT");
    }

    @Test
    void contactGateSerializesTwoPreviouslyClaimedWindowsIntoOnlyOneRoot() throws Exception {
        backlog("u", 2, 1);
        Instant now = clock.instant().plusSeconds(901);
        var one = rawClaim(now);
        var two = rawClaim(now);
        CountDownLatch start = new CountDownLatch(1);
        var sender = mock(NotificationEmailSender.class);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> {
                start.await();
                dispatcher(sender).process(one);
                return null;
            });
            var b = pool.submit(() -> {
                start.await();
                dispatcher(sender).process(two);
                return null;
            });
            start.countDown();
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject(
                           "SELECT count(*) FROM notification_email_delivery WHERE is_recovery_root", Long.class))
                .isEqualTo(1);
        var root = repository.activeRoot("u").orElseThrow();
        assertThat(root.itemCount()).isEqualTo(2);
        assertThat(repository.count("MERGED")).isEqualTo(1);
        verifyNoInteractions(sender);
        dispatcher((a, b, c, d, e) -> NotificationEmailSender.Result.ACCEPTED).process(rawClaim(now));
        assertThat(repository.find(root.id()).orElseThrow().status()).isEqualTo("SENT");
    }

    @Test
    void manualRetryResetsWindowOnceAndOldMergedDeadlineDoesNotFlowBack() {
        backlog("u", 2, 1);
        var claimed = claim("one");
        var root = repository.find(claimed.id()).orElseThrow();
        Instant previous = clock.instant().plusSeconds(1000);
        jdbc.update("UPDATE notification_email_delivery SET retry_started_at=?,retry_deadline=? WHERE id<>?",
                java.sql.Timestamp.from(previous.minusSeconds(86400)), java.sql.Timestamp.from(previous), root.id());
        dispatcher((a, b, c, d, e) -> NotificationEmailSender.Result.PERMANENT_FAILURE).process(claimed);
        jdbc.update("UPDATE notification_email_delivery SET retry_started_at=?,retry_deadline=? WHERE id=?",
                java.sql.Timestamp.from(previous.minusSeconds(86400)), java.sql.Timestamp.from(previous), root.id());
        var audit = mock(com.iflytek.skillhub.domain.audit.AuditLogService.class);
        var query =
                new NotificationEmailDeliveryQueryService(repository, eligibility, audit, clock, policy, recipients);
        tx.executeWithoutResult(status -> query.retry(root.id(), "admin", false));
        assertThat(repository.find(root.id()).orElseThrow().retryDeadline()).isNull();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> query.retry(root.id(), "admin", false)))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);
        var restored = claim("manual");
        assertThat(repository.find(root.id()).orElseThrow().retryDeadline()).isNull();
        assertThat(repository.find(root.id()).orElseThrow().itemCount()).isEqualTo(2);
        dispatcher((a, b, c, d, e) -> NotificationEmailSender.Result.TEMPORARY_FAILURE).process(restored);
        assertThat(repository.find(root.id()).orElseThrow().retryDeadline())
                .isEqualTo(clock.instant().plusSeconds(901 + 86400));
        verify(audit).record(eq("admin"), eq("NOTIFICATION_EMAIL_RETRY"), eq("NOTIFICATION_EMAIL_DELIVERY"),
                eq(root.id()), isNull(), isNull(), isNull(), contains("recoveryWindowReset"));
    }

    @Test
    void terminalRetentionStartsAfterSuccessfulRecoveryRatherThanOldBatchCreation() {
        backlog("u", 1, 1);
        var claimed = claim("old");
        dispatcher((a, b, c, d, e) -> NotificationEmailSender.Result.ACCEPTED).process(claimed);
        jdbc.update("UPDATE notification_email_delivery SET created_at=? WHERE id=?",
                java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(40))), claimed.id());
        jdbc.update("UPDATE notification_email_item SET enqueued_at=?",
                java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(40))));
        tx.executeWithoutResult(status -> repository.cleanup(clock.instant(), clock.instant()));
        assertThat(repository.find(claimed.id())).isPresent();
        tx.executeWithoutResult(
                status -> repository.cleanup(clock.instant().plusSeconds(902), clock.instant().plusSeconds(902)));
        assertThat(repository.find(claimed.id())).isEmpty();
    }
    @Test
    void recoveryDeadlineOnlyBlocksNewAuthorizationNotAcceptedInFlightSmtp() {
        backlog("u", 1, 1);
        var claimed = claim("deadline");
        var time = new java.util.concurrent.atomic.AtomicReference<>(clock.instant().plusSeconds(901));
        Clock mutable = new Clock() {
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }
            public Clock withZone(ZoneId zone) {
                return this;
            }
            public Instant instant() {
                return time.get();
            }
        };
        jdbc.update("UPDATE notification_email_delivery SET retry_started_at=?,retry_deadline=? WHERE id=?",
                java.sql.Timestamp.from(time.get().minusSeconds(86399)),
                java.sql.Timestamp.from(time.get().plusSeconds(1)), claimed.id());
        var worker = new NotificationEmailDispatcher(
                repository, policy, recipients, preferences, eligibility, (a, b, c, d, e) -> {
                    time.set(time.get().plusSeconds(2));
                    return NotificationEmailSender.Result.ACCEPTED;
                }, new NotificationEmailDeliveryProperties(), mutable, manager);
        worker.process(repository.find(claimed.id()).orElseThrow());
        assertThat(repository.find(claimed.id()).orElseThrow().status()).isEqualTo("SENT");
    }

    @Test
    void chinesePreviewUsesReadableCategorySectionsWithoutExposingBody() {
        enqueue(command("u", "publish", NotificationCategory.PUBLISH));
        enqueue(command("u", "review", NotificationCategory.REVIEW));
        var claimed = claim("localized");
        when(recipients.lockCurrent("u"))
                .thenReturn(Optional.of(new NotificationEmailRecipient("u", "same@example.test", 0, "zh")));
        var sent = new ArrayList<String>();
        dispatcher((a, b, c, d, e) -> {
            sent.add(e);
            return NotificationEmailSender.Result.ACCEPTED;
        }).process(claimed);
        assertThat(sent)
                .singleElement()
                .asString()
                .contains("发布动态（预览）", "审核进度（预览）", "原始通知记录数：2")
                .doesNotContain("PUBLISH", "REVIEW", "PRIVATE", "reason", "skillId");
    }

    @Test
    void leaseRecoveryIsBoundedAndResumesOnNextRound() {
        backlog("u", 150, 1);
        jdbc.update("UPDATE notification_email_delivery SET status='CLAIMED',lease_token='dead',lease_until=?",
                java.sql.Timestamp.from(clock.instant().minusSeconds(1)));
        tx.executeWithoutResult(status -> repository.recoverExpired(clock.instant()));
        assertThat(repository.count("RETRY")).isEqualTo(100);
        assertThat(repository.count("CLAIMED")).isEqualTo(50);
        tx.executeWithoutResult(status -> repository.recoverExpired(clock.instant()));
        assertThat(repository.count("RETRY")).isEqualTo(150);
    }

    enum ClosingScope { GLOBAL, CATEGORY, ACCOUNT }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> closingOutcomes() {
        return Arrays.stream(ClosingScope.values())
                .flatMap(scope
                        -> java.util.stream.Stream
                                .of(NotificationEmailSender.Result.ACCEPTED,
                                        NotificationEmailSender.Result.TEMPORARY_FAILURE,
                                        NotificationEmailSender.Result.UNKNOWN)
                                .map(result -> org.junit.jupiter.params.provider.Arguments.of(scope, result)));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("closingOutcomes")
    void closingDuringSendingPreservesOutcomeButCannotReviveCancelledSourceOnRetry(
            ClosingScope scope, NotificationEmailSender.Result result) {
        enqueue(command("u", "publish", NotificationCategory.PUBLISH));
        tx.executeWithoutResult(status -> {
            var source = repository.openBatch("u", "DIGEST", "review-source", 0, clock.instant(), clock.instant());
            repository.addItem(source.id(), command("u", "review", NotificationCategory.REVIEW), 1L, clock.instant());
        });
        var claimed = claim("inflight");
        assertThat(repository.count("MERGED")).isEqualTo(1);
        var attempted = new java.util.concurrent.atomic.AtomicInteger();
        NotificationEmailSender sender = (a, b, c, d, e) -> {
            attempted.incrementAndGet();
            tx.executeWithoutResult(status -> {
                assertThat(repository.find(claimed.id()).orElseThrow().status()).isEqualTo("SENDING");
                var cancellation = new JdbcNotificationEmailCancellation(jdbc);
                if (scope == ClosingScope.GLOBAL)
                    cancellation.cancelAll();
                else
                    cancellation.cancelCategories("u",
                            scope == ClosingScope.CATEGORY ? Set.of(NotificationCategory.PUBLISH)
                                                           : Set.of(NotificationCategory.values()));
                // Current payload/result remains intact even after sweep catches up; re-enable does not clear barriers.
                repository.drainCancelledItems();
                assertThat(repository.find(claimed.id()).orElseThrow().status()).isEqualTo("SENDING");
                assertThat(repository.itemPage(claimed.id(), 0, 100)).allMatch(item -> "ACTIVE".equals(item.status()));
            });
            return result;
        };
        dispatcher(sender).process(claimed);
        assertThat(attempted).hasValue(1);
        String expected = switch (result) {
            case ACCEPTED -> "SENT";
            case UNKNOWN -> "UNKNOWN";
            default -> "RETRY";
        };
        assertThat(repository.find(claimed.id()).orElseThrow().status()).isEqualTo(expected);
        if (result != NotificationEmailSender.Result.TEMPORARY_FAILURE) {
            Optional<NotificationEmailDelivery> replay = tx.execute(status
                    -> repository.claim(
                            clock.instant().plusSeconds(1200), clock.instant().plusSeconds(1300), "no-replay"));
            assertThat(replay).isEmpty();
            return;
        }
        Instant recovered = clock.instant().plusSeconds(1200);
        var retries = new ArrayList<String>();
        var worker = at(recovered, (a, b, c, d, e) -> {
            retries.add(e);
            return NotificationEmailSender.Result.ACCEPTED;
        });
        worker.process(rawClaim(recovered));
        if (scope == ClosingScope.CATEGORY) {
            worker.process(rawClaim(recovered));
            assertThat(retries)
                    .singleElement()
                    .asString()
                    .contains("Reviews (preview)")
                    .doesNotContain("Publishing (preview)");
            assertThat(repository.find(claimed.id()).orElseThrow().status()).isEqualTo("SENT");
        } else {
            assertThat(retries).isEmpty();
            assertThat(repository.find(claimed.id()).orElseThrow().status()).isEqualTo("SUPPRESSED");
        }
    }

    @Test
    void closingWritesOnlyBarrierAndSweepNeverLoadsMoreThanHundredItems() {
        backlog("u", 1, 1000);
        tx.executeWithoutResult(status -> new JdbcNotificationEmailCancellation(jdbc).cancelAll());
        assertThat(
                jdbc.queryForObject("SELECT count(*) FROM notification_email_item WHERE status='ACTIVE'", Long.class))
                .isEqualTo(1000);
        assertThat(repository.count("PENDING")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                           "SELECT through_item_id FROM notification_email_cancel_barrier WHERE scope_key='GLOBAL'",
                           Long.class))
                .isEqualTo(1000);
        Integer scanned = tx.execute(status -> repository.drainCancelledItems());
        assertThat(scanned).isEqualTo(100);
        assertThat(jdbc.queryForObject(
                           "SELECT count(*) FROM notification_email_item WHERE status='SUPPRESSED'", Long.class))
                .isEqualTo(100);
        assertThat(
                jdbc.queryForObject(
                        "SELECT cursor_id FROM notification_email_cancel_barrier WHERE scope_key='GLOBAL'", Long.class))
                .isEqualTo(100);
        // Even before sweep reaches the tail, final authorization reads the barrier directly.
        assertThat(repository.cancelledItemIds(List.of(1000L))).containsExactly(1000L);
    }
    @Test
    void reenabledNewInboxItemJoinsSameRetryRootWithoutRevivingOldItemsAndSentHistorySurvivesLaterClose() {
        enqueue(command("u", "before-close", NotificationCategory.PUBLISH));
        var claimed = claim("first-attempt");
        dispatcher((a, b, c, d, e) -> {
            tx.executeWithoutResult(status -> {
                new JdbcNotificationEmailCancellation(jdbc).cancelAll();
                repository.drainCancelledItems();
                assertThat(repository.find(claimed.id()).orElseThrow().status()).isEqualTo("SENDING");
            });
            return NotificationEmailSender.Result.TEMPORARY_FAILURE;
        }).process(claimed);
        Instant reopened = clock.instant().plusSeconds(910);
        Clock reopenedClock = Clock.fixed(reopened, ZoneOffset.UTC);
        var newInbox = new NotificationEmailInboxService(
                policy, recipients, preferences, repository, eligibility, new ObjectMapper(), reopenedClock, admission);
        tx.executeWithoutResult(status
                -> newInbox.enqueue(new NotificationEmailCommand("u", NotificationCategory.PUBLISH, "SKILL_PUBLISHED",
                        "after-reenable", reopened, null, "SKILL", 1L)));
        assertThat(repository.count(null)).isEqualTo(2);
        Instant recovered = clock.instant().plusSeconds(1901);
        var sent = new ArrayList<String>();
        var worker = at(recovered, (a, b, c, d, e) -> {
            sent.add(e);
            return NotificationEmailSender.Result.ACCEPTED;
        });
        worker.process(rawClaim(recovered));
        worker.process(rawClaim(recovered));
        var root = repository.find(claimed.id()).orElseThrow();
        assertThat(root.status()).isEqualTo("SENT");
        assertThat(root.itemCount()).isEqualTo(2);
        assertThat(repository.count("MERGED")).isEqualTo(1);
        assertThat(sent).singleElement().asString().contains("Original notification records: 2");
        assertThat(sent.getFirst().lines().filter(line -> line.contains(" · ")).count()).isEqualTo(1);
        var items = repository.itemPage(root.id(), 0, 100);
        assertThat(items)
                .filteredOn(item -> item.eventKey().equals("before-close"))
                .singleElement()
                .extracting(NotificationEmailItem::status)
                .isEqualTo("SUPPRESSED");
        assertThat(items)
                .filteredOn(item -> item.eventKey().equals("after-reenable"))
                .singleElement()
                .extracting(NotificationEmailItem::status)
                .isEqualTo("ACTIVE");
        tx.executeWithoutResult(status -> {
            new JdbcNotificationEmailCancellation(jdbc).cancelAll();
            repository.drainCancelledItems();
        });
        // Completed sent history is not rewritten by future switches; barriers restrict future authorization only.
        assertThat(repository.find(root.id()).orElseThrow().status()).isEqualTo("SENT");
        assertThat(repository.itemPage(root.id(), 0, 100))
                .filteredOn(item -> item.eventKey().equals("after-reenable"))
                .singleElement()
                .extracting(NotificationEmailItem::status)
                .isEqualTo("ACTIVE");
    }
    private NotificationEmailDeliveryQueryService query(Clock queryClock) {
        return new NotificationEmailDeliveryQueryService(repository, eligibility,
                mock(com.iflytek.skillhub.domain.audit.AuditLogService.class), queryClock, policy, recipients);
    }

    @Test
    void closingLargeRecoveryRootTerminatesOnlyBatchThenSweepsHundredAndHidesUnscannedDetails() {
        backlog("u", 1000, 2);
        var claimed = claim("large-root");
        assertThat(repository.count("MERGED")).isEqualTo(999);
        tx.executeWithoutResult(status -> new JdbcNotificationEmailCancellation(jdbc).cancelAll());
        when(policy.lockCurrent()).thenReturn(new NotificationEmailPolicy(false, 15, "https://example.test", 1));
        var sender = mock(NotificationEmailSender.class);
        dispatcher(sender).process(claimed);
        var root = repository.find(claimed.id()).orElseThrow();
        assertThat(root.status()).isEqualTo("SUPPRESSED");
        assertThat(root.itemCount()).isEqualTo(2000);
        assertThat(
                jdbc.queryForObject("SELECT count(*) FROM notification_email_item WHERE status='ACTIVE'", Long.class))
                .isEqualTo(2000);
        var detail = query(clock).detail(root.id(), "u", null, 100);
        assertThat(detail.items()).isEmpty();
        assertThat(detail.unavailableCount()).isEqualTo(100);
        assertThat(detail.nextCursor()).isNotNull();
        var source = repository.list("MERGED", 0, 1).getFirst();
        assertThat(query(clock).detail(source.id(), "u", null, 100).items()).isEmpty();
        Integer scanned = tx.execute(status -> repository.drainCancelledItems());
        assertThat(scanned).isEqualTo(100);
        assertThat(jdbc.queryForObject(
                           "SELECT count(*) FROM notification_email_item WHERE status='SUPPRESSED'", Long.class))
                .isEqualTo(100);
        assertThat(
                jdbc.queryForObject("SELECT count(*) FROM notification_email_item WHERE status='ACTIVE'", Long.class))
                .isEqualTo(1900);
        verifyNoInteractions(sender);
    }

    @Test
    void nonSwitchSuppressionIsLogicalImmediatelyAndUsesSeparateBoundedPhysicalCursor() {
        backlog("u", 10, 150);
        var claimed = claim("recipient-changed");
        tx.executeWithoutResult(status -> repository.suppress(claimed.id(), "stale-token", "INVALID", clock.instant()));
        assertThat(repository.find(claimed.id()).orElseThrow().status()).isEqualTo("CLAIMED");
        assertThat(
                jdbc.queryForObject("SELECT count(*) FROM notification_email_item WHERE status='ACTIVE'", Long.class))
                .isEqualTo(1500);
        when(recipients.lockCurrent("u"))
                .thenReturn(Optional.of(new NotificationEmailRecipient("u", "new@example.test", 1, "en")));
        dispatcher(mock(NotificationEmailSender.class)).process(claimed);
        var root = repository.find(claimed.id()).orElseThrow();
        assertThat(root.status()).isEqualTo("SUPPRESSED");
        assertThat(root.itemCursor()).isZero();
        assertThat(root.itemsComplete()).isFalse();
        assertThat(
                jdbc.queryForObject("SELECT count(*) FROM notification_email_item WHERE status='ACTIVE'", Long.class))
                .isEqualTo(1500);
        assertThat(query(clock).detail(root.id(), "u", null, 100).items()).isEmpty();
        var source = repository.list("MERGED", 0, 1).getFirst();
        assertThat(query(clock).detail(source.id(), "u", null, 100).items()).isEmpty();
        Integer scanned = tx.execute(status -> repository.drainCancelledItems());
        assertThat(scanned).isEqualTo(100);
        assertThat(repository.find(root.id()).orElseThrow().itemCursor()).isEqualTo(100);
        assertThat(repository.find(root.id()).orElseThrow().itemsComplete()).isFalse();
        assertThat(jdbc.queryForObject(
                           "SELECT count(*) FROM notification_email_item WHERE status='SUPPRESSED'", Long.class))
                .isEqualTo(100);
        assertThat(
                jdbc.queryForObject("SELECT count(*) FROM notification_email_item WHERE status='ACTIVE'", Long.class))
                .isEqualTo(1400);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"FAILED,false", "FAILED,true", "UNKNOWN,false", "UNKNOWN,true"})
    void manualRetryCannotBypassCancellationBeforeSweepButNewLegalItemStillWorks(String outcome, boolean withNewItem) {
        backlog("u", 2, 1);
        var claimed = claim("original");
        dispatcher((a, b, c, d, e)
                           -> "UNKNOWN".equals(outcome) ? NotificationEmailSender.Result.UNKNOWN
                                                        : NotificationEmailSender.Result.PERMANENT_FAILURE)
                .process(claimed);
        tx.executeWithoutResult(status -> new JdbcNotificationEmailCancellation(jdbc).cancelAll());
        assertThat(
                jdbc.queryForObject("SELECT count(*) FROM notification_email_item WHERE status='ACTIVE'", Long.class))
                .isEqualTo(2);
        var before = query(clock).detail(claimed.id(), "u", null, 100);
        assertThat(before.items()).isEmpty();
        assertThat(before.unavailableCount()).isEqualTo(2);
        Instant reopened = clock.instant().plusSeconds(910);
        Clock later = Clock.fixed(reopened, ZoneOffset.UTC);
        var service = query(later);
        tx.executeWithoutResult(status -> service.retry(claimed.id(), "admin", true));
        assertThat(query(later).detail(claimed.id(), "u", null, 100).items()).isEmpty();
        if (withNewItem) {
            var newInbox = new NotificationEmailInboxService(
                    policy, recipients, preferences, repository, eligibility, new ObjectMapper(), later, admission);
            tx.executeWithoutResult(status
                    -> newInbox.enqueue(new NotificationEmailCommand("u", NotificationCategory.PUBLISH,
                            "SKILL_PUBLISHED", "new-after-manual", reopened, null, "SKILL", 1L)));
        }
        Instant recovered = clock.instant().plusSeconds(1901);
        var mail = new ArrayList<String>();
        var worker = at(recovered, (a, b, c, d, e) -> {
            mail.add(e);
            return NotificationEmailSender.Result.ACCEPTED;
        });
        worker.process(rawClaim(recovered));
        if (withNewItem) {
            worker.process(rawClaim(recovered));
            assertThat(mail)
                    .singleElement()
                    .asString()
                    .contains(reopened.toString())
                    .doesNotContain(clock.instant().toString());
            assertThat(mail.getFirst().lines().filter(line -> line.contains(" · ")).count()).isEqualTo(1);
            assertThat(repository.find(claimed.id()).orElseThrow().itemCount()).isEqualTo(3);
            assertThat(query(later).detail(claimed.id(), "u", null, 100).items()).hasSize(1);
        } else {
            assertThat(mail).isEmpty();
            assertThat(repository.find(claimed.id()).orElseThrow().status()).isEqualTo("SUPPRESSED");
            assertThat(query(later).detail(claimed.id(), "u", null, 100).items()).isEmpty();
        }
    }
    @Test
    void realSpringSmtpConnectionRefusalStartsPersistentTwentyFourHourRecovery() throws Exception {
        enqueue(command("u", "real-smtp-refusal", NotificationCategory.PUBLISH));
        var claimed = claim("real-connection");
        try (var reserved = new java.net.Socket()) {
            reserved.bind(new java.net.InetSocketAddress("127.0.0.1", 0));
            var mail = new org.springframework.mail.javamail.JavaMailSenderImpl();
            mail.setHost("127.0.0.1");
            mail.setPort(reserved.getLocalPort());
            mail.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "300");
            mail.getJavaMailProperties().setProperty("mail.smtp.timeout", "300");
            var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory();
            beans.addBean("mailSender", mail);
            var realSender = new SmtpNotificationEmailSender(
                    beans.getBeanProvider(org.springframework.mail.javamail.JavaMailSender.class),
                    "notify@example.test", "SkillHub");
            dispatcher(realSender).process(claimed);
        }
        var root = repository.find(claimed.id()).orElseThrow();
        assertThat(root.status()).isEqualTo("RETRY");
        assertThat(root.resultCode()).isEqualTo("TEMPORARY_FAILURE");
        assertThat(root.retryStartedAt()).isEqualTo(clock.instant().plusSeconds(901));
        assertThat(root.retryDeadline()).isEqualTo(clock.instant().plusSeconds(901 + 86400));
    }
}
