package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchVisibilityScope;
import com.iflytek.skillhub.search.SkillSearchSource;
import com.iflytek.skillhub.search.postgres.PostgresFullTextQueryService;
import com.iflytek.skillhub.support.PostgresTestImage;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
class SkillSearchSourcePostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name())
            .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withMemory(256L * 1024 * 1024));

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
    private PostgresFullTextQueryService query;
    private Namespace team;
    private Skill platform;
    private Skill github;
    private Skill internal;

    @BeforeEach
    void setup() {
        query = new PostgresFullTextQueryService(em);
        em.persist(new UserAccount("source-owner", "Owner", null, null));
        team = new Namespace("source-team", "Source team", "source-owner");
        em.persist(team);
        em.flush();
        platform = fixture("platform", false, SkillVisibility.PUBLIC, false);
        github = fixture("github", true, SkillVisibility.PUBLIC, false);
        fixture("github-two", true, SkillVisibility.PUBLIC, false);
        internal = fixture("internal", true, SkillVisibility.NAMESPACE_ONLY, false);
        fixture("hidden", true, SkillVisibility.PUBLIC, true);
        fixture("private", false, SkillVisibility.PRIVATE, false);
        em.flush();
    }

    @Test
    void sourceFilteringPrecedesPaginationCountAndKeywordRanking() {
        for (String sort : List.of("newest", "downloads", "relevance")) {
            var first = search(SkillSearchSource.GITHUB, sort, 0, SearchVisibilityScope.anonymous());
            var second = search(SkillSearchSource.GITHUB, sort, 1, SearchVisibilityScope.anonymous());
            var beyond = search(SkillSearchSource.GITHUB, sort, 2, SearchVisibilityScope.anonymous());
            assertThat(first.total()).isEqualTo(2);
            assertThat(second.total()).isEqualTo(2);
            assertThat(first.skillIds()).hasSize(1);
            assertThat(second.skillIds()).hasSize(1).doesNotContainAnyElementsOf(first.skillIds());
            assertThat(beyond.skillIds()).isEmpty();
            assertThat(beyond.total()).isEqualTo(2);
            assertThat(search(SkillSearchSource.PLATFORM, sort, 0, SearchVisibilityScope.anonymous()).skillIds())
                    .containsExactly(platform.getId());
            assertThat(search(SkillSearchSource.ALL, sort, 0, SearchVisibilityScope.anonymous()).total()).isEqualTo(3);
        }
    }

    @Test
    void largeOffsetsRemainEmptyWithoutChangingAuthorizedTotals() {
        for (int page : List.of(107_374_183, Integer.MAX_VALUE)) {
            for (String sort : List.of("newest", "downloads", "relevance")) {
                for (SkillSearchSource source : SkillSearchSource.values()) {
                    var result = query.search(new SearchQuery("shared", team.getId(),
                            SearchVisibilityScope.anonymous(), sort, page, 20, List.of(), false, source));
                    assertThat(result.skillIds()).isEmpty();
                    assertThat(result.total()).isEqualTo(source == SkillSearchSource.PLATFORM ? 1
                            : source == SkillSearchSource.GITHUB ? 2 : 3);
                    assertThat(result.page()).isEqualTo(page);
                }
            }
        }
    }

    @Test
    void currentSourceAndPermissionsOverrideStaleSearchDocuments() {
        var member = new SearchVisibilityScope("member", Set.of(team.getId()), Set.of(), false);
        assertThat(search(SkillSearchSource.GITHUB, "newest", 0, member).total()).isEqualTo(3);
        assertThat(search(SkillSearchSource.GITHUB, "newest", 0, SearchVisibilityScope.anonymous()).total()).isEqualTo(2);
        github.setGithubSyncManaged(false);
        internal.setVisibility(SkillVisibility.PRIVATE);
        em.flush();
        assertThat(search(SkillSearchSource.GITHUB, "newest", 0, member).total()).isEqualTo(1);
        assertThat(search(SkillSearchSource.PLATFORM, "newest", 0, member).total()).isEqualTo(2);
    }

    private com.iflytek.skillhub.search.SearchResult search(
            SkillSearchSource source, String sort, int page, SearchVisibilityScope scope) {
        return query.search(new SearchQuery("shared", team.getId(), scope, sort, page, 1, List.of(), false, source));
    }

    private Skill fixture(String slug, boolean synced, SkillVisibility visibility, boolean hidden) {
        Skill skill = new Skill(team.getId(), slug, "source-owner", visibility);
        skill.setDisplayName("Shared skill");
        skill.setSummary("Shared content from distinct sources");
        skill.setGithubSyncManaged(synced);
        skill.setHidden(hidden);
        em.persist(skill);
        em.flush();
        // Deliberately identical titles and stale PUBLIC index visibility: current Skill must authorize.
        em.persist(new SkillSearchDocumentEntity(skill.getId(), team.getId(), team.getSlug(), "source-owner",
                "Shared skill", "Shared content", "shared", "shared skill", null, "PUBLIC", "ACTIVE"));
        return skill;
    }
}
