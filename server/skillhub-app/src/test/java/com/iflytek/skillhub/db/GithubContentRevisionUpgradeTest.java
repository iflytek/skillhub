package com.iflytek.skillhub.db;

import com.iflytek.skillhub.support.PostgresTestImage;
import java.util.UUID;
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
class GithubContentRevisionUpgradeTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());

    @Test void v106HistoryAABABackfillsConsecutiveContentRevisionsWithoutChangingTechnicalVersions() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        migrate(source, "106");
        var jdbc = new JdbcTemplate(source);
        long namespace = jdbc.queryForObject("SELECT id FROM namespace WHERE slug='github'", Long.class);
        long binding = jdbc.queryForObject("INSERT INTO github_source_binding(namespace_id,repository_id,owner_login,repository_name,visibility) VALUES (?,9701,'fixture','history','PUBLIC') RETURNING id", Long.class, namespace);
        long skill = jdbc.queryForObject("INSERT INTO skill(namespace_id,slug,owner_id,github_sync_managed) VALUES (?,'revision-history','github-sync-publisher',TRUE) RETURNING id", Long.class, namespace);
        long ordinary = jdbc.queryForObject("INSERT INTO skill_version(skill_id,version,status) VALUES (?,'1.0.0','PUBLISHED') RETURNING id", Long.class, skill);
        UUID latest = null;
        for (int i=0;i<4;i++) {
            String digest = (i==2 ? "b" : "a").repeat(64);
            UUID run=UUID.randomUUID(), task=UUID.randomUUID(), admission=UUID.randomUUID(), upload=UUID.randomUUID();
            latest=UUID.randomUUID();
            jdbc.update("INSERT INTO github_sync_run(id,namespace_id,kind) VALUES (?,?,'REPOSITORY_SYNC')",run,namespace);
            jdbc.update("INSERT INTO github_sync_task(id,run_id,namespace_id,source_id,kind,mode,dedupe_key,snapshot_json) VALUES (?,?,?,?,'REPOSITORY_SYNC','FULL',?,'{}')",task,run,namespace,binding,task.toString());
            jdbc.update("INSERT INTO github_sync_package_admission(id,task_id,namespace_id,repository_id,commit_sha,skill_path,lease_generation) VALUES (?,?,?,9701,?,'skills/demo',1)",admission,task,namespace,"a".repeat(40));
            jdbc.update("INSERT INTO github_sync_staged_upload(id,admission_id,object_key,mode,ticket_hash,size_bytes,sha256,status,expires_at) VALUES (?,?,?,'LOCAL_STREAM',?,1,?,'ACCEPTED',now()+INTERVAL '1 hour')",upload,admission,upload.toString(),digest,digest);
            jdbc.update("INSERT INTO github_sync_package_ingestion(id,task_id,admission_id,upload_id,namespace_id,repository_id,commit_sha,skill_path,object_key,size_bytes,sha256,content_sha256,status) VALUES (?,?,?,?,?,9701,?,'skills/demo',?,1,?,?,'SUPERSEDED')",latest,task,admission,upload,namespace,"a".repeat(40),upload.toString(),digest,digest);
            jdbc.update("INSERT INTO skill_version(skill_id,version,status,github_sync_ingestion_id) VALUES (?,?,'REJECTED',?)",skill,"0.0.0-gfixture-"+i,latest);
        }
        jdbc.update("INSERT INTO github_source_skill_binding(source_id,namespace_id,repository_id,skill_path,skill_id,latest_content_sha256,latest_ingestion_id) VALUES (?,?,9701,'skills/demo',?,?,?)",binding,namespace,skill,"a".repeat(64),latest);
        var versions=jdbc.queryForList("SELECT id,version FROM skill_version WHERE skill_id=? ORDER BY id",skill);
        migrate(source,"107");
        assertThat(jdbc.queryForList("SELECT github_sync_revision FROM skill_version WHERE skill_id=? AND github_sync_ingestion_id IS NOT NULL ORDER BY id",Integer.class,skill)).containsExactly(1,1,2,3);
        assertThat(jdbc.queryForObject("SELECT latest_revision FROM github_source_skill_binding WHERE skill_id=?",Integer.class,skill)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT github_sync_revision FROM skill_version WHERE id=?",Integer.class,ordinary)).isNull();
        assertThat(jdbc.queryForList("SELECT id,version FROM skill_version WHERE skill_id=? ORDER BY id",skill)).isEqualTo(versions);
        assertThat(jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version='107'",Boolean.class)).isTrue();
    }

    private void migrate(DriverManagerDataSource source,String version) {
        Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version)).load().migrate();
    }
}
