package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.social.SkillStar;
import com.iflytek.skillhub.domain.user.UserAccount;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Import(JpaStarredSkillQueryRepository.class)
class StarredSkillAccessPostgresTest {
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

    @Autowired private EntityManager em;
    @Autowired private JpaStarredSkillQueryRepository query;
    private Long namespaceId;
    private final List<Skill> fixtures = new ArrayList<>();

    @BeforeEach
    void setup() {
        em.persist(new UserAccount("owner", "Owner", null, null));
        em.persist(new UserAccount("viewer", "Viewer", null, null));
        Namespace namespace = new Namespace("social-access", "Social access", "owner");
        em.persist(namespace);
        em.flush();
        namespaceId = namespace.getId();
        for (SkillVisibility visibility : SkillVisibility.values()) {
            for (boolean published : List.of(false, true)) {
                for (boolean hidden : List.of(false, true)) {
                    Skill skill = new Skill(namespaceId, visibility.name().toLowerCase() + "-" + published + "-" + hidden,
                            "owner", visibility);
                    skill.setDisplayName("Secret metadata");
                    skill.setSummary("Must not leak through a historical star");
                    skill.setHidden(hidden);
                    em.persist(skill);
                    em.flush();
                    if (published) {
                        SkillVersion version = new SkillVersion(skill.getId(), "1.0.0", "owner");
                        version.setStatus(SkillVersionStatus.PUBLISHED);
                        em.persist(version);
                        em.flush();
                        skill.setLatestVersionId(version.getId());
                    }
                    em.persist(new SkillStar(skill.getId(), "viewer"));
                    em.persist(new SkillStar(skill.getId(), "owner"));
                    fixtures.add(skill);
                }
            }
        }
        em.flush();
    }

    @Test
    void databasePolicyMatchesVisibilityCheckerForEveryLifecycleAndRole() {
        assertPolicy("viewer", Map.of(), Set.of());
        assertPolicy("owner", Map.of(), Set.of());
        for (NamespaceRole role : NamespaceRole.values()) {
            assertPolicy("viewer", Map.of(namespaceId, role), Set.of());
        }
        assertPolicy("viewer", Map.of(), Set.of("SUPER_ADMIN"));
        assertPolicy("viewer", Map.of(), Set.of("SKILL_ADMIN"));
    }

    @Test
    void filteringPrecedesCountAndPaginationWithoutDeletingHistoricalStars() {
        var first = query.findAccessible("viewer", Map.of(), Set.of(), PageRequest.of(0, 1));
        assertThat(first.getTotalElements()).isEqualTo(1);
        assertThat(first.getContent()).extracting(Skill::getVisibility).containsExactly(SkillVisibility.PUBLIC);
        var beyond = query.findAccessible("viewer", Map.of(), Set.of(), PageRequest.of(1, 1));
        assertThat(beyond.getContent()).isEmpty();
        assertThat(beyond.getTotalElements()).isEqualTo(1);
        assertThat(em.createQuery("select count(s) from SkillStar s where s.userId = 'viewer'", Long.class)
                .getSingleResult()).isEqualTo(12);
    }

    @Test
    void membershipRevocationAndVisibilityReductionRemoveMetadataImmediately() {
        assertThat(query.findAccessible("viewer", Map.of(namespaceId, NamespaceRole.MEMBER), Set.of(), PageRequest.of(0, 50))
                .getTotalElements()).isEqualTo(2);
        assertThat(query.findAccessible("viewer", Map.of(), Set.of(), PageRequest.of(0, 50)).getTotalElements()).isEqualTo(1);
        fixtures.stream().filter(skill -> skill.getVisibility() == SkillVisibility.PUBLIC)
                .forEach(skill -> skill.setVisibility(SkillVisibility.PRIVATE));
        em.flush();
        assertThat(query.findAccessible("viewer", Map.of(), Set.of(), PageRequest.of(0, 50)).getTotalElements()).isZero();
    }

    @Test
    void removedOwnerRetainsInternalStarsWithoutExposingThemToOtherNonMembers() {
        Skill internal = fixtures.stream()
                .filter(skill -> skill.getVisibility() == SkillVisibility.NAMESPACE_ONLY)
                .filter(skill -> skill.getLatestVersionId() != null && !skill.isHidden())
                .findFirst().orElseThrow();
        var owner = query.findAccessible("owner", Map.of(), Set.of(), PageRequest.of(0, 50));
        var other = query.findAccessible("viewer", Map.of(), Set.of(), PageRequest.of(0, 50));
        assertThat(owner.getContent()).extracting(Skill::getId).contains(internal.getId());
        assertThat(owner.getTotalElements()).isEqualTo(12);
        assertThat(other.getContent()).extracting(Skill::getId).doesNotContain(internal.getId());
        assertThat(other.getTotalElements()).isEqualTo(1);
    }

    private void assertPolicy(String user, Map<Long, NamespaceRole> roles, Set<String> platformRoles) {
        var checker = new VisibilityChecker();
        List<Long> expected = fixtures.stream().filter(skill -> checker.canAccess(skill, user, roles, platformRoles))
                .map(Skill::getId).toList();
        var result = query.findAccessible(user, roles, platformRoles, PageRequest.of(0, 50));
        assertThat(result.getTotalElements()).isEqualTo(expected.size());
        assertThat(result.getContent()).extracting(Skill::getId).containsExactlyInAnyOrderElementsOf(expected);
        for (int i = 0; i < expected.size(); i++) {
            var page = query.findAccessible(user, roles, platformRoles, PageRequest.of(i, 1));
            assertThat(page.getTotalElements()).isEqualTo(expected.size());
            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().getFirst().getId()).isEqualTo(result.getContent().get(i).getId());
        }
    }
}
