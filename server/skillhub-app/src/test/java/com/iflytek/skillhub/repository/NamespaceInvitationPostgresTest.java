package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.support.PostgresTestImage;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.auth.merge.AccountMergeRequest;
import com.iflytek.skillhub.auth.merge.AccountMergeRequestRepository;
import com.iflytek.skillhub.auth.merge.AccountMergeService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceInvitationService;
import com.iflytek.skillhub.domain.namespace.NamespaceAccessPolicy;
import com.iflytek.skillhub.domain.namespace.NamespaceGovernanceService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberService;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.infra.jpa.NamespaceInvitationJpaRepository;
import com.iflytek.skillhub.infra.jpa.NamespaceJpaRepository;
import com.iflytek.skillhub.infra.jpa.NamespaceMemberJpaRepository;
import com.iflytek.skillhub.infra.jpa.UserAccountJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Import({NamespaceInvitationService.class, NamespaceMemberService.class, NamespaceService.class,
        AccountMergeService.class,
        NamespaceAccessPolicy.class, NamespaceGovernanceService.class, AuditLogService.class,
        NamespaceInvitationPostgresTest.ClockConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NamespaceInvitationPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean Clock clock() { return Clock.systemUTC(); }
    }

    @Autowired private NamespaceInvitationService invitations;
    @Autowired private NamespaceMemberService memberService;
    @Autowired private NamespaceGovernanceService governance;
    @Autowired private NamespaceService namespaceService;
    @Autowired private AccountMergeService accountMerge;
    @Autowired private AccountMergeRequestRepository mergeRequests;
    @Autowired private NamespaceInvitationJpaRepository invitationRows;
    @Autowired private com.iflytek.skillhub.infra.jpa.NamespaceInvitationHistoryJpaRepository historyRows;
    @Autowired private NamespaceJpaRepository namespaces;
    @Autowired private NamespaceMemberJpaRepository members;
    @Autowired private UserAccountJpaRepository users;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockBean private SkillRepository skills;
    @MockBean private ReviewTaskRepository reviewTasks;
    @MockBean private PromotionRequestRepository promotions;

    @Test
    void twoConcurrentAcceptsCreateOnlyOneMemberAndOneConsumption() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String owner = "inv-owner-" + suffix;
        String first = "inv-a-" + suffix;
        String second = "inv-b-" + suffix;
        Long namespaceId = new TransactionTemplate(transactionManager).execute(status -> {
            users.saveAndFlush(new UserAccount(owner, owner, null, null));
            users.saveAndFlush(new UserAccount(first, first, null, null));
            users.saveAndFlush(new UserAccount(second, second, null, null));
            Namespace namespace = namespaces.saveAndFlush(new Namespace("inv-" + suffix, "Invitation test", owner));
            members.saveAndFlush(new NamespaceMember(namespace.getId(), owner, NamespaceRole.OWNER));
            return namespace.getId();
        });
        String token = invitations.create("inv-" + suffix, owner).token();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> attempt(start, token, first));
            var two = pool.submit(() -> attempt(start, token, second));
            start.countDown();
            assertThat(one.get(20, TimeUnit.SECONDS) + two.get(20, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(members.findByNamespaceId(namespaceId, org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream().filter(member -> member.getRole() == NamespaceRole.MEMBER)).hasSize(1);
        assertThat(invitationRows.findByNamespaceId(namespaceId).orElseThrow().getConsumedBy()).isIn(first, second);
        var history = invitations.history("inv-" + suffix, owner, 0, 10).getContent();
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getConsumedBy()).isIn(first, second);
        assertThat(history.getFirst().status(Instant.now())).isEqualTo("USED");
    }

    @Test
    void revokeAndReplaceSerializeWithAcceptance() throws Exception {
        for (boolean replace : new boolean[] {false, true}) {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String owner = "inv-owner-" + suffix;
            String guest = "inv-guest-" + suffix;
            String slug = "inv-" + suffix;
            Long namespaceId = new TransactionTemplate(transactionManager).execute(status -> {
                users.saveAndFlush(new UserAccount(owner, owner, null, null));
                users.saveAndFlush(new UserAccount(guest, guest, null, null));
                Namespace namespace = namespaces.saveAndFlush(new Namespace(slug, "Invitation race", owner));
                members.saveAndFlush(new NamespaceMember(namespace.getId(), owner, NamespaceRole.OWNER));
                return namespace.getId();
            });
            String oldToken = invitations.create(slug, owner).token();
            CountDownLatch start = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                var accept = pool.submit(() -> attempt(start, oldToken, guest));
                var change = pool.submit(() -> {
                    start.await();
                    if (replace) invitations.create(slug, owner, true);
                    else invitations.revoke(slug, owner);
                    return true;
                });
                start.countDown();
                int accepted = accept.get(20, TimeUnit.SECONDS);
                assertThat(change.get(20, TimeUnit.SECONDS)).isTrue();
                assertThat(members.findByNamespaceIdAndUserId(namespaceId, guest).isPresent())
                        .isEqualTo(accepted == 1);
                if (replace) {
                    assertThat(invitationRows.findByNamespaceId(namespaceId)).isPresent();
                    assertThat(invitationRows.findByNamespaceId(namespaceId).orElseThrow().getConsumedAt()).isNull();
                } else {
                    assertThat(invitationRows.findByNamespaceId(namespaceId)).isEmpty();
                }
                assertThat(attempt(new CountDownLatch(0), oldToken, guest)).isZero();
            }
        }
    }

    @Test
    void directAddAndAcceptProduceOneMembershipWithoutConsumingUnusedLink() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String owner = "inv-owner-" + suffix;
        String guest = "inv-guest-" + suffix;
        String slug = "inv-" + suffix;
        Long namespaceId = createTeam(slug, owner, guest);
        String token = invitations.create(slug, owner).token();
        CountDownLatch start = new CountDownLatch(1);
        boolean acceptedAsExistingMember;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var accept = pool.submit(() -> {
                start.await();
                return invitations.accept(token, guest).outcome() == NamespaceInvitationService.AcceptOutcome.ALREADY_MEMBER;
            });
            var direct = pool.submit(() -> {
                start.await();
                try {
                    memberService.addMember(namespaceId, guest, NamespaceRole.MEMBER, owner);
                    return true;
                } catch (DomainBadRequestException alreadyMember) {
                    assertThat(alreadyMember).hasMessageContaining("error.namespace.member.alreadyExists");
                    return false;
                }
            });
            start.countDown();
            acceptedAsExistingMember = accept.get(20, TimeUnit.SECONDS);
            boolean directAdded = direct.get(20, TimeUnit.SECONDS);
            assertThat(acceptedAsExistingMember).isEqualTo(directAdded);
        }
        assertThat(members.findByNamespaceId(namespaceId, org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream().filter(member -> member.getUserId().equals(guest))).hasSize(1);
        assertThat(invitationRows.findByNamespaceId(namespaceId).orElseThrow().getConsumedAt() != null)
                .isEqualTo(!acceptedAsExistingMember);
    }

    @Test
    void freezeAndArchiveSerializeWithAcceptance() throws Exception {
        for (boolean archive : new boolean[] {false, true}) {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String owner = "inv-owner-" + suffix;
            String guest = "inv-guest-" + suffix;
            String slug = "inv-" + suffix;
            Long namespaceId = createTeam(slug, owner, guest);
            String token = invitations.create(slug, owner).token();
            CountDownLatch start = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                var accept = pool.submit(() -> attempt(start, token, guest));
                var lifecycle = pool.submit(() -> {
                    start.await();
                    if (archive) governance.archiveNamespace(slug, owner, "test", null, null, null);
                    else governance.freezeNamespace(slug, owner, "test", null, null, null);
                    return true;
                });
                start.countDown();
                int accepted = accept.get(20, TimeUnit.SECONDS);
                assertThat(lifecycle.get(20, TimeUnit.SECONDS)).isTrue();
                assertThat(members.findByNamespaceIdAndUserId(namespaceId, guest).isPresent())
                        .isEqualTo(accepted == 1);
                assertThat(invitationRows.findByNamespaceId(namespaceId).orElseThrow().getConsumedAt() != null)
                        .isEqualTo(accepted == 1);
            }
        }
    }

    @Test
    void deletingNamespaceAndAcceptingInvitationLeaveNoOrphan() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String owner = "inv-owner-" + suffix;
        String guest = "inv-guest-" + suffix;
        String slug = "inv-" + suffix;
        Long namespaceId = createTeam(slug, owner, guest);
        String token = invitations.create(slug, owner).token();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var accept = pool.submit(() -> attempt(start, token, guest));
            var deletion = pool.submit(() -> {
                start.await();
                namespaceService.deleteNamespace(namespaceId, owner);
                return true;
            });
            start.countDown();
            accept.get(20, TimeUnit.SECONDS);
            assertThat(deletion.get(20, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(namespaces.findBySlug(slug)).isEmpty();
        assertThat(invitationRows.findByNamespaceId(namespaceId)).isEmpty();
        assertThat(historyRows.findByNamespaceId(namespaceId, org.springframework.data.domain.Pageable.unpaged())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_type='NAMESPACE' AND target_id=? AND action='NAMESPACE_INVITE_CREATED'", Long.class, namespaceId)).isEqualTo(1);

        assertThat(members.findByNamespaceIdAndUserId(namespaceId, guest)).isEmpty();
    }

    @Test
    void overlappingNamespaceAccountMergeAndAcceptKeepOnePrimaryMembership() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String owner = "inv-owner-" + suffix;
        String primary = "inv-a-" + suffix;
        String secondary = "inv-z-" + suffix;
        String inviteSlug = "inv-b-" + suffix;
        Long[] ids = new TransactionTemplate(transactionManager).execute(status -> {
            users.saveAndFlush(new UserAccount(owner, owner, null, null));
            users.saveAndFlush(new UserAccount(primary, primary, null, null));
            users.saveAndFlush(new UserAccount(secondary, secondary, null, null));
            Namespace shared = namespaces.saveAndFlush(new Namespace("inv-a-" + suffix, "Shared", owner));
            Namespace invited = namespaces.saveAndFlush(new Namespace(inviteSlug, "Invited", owner));
            for (Namespace ns : new Namespace[] {shared, invited}) {
                members.saveAndFlush(new NamespaceMember(ns.getId(), owner, NamespaceRole.OWNER));
                members.saveAndFlush(new NamespaceMember(ns.getId(), secondary, NamespaceRole.MEMBER));
            }
            members.saveAndFlush(new NamespaceMember(shared.getId(), primary, NamespaceRole.MEMBER));
            AccountMergeRequest request = new AccountMergeRequest(primary, secondary, null,
                    Instant.now().plusSeconds(3600));
            request.setStatus(AccountMergeRequest.STATUS_VERIFIED);
            return new Long[] {shared.getId(), invited.getId(), mergeRequests.saveAndFlush(request).getId()};
        });
        String token = invitations.create(inviteSlug, owner).token();
        CountDownLatch start = new CountDownLatch(1);
        boolean alreadyMember;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var accept = pool.submit(() -> {
                start.await();
                return invitations.accept(token, primary).outcome() == NamespaceInvitationService.AcceptOutcome.ALREADY_MEMBER;
            });
            var merge = pool.submit(() -> {
                start.await();
                accountMerge.confirm(primary, ids[2]);
                return true;
            });
            start.countDown();
            alreadyMember = accept.get(20, TimeUnit.SECONDS);
            assertThat(merge.get(20, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(users.findAllById(java.util.List.of(secondary)).iterator().next().getStatus()).isEqualTo(UserStatus.MERGED);
        assertThat(members.findByNamespaceIdAndUserId(ids[0], secondary)).isEmpty();
        assertThat(members.findByNamespaceIdAndUserId(ids[1], secondary)).isEmpty();
        assertThat(members.findByNamespaceIdAndUserId(ids[1], primary)).isPresent();
        assertThat(members.findByNamespaceId(ids[1], org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream().filter(member -> member.getUserId().equals(primary))).hasSize(1);
        assertThat(invitationRows.findByNamespaceId(ids[1]).orElseThrow().getConsumedAt() != null)
                .isEqualTo(!alreadyMember);
    }

    @Test
    void migrationBackfillsOnlyProvableCurrentRowsAndNeverTokensOrInventedNames() throws Exception {
        String schema = "invite_legacy_" + UUID.randomUUID().toString().replace("-", "");
        var flyway = org.flywaydb.core.Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("73").load();
        flyway.migrate();
        try (var connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            statement.execute("INSERT INTO user_account(id,display_name,status) VALUES ('legacy-owner','Owner','ACTIVE')");
            statement.execute("INSERT INTO namespace(slug,display_name,type,status,created_by) VALUES ('legacy-one','Legacy one','TEAM','ACTIVE','legacy-owner'),('legacy-two','Legacy two','TEAM','ACTIVE','legacy-owner')");
            statement.execute("INSERT INTO namespace_invitation(namespace_id,token_hash,issued_by,issued_at,expires_at,consumed_by,consumed_at) "
                    + "SELECT id, CASE slug WHEN 'legacy-one' THEN repeat('a',64) ELSE repeat('b',64) END,'legacy-owner',now()-interval '1 hour',now()+interval '23 hours',"
                    + "CASE slug WHEN 'legacy-two' THEN 'legacy-guest' END,CASE slug WHEN 'legacy-two' THEN now() END FROM namespace WHERE slug IN ('legacy-one','legacy-two')");
            org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas(schema).defaultSchema(schema).load().migrate();
            try (var rows = statement.executeQuery("SELECT count(*), count(*) FILTER (WHERE legacy), count(*) FILTER (WHERE consumed_by='legacy-guest'), count(issued_by_name) FROM namespace_invitation_history")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(2);
                assertThat(rows.getInt(2)).isEqualTo(2);
                assertThat(rows.getInt(3)).isEqualTo(1);
                assertThat(rows.getInt(4)).isZero();
            }
            try (var rows = statement.executeQuery("SELECT count(*) FROM namespace_invitation i JOIN namespace_invitation_history h ON h.id=i.history_id AND h.namespace_id=i.namespace_id")) {
                rows.next(); assertThat(rows.getInt(1)).isEqualTo(2);
            }
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void historyKeepsUsedAndRemovedMemberFactsAndIndependentReplacementRevocation() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String owner = "history-owner-" + suffix;
        String guest = "history-guest-" + suffix;
        String slug = "history-" + suffix;
        Long namespaceId = createTeam(slug, owner, guest);
        String firstToken = invitations.create(slug, owner).token();
        String firstId = invitationRows.findByNamespaceId(namespaceId).orElseThrow().getHistoryId();
        invitations.create(slug, owner, true);
        assertThat(historyRows.findById(firstId).orElseThrow().status(Instant.now())).isEqualTo("REPLACED");
        String secondId = invitationRows.findByNamespaceId(namespaceId).orElseThrow().getHistoryId();
        invitations.revoke(slug, owner);
        assertThat(historyRows.findById(secondId).orElseThrow().status(Instant.now())).isEqualTo("REVOKED");
        String thirdToken = invitations.create(slug, owner).token();
        String thirdId = invitationRows.findByNamespaceId(namespaceId).orElseThrow().getHistoryId();
        invitations.accept(thirdToken, guest);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            members.delete(members.findByNamespaceIdAndUserId(namespaceId, guest).orElseThrow());
            users.findAllById(java.util.List.of(guest)).iterator().next().setDisplayName("Renamed later");
        });
        invitations.create(slug, owner);
        var history = invitations.history(slug, owner, 0, 2);
        assertThat(history.getTotalElements()).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT detail_json::jsonb->>'invitationId' FROM audit_log WHERE target_type='NAMESPACE' AND target_id=? AND action='NAMESPACE_INVITE_ACCEPTED'", String.class, namespaceId)).isEqualTo(thirdId);
        assertThat(history.getTotalPages()).isEqualTo(2);
        var used = historyRows.findById(thirdId).orElseThrow();
        assertThat(used.status(Instant.now())).isEqualTo("USED");
        assertThat(used.getConsumedBy()).isEqualTo(guest);
        assertThat(used.getConsumedByName()).isNotEqualTo("Renamed later");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> invitations.preview(firstToken, guest)))
                .isInstanceOf(DomainNotFoundException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> invitations.history(slug, guest, 0, 10)))
                .isInstanceOf(DomainNotFoundException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> invitations.history(slug, owner, 0, 51)))
                .isInstanceOf(DomainBadRequestException.class);
    }

    @Test
    void accountMergeDoesNotRewriteHistoricalRecipientIdentity() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String owner = "history-owner-" + suffix;
        String secondary = "history-secondary-" + suffix;
        String primary = "history-primary-" + suffix;
        String slug = "history-merge-" + suffix;
        Long namespaceId = createTeam(slug, owner, secondary);
        String token = invitations.create(slug, owner).token();
        invitations.accept(token, secondary);
        Long requestId = new TransactionTemplate(transactionManager).execute(status -> {
            users.saveAndFlush(new UserAccount(primary, primary, null, null));
            AccountMergeRequest request = new AccountMergeRequest(primary, secondary, null, Instant.now().plusSeconds(3600));
            request.setStatus(AccountMergeRequest.STATUS_VERIFIED);
            return mergeRequests.saveAndFlush(request).getId();
        });
        accountMerge.confirm(primary, requestId);
        assertThat(members.findByNamespaceIdAndUserId(namespaceId, primary)).isPresent();
        var row = invitations.history(slug, owner, 0, 10).getContent().getFirst();
        assertThat(row.getConsumedBy()).isEqualTo(secondary);
        assertThat(row.status(Instant.now())).isEqualTo("USED");
    }

    private Long createTeam(String slug, String owner, String guest) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            users.saveAndFlush(new UserAccount(owner, owner, null, null));
            users.saveAndFlush(new UserAccount(guest, guest, null, null));
            Namespace namespace = namespaces.saveAndFlush(new Namespace(slug, "Invitation race", owner));
            members.saveAndFlush(new NamespaceMember(namespace.getId(), owner, NamespaceRole.OWNER));
            return namespace.getId();
        });
    }

    private int attempt(CountDownLatch start, String token, String userId) throws InterruptedException {
        start.await();
        try {
            invitations.accept(token, userId);
            return 1;
        } catch (DomainNotFoundException expected) {
            return 0;
        }
    }
}
