package com.iflytek.skillhub.infra.jpa;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** Only complete, validated acquisition may advance a source's full-processing marker. */
@Repository
public class JdbcGithubSyncSourceCheckpointRepository {
    private final JdbcTemplate jdbc;

    public JdbcGithubSyncSourceCheckpointRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Capture the complete accepted path set in the repository-completion transaction. */
    public void captureRepositorySnapshot(UUID taskId, String commitSha, Instant now) {
        jdbc.update("""
                WITH task AS (
                    SELECT task.id, task.source_id, task.fixed_commit_sha, source.ignore_patterns,
                           source.namespace_id, source.repository_id
                    FROM github_sync_task task
                    JOIN github_source_binding source ON source.id = task.source_id
                    JOIN namespace ns ON ns.id = source.namespace_id
                    WHERE task.id = ? AND task.fixed_commit_sha = ?
                      AND task.kind = 'REPOSITORY_SYNC' AND task.mode = 'FULL'
                      AND task.status = 'ACQUISITION_COMPLETE' AND task.details_purged_at IS NULL
                      AND ns.slug = 'github' AND source.status <> 'PAUSED'
                      AND NOT EXISTS (
                          SELECT 1 FROM github_sync_task newer
                          WHERE newer.source_id = task.source_id AND newer.kind = 'REPOSITORY_SYNC'
                            AND newer.mode = 'FULL' AND newer.status = 'ACQUISITION_COMPLETE'
                            AND (newer.created_at, newer.id) > (task.created_at, task.id))
                      AND COALESCE(task.snapshot_json -> 'ignorePatterns', '[]'::jsonb) = source.ignore_patterns
                ), paths AS (
                    SELECT path.skill_path, ingestion.id AS ingestion_id
                    FROM task
                    JOIN github_sync_path_result path
                      ON path.task_id = task.id AND path.commit_sha = task.fixed_commit_sha
                    LEFT JOIN github_sync_package_ingestion ingestion
                      ON ingestion.id = path.ingestion_id AND ingestion.task_id = task.id
                     AND ingestion.namespace_id = task.namespace_id AND ingestion.repository_id = task.repository_id
                     AND ingestion.skill_path = path.skill_path AND ingestion.commit_sha = task.fixed_commit_sha
                    WHERE path.outcome = 'ACCEPTED'
                ), captured AS (
                    INSERT INTO github_repository_snapshot
                        (source_id, acquisition_task_id, commit_sha, ignore_patterns, captured_at)
                    SELECT task.source_id, task.id, task.fixed_commit_sha, task.ignore_patterns, ?
                    FROM task
                    WHERE EXISTS (SELECT 1 FROM paths)
                      AND NOT EXISTS (SELECT 1 FROM paths WHERE ingestion_id IS NULL)
                      AND NOT EXISTS (
                          SELECT 1 FROM github_sync_path_result path
                          WHERE path.task_id = task.id AND path.commit_sha = task.fixed_commit_sha
                            AND path.outcome NOT IN ('ACCEPTED', 'IGNORED'))
                    ON CONFLICT (acquisition_task_id) DO NOTHING
                    RETURNING id
                )
                INSERT INTO github_repository_snapshot_member(snapshot_id, skill_path, ingestion_id)
                SELECT captured.id, paths.skill_path, paths.ingestion_id
                FROM captured CROSS JOIN paths
                """, taskId, commitSha, Timestamp.from(now));
    }

    public void advanceIfComplete(UUID taskId, String commitSha, Instant now) {
        jdbc.update("""
                UPDATE github_source_binding source
                SET last_fully_processed_commit = ?, updated_at = ?
                FROM github_sync_task task
                WHERE task.id = ? AND task.source_id = source.id
                  AND task.kind = 'REPOSITORY_SYNC' AND task.mode = 'FULL'
                  AND task.status = 'ACQUISITION_COMPLETE' AND task.fixed_commit_sha = ?
                  AND source.last_observed_commit = ?
                  AND COALESCE(task.snapshot_json -> 'ignorePatterns', '[]'::jsonb)
                      = source.ignore_patterns
                  AND NOT EXISTS (
                      SELECT 1 FROM github_sync_package_ingestion ingestion
                      WHERE ingestion.task_id = task.id
                        AND ingestion.status NOT IN ('VALIDATED', 'NO_CHANGE',
                                                     'READY_FOR_REVIEW', 'APPROVED',
                                                     'REJECTED', 'SUPERSEDED', 'BLOCKED'))
                  AND NOT EXISTS (
                      SELECT 1 FROM github_sync_path_result path
                      WHERE path.task_id = task.id AND path.commit_sha = ?
                        AND path.outcome IN ('RETRYABLE_FAILURE', 'TERMINAL_FAILURE'))
                """, commitSha, Timestamp.from(now), taskId, commitSha, commitSha, commitSha);
    }
}
