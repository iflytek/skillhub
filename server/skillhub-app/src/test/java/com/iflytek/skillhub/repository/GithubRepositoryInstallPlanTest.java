package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillFingerprint;
import com.iflytek.skillhub.support.PostgresTestImage;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class GithubRepositoryInstallPlanTest {
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

    @Autowired private JdbcTemplate jdbc;

    @Test
    void resolvesCompletePublishedMembershipAndDoesNotAdvanceToPendingVersions() {
        long namespace = namespace();
        long source = source(namespace);
        for (int i = 0; i < 130; i++) member(namespace, source, "member-" + i, "PUBLIC");
        long pendingSkill = jdbc.queryForObject("SELECT id FROM skill WHERE slug = 'member-0'", Long.class);
        jdbc.update("INSERT INTO skill_version(skill_id, version, status) VALUES (?, '2.0.0', 'PENDING_REVIEW')", pendingSkill);
        // The binding can already point at new upstream content while the published pointer stays usable.
        long unapproved = member(namespace, source, "first-pending", "PUBLIC");
        jdbc.update("UPDATE skill_version SET status = 'PENDING_REVIEW', download_ready = false WHERE skill_id = ?", unapproved);
        var plan = repository().installPlan(981001, Set.of()).orElseThrow();
        assertThat(plan.members()).hasSize(130);
        assertThat(plan.members()).extracting(item -> item.skillId()).doesNotHaveDuplicates();
        assertThat(plan.members()).allSatisfy(item -> {
            assertThat(item.version()).isEqualTo("1.0.0");
            assertThat(item.fingerprint()).isEqualTo(SkillFingerprint.compute(
                    List.of(new SkillFingerprint.FileHash("SKILL.md", "a".repeat(64)))));
            assertThat(item.downloadUrl()).contains("/versions/1.0.0/download");
        });
        long pinnedVersion = plan.members().getFirst().versionId();
        jdbc.update("UPDATE skill_version SET yanked_at = now() WHERE id = ?", pinnedVersion);
        assertThat(repository().installPlan(981001, Set.of()).orElseThrow().members()).hasSize(129);
        assertThat(plan.members()).hasSize(130); // Earlier response remains an immutable exact-version plan.
    }

    @Test
    void sourceAndMemberVisibilityBothApplyAndHiddenOrArchivedDataNeverLeaks() {
        long namespace = namespace();
        long source = source(namespace);
        long publicSkill = member(namespace, source, "public-member", "PUBLIC");
        member(namespace, source, "team-member", "NAMESPACE_ONLY");
        long hidden = member(namespace, source, "hidden-member", "PUBLIC");
        jdbc.update("UPDATE skill SET hidden = true WHERE id = ?", hidden);
        assertThat(repository().installPlan(981001, Set.of()).orElseThrow().members())
                .extracting(item -> item.skillId()).containsExactly(publicSkill);
        assertThat(repository().installPlan(981001, Set.of(namespace)).orElseThrow().members()).hasSize(2);
        jdbc.update("UPDATE github_source_binding SET visibility = 'NAMESPACE_ONLY' WHERE id = ?", source);
        assertThat(repository().installPlan(981001, Set.of())).isEmpty();
        assertThat(repository().installPlan(981001, Set.of(namespace))).isPresent();
        jdbc.update("UPDATE namespace SET status = 'ARCHIVED' WHERE id = ?", namespace);
        assertThat(repository().installPlan(981001, Set.of(namespace))).isEmpty();
    }

    @Test
    void oversizedPlansFailInsteadOfInstallingTheFirstThousand() {
        long namespace = namespace();
        long source = source(namespace);
        for (int i = 0; i < 1001; i++) member(namespace, source, "large-" + i, "PUBLIC");
        assertThatThrownBy(() -> repository().installPlan(981001, Set.of()))
                .isInstanceOf(DomainBadRequestException.class);
    }

    private long namespace() {
        return jdbc.queryForObject("SELECT id FROM namespace WHERE slug = 'github'", Long.class);
    }

    private long source(long namespace) {
        return jdbc.queryForObject("""
                INSERT INTO github_source_binding(namespace_id, repository_id, owner_login, repository_name, visibility)
                VALUES (?, 981001, 'install-test', 'skills', 'PUBLIC') RETURNING id
                """, Long.class, namespace);
    }

    private long member(long namespace, long source, String slug, String visibility) {
        long skill = jdbc.queryForObject("""
                INSERT INTO skill(namespace_id, slug, owner_id, visibility, github_sync_managed)
                VALUES (?, ?, 'github-sync-publisher', ?, true) RETURNING id
                """, Long.class, namespace, slug, visibility);
        long version = jdbc.queryForObject("""
                INSERT INTO skill_version(skill_id, version, status, download_ready)
                VALUES (?, '1.0.0', 'PUBLISHED', true) RETURNING id
                """, Long.class, skill);
        jdbc.update("UPDATE skill SET latest_version_id = ? WHERE id = ?", version, skill);
        jdbc.update("""
                INSERT INTO skill_file(version_id, file_path, file_size, content_type, sha256, storage_key)
                VALUES (?, 'SKILL.md', 1, 'text/markdown', ?, 'install-test')
                """, version, "a".repeat(64));
        jdbc.update("""
                INSERT INTO github_source_skill_binding(source_id, namespace_id, repository_id, skill_path, skill_id)
                VALUES (?, ?, 981001, ?, ?)
                """, source, namespace, "skills/" + slug, skill);
        return skill;
    }

    private GithubRepositoryCatalogQueryRepository repository() {
        var template = new NamedParameterJdbcTemplate(jdbc);
        return new GithubRepositoryCatalogQueryRepository(template, new GithubRepositorySnapshotQueryRepository(template));
    }
}
