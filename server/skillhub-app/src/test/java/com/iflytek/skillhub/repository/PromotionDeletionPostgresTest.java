package com.iflytek.skillhub.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.PromotionRequestKind;
import com.iflytek.skillhub.domain.review.PromotionRevocationService;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillHardDeleteService;
import com.iflytek.skillhub.domain.skill.service.SkillStorageDeletionCompensationService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.infra.jpa.NamespaceJpaRepository;
import com.iflytek.skillhub.infra.jpa.PromotionRequestJpaRepository;
import com.iflytek.skillhub.infra.jpa.PromotionRevocationHistoryJpaRepository;
import com.iflytek.skillhub.infra.jpa.SkillJpaRepository;
import com.iflytek.skillhub.infra.jpa.SkillVersionJpaRepository;
import com.iflytek.skillhub.infra.jpa.UserAccountJpaRepository;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the deletion boundary with real foreign keys, commits and retained audit history. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Import({com.iflytek.skillhub.domain.skill.SkillOwnerAccessPolicy.class, PromotionRevocationService.class, PromotionRevocationHistoryJpaRepository.class,
        SkillHardDeleteService.class, AuditLogService.class, PromotionDeletionPostgresTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PromotionDeletionPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

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
    static class Config {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }

    @Autowired private NamespaceJpaRepository namespaces;
    @Autowired private UserAccountJpaRepository users;
    @Autowired private SkillJpaRepository skills;
    @Autowired private com.iflytek.skillhub.infra.jpa.NamespaceMemberJpaRepository members;
    @Autowired private SkillVersionJpaRepository versions;
    @Autowired private PromotionRequestJpaRepository promotions;
    @Autowired private PromotionRevocationService revocations;
    @Autowired private SkillHardDeleteService deletion;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private ObjectStorageService storage;
    @MockBean private SkillStorageDeletionCompensationService compensation;
    @MockBean private SecurityScanService scanning;

    @Test
    void rejectedUpdateDoesNotRemoveThePublishedPromotion() {
        Graph graph = fixture(ReviewTaskStatus.REJECTED);
        assertDeletionBlocked(graph.source(), graph.teamSlug(), graph.owner());
        assertDeletionBlocked(graph.target(), "global", graph.owner());
        assertThat(promotions.findActiveInitialBySourceSkillId(graph.source().getId())).isPresent();
        assertThat(skills.existsById(graph.target().getId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'DELETE_SKILL_HARD' AND target_id IN (?, ?)",
                Integer.class, graph.source().getId(), graph.target().getId())).isZero();
    }

    @Test
    void onlyApprovedRevocationRemovesGlobalAndAllowsSourceDeletionWithAuditRetained() {
        Graph graph = fixture(ReviewTaskStatus.PENDING);
        var request = revocations.submit(graph.source().getId(), graph.owner(), Map.of(), Set.of(), "Remove global listing");
        assertDeletionBlocked(graph.source(), graph.teamSlug(), graph.owner());
        assertDeletionBlocked(graph.target(), "global", graph.owner());
        revocations.approve(request.getId(), graph.reviewer(), Set.of("SUPER_ADMIN"), "Approved", null, null);
        assertThat(skills.existsById(graph.target().getId())).isFalse();
        assertThat(skills.existsById(graph.source().getId())).isTrue();
        assertThat(promotions.findActiveInitialBySourceSkillId(graph.source().getId())).isEmpty();
        assertThat(jdbc.queryForList("SELECT target_skill_id FROM promotion_request WHERE source_skill_id = ?",
                Long.class, graph.source().getId())).containsOnlyNulls();
        assertThat(jdbc.queryForObject("SELECT status FROM promotion_request WHERE id = ?",
                String.class, graph.updateId())).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT target_skill_id_snapshot FROM promotion_request WHERE id = ?",
                Long.class, graph.initialId())).isEqualTo(graph.target().getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM skill_version WHERE skill_id = ?",
                Integer.class, graph.source().getId())).isEqualTo(2);
        deletion.hardDeleteSkill(graph.source(), graph.teamSlug(), graph.owner(), null, null);
        assertThat(skills.existsById(graph.source().getId())).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM promotion_revocation_request WHERE id = ?",
                String.class, request.getId())).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'REVOKE_PROMOTION_TARGET' AND target_id = ?",
                Integer.class, graph.target().getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'DELETE_SKILL_HARD' AND target_id = ?",
                Integer.class, graph.source().getId())).isEqualTo(1);
    }

    private void assertDeletionBlocked(Skill skill, String namespace, String actor) {
        assertThatThrownBy(() -> deletion.hardDeleteSkill(skill, namespace, actor, null, null))
                .isInstanceOf(DomainBadRequestException.class).hasMessageContaining("promotion.revocation.required");
    }

    private Graph fixture(ReviewTaskStatus updateStatus) {
        return new TransactionTemplate(transactions).execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String owner = "delete-owner-" + suffix;
            String reviewer = "delete-admin-" + suffix;
            users.saveAndFlush(new UserAccount(owner, owner, null, null));
            users.saveAndFlush(new UserAccount(reviewer, reviewer, null, null));
            Namespace team = namespaces.saveAndFlush(new Namespace("delete-" + suffix, "Deletion team", owner));
            members.saveAndFlush(new com.iflytek.skillhub.domain.namespace.NamespaceMember(
                    team.getId(), owner, com.iflytek.skillhub.domain.namespace.NamespaceRole.OWNER));
            Namespace global = namespaces.findBySlug("global").orElseGet(() -> {
                Namespace created = new Namespace("global", "Global", reviewer);
                created.setType(NamespaceType.GLOBAL);
                return namespaces.saveAndFlush(created);
            });
            Skill source = skills.saveAndFlush(new Skill(team.getId(), "delete-" + suffix, owner, SkillVisibility.PUBLIC));
            SkillVersion first = publishedVersion(source, "1.0.0", owner);
            SkillVersion second = publishedVersion(source, "1.1.0", owner);
            source.setLatestVersionId(second.getId());
            skills.saveAndFlush(source);
            Skill target = new Skill(global.getId(), source.getSlug(), owner, SkillVisibility.PUBLIC);
            target.setSourceSkillId(source.getId());
            target = skills.saveAndFlush(target);
            SkillVersion targetVersion = publishedVersion(target, "1.0.0", owner);
            target.setLatestVersionId(targetVersion.getId());
            skills.saveAndFlush(target);
            PromotionRequest initial = new PromotionRequest(source.getId(), first.getId(), global.getId(), owner);
            initial.setStatus(ReviewTaskStatus.APPROVED);
            initial.setTargetSkillId(target.getId());
            initial.setTargetVersionId(targetVersion.getId());
            initial = promotions.saveAndFlush(initial);
            PromotionRequest update = new PromotionRequest(source.getId(), second.getId(), global.getId(), owner);
            update.setRequestKind(PromotionRequestKind.UPDATE);
            update.setStatus(updateStatus);
            update.setTargetSkillId(target.getId());
            update = promotions.saveAndFlush(update);
            return new Graph(source, target, team.getSlug(), owner, reviewer, initial.getId(), update.getId());
        });
    }

    private SkillVersion publishedVersion(Skill skill, String version, String owner) {
        SkillVersion created = new SkillVersion(skill.getId(), version, owner);
        created.setStatus(SkillVersionStatus.PUBLISHED);
        return versions.saveAndFlush(created);
    }

    private record Graph(Skill source, Skill target, String teamSlug, String owner, String reviewer,
                         Long initialId, Long updateId) { }
}
