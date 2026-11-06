package com.iflytek.skillhub.db;

import com.iflytek.skillhub.support.PostgresTestImage;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class GithubReviewOriginMigrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PostgresTestImage.name());

    @Test
    void existingReviewsRemainUserReviewsAfterGithubOriginMigration() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        migrate(dataSource, "82");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO user_account (id, display_name) VALUES ('review-author', 'Review Author')");
        long namespaceId = jdbc.queryForObject("""
                INSERT INTO namespace (slug, display_name, type, created_by)
                VALUES ('review-origin-team', 'Review Origin Team', 'TEAM', 'review-author')
                RETURNING id
                """, Long.class);
        long skillId = jdbc.queryForObject("""
                INSERT INTO skill (namespace_id, slug, owner_id)
                VALUES (?, 'existing-skill', 'review-author') RETURNING id
                """, Long.class, namespaceId);
        long versionId = jdbc.queryForObject("""
                INSERT INTO skill_version (skill_id, version, created_by)
                VALUES (?, '1.0.0', 'review-author') RETURNING id
                """, Long.class, skillId);
        long reviewId = jdbc.queryForObject("""
                INSERT INTO review_task
                    (skill_version_id, skill_id, skill_version, namespace_id,
                     subject_type, subject_id, subject_version_id, subject_version, submitted_by)
                VALUES (?, ?, '1.0.0', ?, 'SKILL_VERSION', ?, ?, '1.0.0', 'review-author')
                RETURNING id
                """, Long.class, versionId, skillId, namespaceId, skillId, versionId);

        migrate(dataSource, "83");

        assertThat(jdbc.queryForObject("""
                SELECT origin_type FROM review_task WHERE id = ?
                """, String.class, reviewId)).isEqualTo("USER");
        assertThat(jdbc.queryForObject("""
                SELECT github_sync_ingestion_id IS NULL FROM review_task WHERE id = ?
                """, Boolean.class, reviewId)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM review_task
                WHERE namespace_id = ? AND status = 'PENDING' AND origin_type = 'USER'
                """, Integer.class, namespaceId)).isEqualTo(1);
    }

    private void migrate(DriverManagerDataSource dataSource, String target) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(target)).load().migrate();
    }
}
