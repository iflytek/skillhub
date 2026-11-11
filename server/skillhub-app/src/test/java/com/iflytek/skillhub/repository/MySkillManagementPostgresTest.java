package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.support.PostgresTestImage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.review.*;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.domain.user.UserAccount;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class MySkillManagementPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
    @Autowired EntityManager entities;
    JpaMySkillQueryRepository query;
    Namespace team;
    Namespace global;
    @BeforeEach void setup() {
        query = new JpaMySkillQueryRepository(mock(NamespaceRepository.class), mock(PromotionRequestRepository.class),
                mock(SkillLifecycleProjectionService.class));
        ReflectionTestUtils.setField(query, "entityManager", entities);
        entities.persist(new UserAccount("owner", "Owner", "owner@example.test", null));
        entities.persist(new UserAccount("other", "Other", "other@example.test", null));
        team = new Namespace("management-team", "Team", "owner");
        entities.persist(team);
        global = new Namespace("management-global", "Global", "owner");
        global.setType(NamespaceType.GLOBAL);
        entities.persist(global);
    }
    Skill skill(Namespace namespace, String slug) {
        Skill skill = new Skill(namespace.getId(), slug, "owner", SkillVisibility.PRIVATE);
        skill.setDisplayName(slug);
        entities.persist(skill);
        return skill;
    }
    @Test void pendingProjectionIsPageBoundedSingleQueryAndClearsForAllFinishedStatuses() {
        Skill first = skill(team, "first");
        Skill second = skill(team, "second");
        Skill outside = skill(team, "outside-page");
        ReviewTask task = ReviewTask.forVisibility(first, SkillVisibility.PUBLIC, "owner");
        entities.persist(task);
        entities.persist(ReviewTask.forVisibility(outside, SkillVisibility.PUBLIC, "owner"));
        entities.flush();
        var statistics = entities.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        long before = statistics.getPrepareStatementCount();
        var states = query.getManagementStates(List.of(first, second), "owner");
        assertThat(statistics.getPrepareStatementCount() - before).isEqualTo(1);
        assertThat(states.keySet()).containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(states.get(first.getId()).requestedVisibility()).isEqualTo("PUBLIC");
        assertThat(states.get(first.getId()).reviewTaskId()).isEqualTo(task.getId());
        assertThat(states.get(second.getId()).requestedVisibility()).isNull();
        assertThat(query.getManagementStates(List.of(first), "other")).isEmpty();
        for (ReviewTaskStatus status : List.of(ReviewTaskStatus.APPROVED, ReviewTaskStatus.REJECTED)) {
            task.setStatus(status);
            entities.flush();
            assertThat(query.getManagementStates(List.of(first), "owner").get(first.getId()).requestedVisibility()).isNull();
        }
        entities.remove(task);
        entities.flush();
        assertThat(query.getManagementStates(List.of(first), "owner").get(first.getId()).reviewTaskId()).isNull();
    }
    @Test void actualSourceIdentityDrivesLinksAndNeverSameSlugOrForeignOwner() {
        Skill source = skill(team, "original");
        Skill unrelated = skill(team, "same-name");
        Skill copy = skill(global, "same-name");
        copy.setVisibility(SkillVisibility.PUBLIC);
        copy.setSourceSkillId(source.getId());
        entities.flush();
        var states = query.getManagementStates(List.of(source, copy, unrelated), "owner");
        assertThat(states.get(source.getId()).promotionRole()).isEqualTo("TEAM_SOURCE");
        assertThat(states.get(source.getId()).relatedNamespace()).isEqualTo(global.getSlug());
        assertThat(states.get(copy.getId()).promotionRole()).isEqualTo("GLOBAL_COPY");
        assertThat(states.get(copy.getId()).relatedSlug()).isEqualTo(source.getSlug());
        assertThat(states.get(unrelated.getId()).promotionRole()).isNull();
        ReflectionTestUtils.setField(source, "ownerId", "other");
        entities.flush();
        var hidden = query.getManagementStates(List.of(copy), "owner").get(copy.getId());
        assertThat(hidden.promotionRole()).isEqualTo("GLOBAL_COPY");
        assertThat(hidden.relatedNamespace()).isNull();
        assertThat(hidden.relatedSlug()).isNull();
    }
    @Test void pendingFilterPagesAndCountsScopeAndContentOnceAndRespectsOwnership() {
        Skill scope = skill(team, "scope-only");
        Skill content = skill(team, "content-only");
        Skill both = skill(team, "both-pending");
        Skill foreign = skill(team, "foreign-pending");
        ReflectionTestUtils.setField(foreign, "ownerId", "other");
        Skill settled = skill(team, "settled");
        Namespace elsewhere = new Namespace("elsewhere", "Elsewhere", "owner");
        entities.persist(elsewhere);
        Skill outside = skill(elsewhere, "outside-namespace");
        for (Skill skill : List.of(scope, content, both, foreign, settled, outside)) {
            ReflectionTestUtils.setField(skill, "updatedAt", java.time.Instant.parse("2026-10-15T00:00:00Z"));
        }
        var scopeTask = ReviewTask.forVisibility(scope, SkillVisibility.PUBLIC, "owner");
        var bothTask = ReviewTask.forVisibility(both, SkillVisibility.PUBLIC, "owner");
        entities.persist(scopeTask);
        entities.persist(bothTask);
        entities.persist(ReviewTask.forVisibility(foreign, SkillVisibility.PUBLIC, "other"));
        entities.persist(ReviewTask.forVisibility(outside, SkillVisibility.PUBLIC, "owner"));
        var contentWork = new SkillVersion(content.getId(), "1.0.0", "owner");
        contentWork.setStatus(SkillVersionStatus.PENDING_REVIEW);
        entities.persist(contentWork);
        var bothWork = new SkillVersion(both.getId(), "1.0.0", "owner");
        bothWork.setStatus(SkillVersionStatus.PENDING_REVIEW);
        entities.persist(bothWork);
        entities.flush();
        var first = query.findPendingSkills("owner", null, team.getId(), org.springframework.data.domain.PageRequest.of(0, 2));
        var second = query.findPendingSkills("owner", null, team.getId(), org.springframework.data.domain.PageRequest.of(1, 2));
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(second.getTotalElements()).isEqualTo(3);
        assertThat(first.getContent()).extracting(Skill::getId).containsExactly(both.getId(), content.getId());
        assertThat(second.getContent()).extracting(Skill::getId).containsExactly(scope.getId());
        assertThat(query.findPendingSkills("owner", "content", team.getId(), org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements()).isEqualTo(1);
        assertThat(query.findPendingSkills("owner", null, -1L, org.springframework.data.domain.PageRequest.of(0, 2)).getTotalElements()).isZero();
        assertThat(query.findPendingSkills("owner", null, null, org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements()).isEqualTo(4);
        for (ReviewTaskStatus status : List.of(ReviewTaskStatus.APPROVED, ReviewTaskStatus.REJECTED)) {
            scopeTask.setStatus(status);
            bothTask.setStatus(status);
            entities.flush();
            var remaining = query.findPendingSkills("owner", null, team.getId(), org.springframework.data.domain.PageRequest.of(0, 10));
            assertThat(remaining.getTotalElements()).isEqualTo(2);
            assertThat(remaining.getContent()).extracting(Skill::getId).containsExactly(both.getId(), content.getId());
        }
        entities.remove(scopeTask);
        entities.remove(bothTask);
        entities.flush();
        assertThat(query.findPendingSkills("owner", null, team.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
    }
    @Test void pendingContentFilterPreservesOwnerPreviewRecencyRules() {
        Skill skill = skill(team, "version-recency");
        SkillVersion oldPending = new SkillVersion(skill.getId(), "1.0.0", "owner");
        oldPending.setStatus(SkillVersionStatus.PENDING_REVIEW);
        ReflectionTestUtils.setField(oldPending, "createdAt", java.time.Instant.parse("2026-10-01T00:00:00Z"));
        entities.persist(oldPending);
        SkillVersion published = new SkillVersion(skill.getId(), "2.0.0", "owner");
        published.setStatus(SkillVersionStatus.PUBLISHED);
        ReflectionTestUtils.setField(published, "createdAt", java.time.Instant.parse("2026-10-02T00:00:00Z"));
        entities.persist(published);
        skill.setLatestVersionId(published.getId());
        entities.flush();
        assertThat(query.findPendingSkills("owner", null, null, org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements()).isZero();
        SkillVersion pending = new SkillVersion(skill.getId(), "3.0.0", "owner");
        pending.setStatus(SkillVersionStatus.PENDING_REVIEW);
        ReflectionTestUtils.setField(pending, "createdAt", java.time.Instant.parse("2026-10-03T00:00:00Z"));
        entities.persist(pending);
        entities.flush();
        assertThat(query.findPendingSkills("owner", null, null, org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
        SkillVersion newerWork = new SkillVersion(skill.getId(), "4.0.0", "owner");
        newerWork.setStatus(SkillVersionStatus.UPLOADED);
        ReflectionTestUtils.setField(newerWork, "createdAt", java.time.Instant.parse("2026-10-04T00:00:00Z"));
        entities.persist(newerWork);
        entities.flush();
        assertThat(query.findPendingSkills("owner", null, null, org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements()).isZero();
    }
    @Test void normalSummarySerializationDoesNotExposeOwnerManagementState() throws Exception {
        var summary = new com.iflytek.skillhub.dto.SkillSummaryResponse(1L, "foo", "Foo", null,
                "PUBLIC", "ACTIVE", 0L, 0, null, 0, "global", null, false,
                null, null, null, null, null);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertThat(mapper.readTree(mapper.writeValueAsString(summary)).has("managementState")).isFalse();
        var managed = summary.withManagementState(new com.iflytek.skillhub.dto.SkillSummaryResponse.ManagementState(
                "PUBLIC", 42L, null, null, null));
        assertThat(mapper.readTree(mapper.writeValueAsString(managed)).path("managementState")
                .path("requestedVisibility").asText()).isEqualTo("PUBLIC");
    }
    @Test void privateGlobalCopyKeepsSourceRelationshipAndNamespaceOnlyCopyRequiresMembership() {
        Skill source = skill(team, "source-to-global");
        Skill copy = skill(global, "global-copy");
        copy.setVisibility(SkillVisibility.PRIVATE);
        copy.setSourceSkillId(source.getId());
        entities.flush();
        var privateState = query.getManagementStates(List.of(source), "owner").get(source.getId());
        assertThat(privateState.promotionRole()).isEqualTo("TEAM_SOURCE");
        assertThat(privateState.relatedNamespace()).isEqualTo(global.getSlug());
        assertThat(privateState.relatedSlug()).isEqualTo(copy.getSlug());
        copy.setVisibility(SkillVisibility.NAMESPACE_ONLY);
        entities.flush();
        var restricted = query.getManagementStates(List.of(source), "owner").get(source.getId());
        assertThat(restricted.promotionRole()).isEqualTo("TEAM_SOURCE");
        assertThat(restricted.relatedNamespace()).isNull();
        assertThat(restricted.relatedSlug()).isNull();
        entities.persist(new NamespaceMember(global.getId(), "owner", NamespaceRole.MEMBER));
        entities.flush();
        var memberState = query.getManagementStates(List.of(source), "owner").get(source.getId());
        assertThat(memberState.promotionRole()).isEqualTo("TEAM_SOURCE");
        assertThat(memberState.relatedSlug()).isEqualTo(copy.getSlug());
        copy.setHidden(true);
        entities.flush();
        assertThat(query.getManagementStates(List.of(source), "owner").get(source.getId()).relatedSlug()).isNull();
    }
    @Test void namespaceOnlySourceRequiresMembershipAndArchivedSourceNeverLeaksCoordinates() {
        Skill source = skill(team, "scoped-source");
        Skill copy = skill(global, "scoped-copy");
        copy.setVisibility(SkillVisibility.PUBLIC);
        copy.setSourceSkillId(source.getId());
        source.setVisibility(SkillVisibility.NAMESPACE_ONLY);
        entities.flush();
        assertThat(query.getManagementStates(List.of(copy), "owner").get(copy.getId()).relatedSlug()).isNull();
        entities.persist(new NamespaceMember(team.getId(), "owner", NamespaceRole.MEMBER));
        entities.flush();
        assertThat(query.getManagementStates(List.of(copy), "owner").get(copy.getId()).relatedSlug()).isEqualTo(source.getSlug());
        team.setStatus(NamespaceStatus.ARCHIVED);
        entities.flush();
        assertThat(query.getManagementStates(List.of(copy), "owner").get(copy.getId()).relatedNamespace()).isNull();
    }
}
