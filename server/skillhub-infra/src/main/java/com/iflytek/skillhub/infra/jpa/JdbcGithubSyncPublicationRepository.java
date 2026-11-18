package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.githubsync.GithubSyncPublicationRepository;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcGithubSyncPublicationRepository implements GithubSyncPublicationRepository {
    private final JdbcTemplate jdbc;

    public JdbcGithubSyncPublicationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Optional<ClaimedPublication> claim(UUID token, Instant now, Instant expiresAt) {
        UUID id = jdbc.query("""
                WITH picked AS (
                    SELECT ingestion.id
                    FROM github_sync_package_ingestion ingestion
                    WHERE ingestion.status = 'VALIDATED'
                      AND (ingestion.publication_retry_at IS NULL OR ingestion.publication_retry_at <= ?)
                      AND (ingestion.publication_token IS NULL OR ingestion.publication_expires_at <= ?)
                    ORDER BY ingestion.validated_at, ingestion.id
                    LIMIT 1 FOR UPDATE OF ingestion SKIP LOCKED
                )
                UPDATE github_sync_package_ingestion ingestion
                SET publication_token = ?, publication_expires_at = ?,
                    publication_attempts = ingestion.publication_attempts + 1,
                    publication_retry_at = NULL, updated_at = ?
                FROM picked WHERE ingestion.id = picked.id
                RETURNING ingestion.id
                """, statement -> {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setObject(3, token);
            statement.setTimestamp(4, Timestamp.from(expiresAt));
            statement.setTimestamp(5, Timestamp.from(now));
        }, result -> result.next() ? result.getObject("id", UUID.class) : null);
        if (id == null) {
            return Optional.empty();
        }
        return jdbc.query("""
                SELECT ingestion.id, ingestion.namespace_id, namespace.slug AS namespace_slug,
                       ingestion.repository_id, source.id AS source_id,
                       source.status AS source_status, ingestion.skill_path,
                       COALESCE(source.visibility,
                           CASE WHEN namespace.type = 'CATALOG' THEN 'PUBLIC'
                                ELSE 'NAMESPACE_ONLY' END) AS visibility,
                       ingestion.object_key, ingestion.size_bytes,
                       ingestion.sha256 AS archive_sha256, ingestion.content_sha256,
                       binding.skill_id AS expected_skill_id,
                       ingestion.publication_attempts
                FROM github_sync_package_ingestion ingestion
                JOIN namespace ON namespace.id = ingestion.namespace_id
                LEFT JOIN github_source_binding source
                  ON source.namespace_id = ingestion.namespace_id
                 AND source.repository_id = ingestion.repository_id
                LEFT JOIN github_source_skill_binding binding
                  ON binding.namespace_id = ingestion.namespace_id
                 AND binding.repository_id = ingestion.repository_id
                 AND binding.skill_path = ingestion.skill_path
                WHERE ingestion.id = ?
                """, result -> result.next() ? Optional.of(new ClaimedPublication(
                result.getObject("id", UUID.class), token,
                result.getLong("namespace_id"), result.getString("namespace_slug"),
                result.getLong("repository_id"), result.getObject("source_id", Long.class),
                result.getString("skill_path"), result.getString("source_status"),
                result.getString("visibility"),
                result.getString("object_key"), result.getLong("size_bytes"),
                result.getString("archive_sha256"), result.getString("content_sha256"),
                result.getObject("expected_skill_id", Long.class),
                result.getInt("publication_attempts"))) : Optional.empty(), id);
    }

    @Override
    @Transactional
    public boolean requestRejectedRetry(long namespaceId, long sourceId, String skillPath, Instant now) {
        int updated = jdbc.update("""
                UPDATE github_source_skill_binding binding
                SET retry_requested_at = ?,
                    retry_scheduled_at = NULL,
                    updated_at = ?
                FROM github_sync_package_ingestion ingestion
                WHERE binding.namespace_id = ? AND binding.source_id = ?
                  AND binding.skill_path = ?
                  AND ingestion.id = binding.latest_ingestion_id
                  AND ingestion.status = 'REJECTED'
                  AND (binding.retry_requested_at IS NULL
                       OR (binding.retry_scheduled_at IS NOT NULL
                           AND NOT EXISTS (SELECT 1 FROM github_sync_task task
                               WHERE task.source_id = binding.source_id
                                 AND task.mode = 'FULL'
                                 AND task.status IN ('QUEUED', 'LEASED', 'RETRY_WAIT')
                                 AND task.updated_at >= binding.retry_scheduled_at)
                           AND NOT EXISTS (SELECT 1 FROM github_sync_package_ingestion pending
                               WHERE pending.namespace_id = binding.namespace_id
                                 AND pending.repository_id = binding.repository_id
                                 AND pending.skill_path = binding.skill_path
                                 AND pending.created_at >= binding.retry_requested_at
                                 AND pending.status IN ('RECEIVED', 'VALIDATING', 'RETRY_WAIT',
                                                        'VALIDATED', 'READY_FOR_REVIEW')))
                      )
                """, Timestamp.from(now), Timestamp.from(now), namespaceId, sourceId, skillPath);
        if (updated == 1) {
            int queued = jdbc.update("""
                    UPDATE github_sync_task
                    SET mode = 'FULL', updated_at = ?
                    WHERE namespace_id = ? AND source_id = ?
                      AND kind = 'REPOSITORY_SYNC' AND status = 'QUEUED'
                    """, Timestamp.from(now), namespaceId, sourceId);
            if (queued > 0) {
                jdbc.update("""
                        UPDATE github_source_skill_binding
                        SET retry_scheduled_at = ?, updated_at = ?
                        WHERE namespace_id = ? AND source_id = ? AND skill_path = ?
                        """, Timestamp.from(now), Timestamp.from(now),
                        namespaceId, sourceId, skillPath);
            }
        }
        return updated == 1;
    }

    @Override
    @Transactional
    public boolean lockAndVerify(ClaimedPublication claim, Instant now) {
        // Serializes only one Skill path. A late worker must not restore an older target.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?), hashtext(?))",
                result -> { result.next(); return null; },
                claim.namespaceId() + ":" + claim.repositoryId(), claim.skillPath());
        return Boolean.TRUE.equals(jdbc.query("""
                SELECT publication_token = ? AND publication_expires_at > ? AS valid
                FROM github_sync_package_ingestion
                WHERE id = ? AND status = 'VALIDATED'
                FOR UPDATE
                """, result -> result.next() && result.getBoolean("valid"),
                claim.token(), Timestamp.from(now), claim.id()));
    }

    @Override
    @Transactional
    public CurrentTarget currentTarget(ClaimedPublication claim) {
        return jdbc.query("""
                SELECT binding.skill_id, binding.latest_content_sha256,
                       (binding.retry_requested_at IS NOT NULL
                        AND claimed.created_at >= binding.retry_requested_at) AS retry_requested,
                       COALESCE((latest_task.created_at, latest_task.id)
                           > (claimed_task.created_at, claimed_task.id), FALSE) AS newer_acquisition
                FROM github_sync_package_ingestion claimed
                JOIN github_sync_task claimed_task ON claimed_task.id = claimed.task_id
                LEFT JOIN github_source_skill_binding binding
                  ON binding.namespace_id = claimed.namespace_id
                 AND binding.repository_id = claimed.repository_id
                 AND binding.skill_path = claimed.skill_path
                LEFT JOIN github_sync_package_ingestion latest
                  ON latest.id = binding.latest_ingestion_id
                LEFT JOIN github_sync_task latest_task ON latest_task.id = latest.task_id
                WHERE claimed.id = ?
                """, result -> result.next()
                        ? new CurrentTarget(result.getObject("skill_id", Long.class),
                                result.getString("latest_content_sha256"),
                                result.getBoolean("retry_requested"),
                                result.getBoolean("newer_acquisition"))
                        : new CurrentTarget(null, null, false, false), claim.id());
    }

    @Override
    @Transactional
    public boolean superseded(ClaimedPublication claim, Instant now) {
        return settle(claim, "SUPERSEDED", null, null, now);
    }

    @Override
    public void recordOutcome(ClaimedPublication claim, Long skillId, Long versionId,
                              boolean newSkill, Instant now) {
        new JdbcGithubDiscoveryOutcomeRepository(jdbc).publication(claim.namespaceId(),
                claim.repositoryId(), skillId, versionId, newSkill, now);
    }

    @Override
    @Transactional
    public boolean readyForReview(ClaimedPublication claim, Long skillId, Long versionId, Instant now) {
        int updated = jdbc.update("""
                UPDATE github_sync_package_ingestion
                SET status = 'READY_FOR_REVIEW', publication_token = NULL,
                    publication_expires_at = NULL, publication_retry_at = NULL,
                    published_skill_id = ?, published_version_id = ?,
                    last_error_code = NULL, updated_at = ?
                WHERE id = ? AND status = 'VALIDATED' AND publication_token = ?
                """, skillId, versionId, Timestamp.from(now), claim.id(), claim.token());
        if (updated != 1) {
            return false;
        }
        record BindingRevision(long id, int revision) {}
        BindingRevision binding = jdbc.query("""
                INSERT INTO github_source_skill_binding
                    (source_id, namespace_id, repository_id, skill_path, skill_id,
                     latest_content_sha256, latest_ingestion_id, latest_revision)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1)
                ON CONFLICT (namespace_id, repository_id, skill_path) DO UPDATE
                SET latest_content_sha256 = EXCLUDED.latest_content_sha256,
                    latest_ingestion_id = EXCLUDED.latest_ingestion_id,
                    latest_revision = CASE
                        WHEN github_source_skill_binding.latest_revision = 0 THEN 1
                        WHEN github_source_skill_binding.latest_content_sha256 = EXCLUDED.latest_content_sha256
                            THEN github_source_skill_binding.latest_revision
                        ELSE github_source_skill_binding.latest_revision + 1 END,
                    retry_requested_at = NULL,
                    retry_scheduled_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE github_source_skill_binding.skill_id = EXCLUDED.skill_id
                  AND github_source_skill_binding.source_id = EXCLUDED.source_id
                RETURNING id, latest_revision
                """, statement -> {
            statement.setLong(1, claim.sourceId());
            statement.setLong(2, claim.namespaceId());
            statement.setLong(3, claim.repositoryId());
            statement.setString(4, claim.skillPath());
            statement.setLong(5, skillId);
            statement.setString(6, claim.contentSha256());
            statement.setObject(7, claim.id());
        }, result -> result.next()
                ? new BindingRevision(result.getLong("id"), result.getInt("latest_revision")) : null);
        if (binding == null) {
            throw new IllegalStateException("GitHub Skill path binding changed while publishing");
        }
        if (jdbc.update("""
                UPDATE skill_version SET github_sync_revision = ?
                WHERE id = ? AND skill_id = ? AND github_sync_ingestion_id = ?
                """, binding.revision(), versionId, skillId, claim.id()) != 1) {
            throw new IllegalStateException("GitHub Skill version changed while assigning content revision");
        }
        jdbc.update("""
                UPDATE github_source_skill_binding binding
                SET upstream_presence = 'PRESENT', presence_commit_sha = ingestion.commit_sha,
                    missing_since = NULL, updated_at = ?
                FROM github_sync_package_ingestion ingestion
                JOIN github_sync_task task ON task.id = ingestion.task_id
                JOIN github_source_binding source ON source.id = task.source_id
                WHERE binding.id = ? AND ingestion.id = ?
                  AND binding.upstream_presence = 'UNKNOWN'
                  AND task.mode = 'FULL'
                  AND task.status IN ('ACQUISITION_COMPLETE', 'PARTIAL')
                  AND task.completed_at IS NOT NULL
                  AND task.incomplete_subtrees = '[]'::jsonb
                  AND task.fixed_commit_sha = ingestion.commit_sha
                  AND source.status <> 'PAUSED'
                  AND COALESCE(task.snapshot_json -> 'ignorePatterns', '[]'::jsonb)
                      = source.ignore_patterns
                  AND NOT EXISTS (SELECT 1 FROM github_sync_task newer
                      WHERE newer.source_id = task.source_id
                        AND newer.kind = 'REPOSITORY_SYNC' AND newer.mode = 'FULL'
                        AND (newer.created_at, newer.id) > (task.created_at, task.id))
                """, Timestamp.from(now), binding.id(), claim.id());
        jdbc.update("""
                UPDATE github_sync_package_ingestion
                SET status = 'SUPERSEDED', updated_at = ?
                WHERE namespace_id = ? AND repository_id = ?
                  AND (skill_path = ? OR published_skill_id = ?)
                  AND id <> ? AND status IN ('READY_FOR_REVIEW', 'BLOCKED')
                """, Timestamp.from(now), claim.namespaceId(), claim.repositoryId(),
                claim.skillPath(), skillId, claim.id());
        return true;
    }

    @Override
    @Transactional
    public boolean noChange(ClaimedPublication claim, Instant now) {
        return settleUnchanged(claim, null, now);
    }

    @Override
    @Transactional
    public PathMoveDecision inspectPathMove(ClaimedPublication claim) {
        if (claim.expectedSkillId() != null) {
            return PathMoveDecision.of(PathMoveOutcome.NONE);
        }
        // Serializes path decisions for this source, including two new paths with the same hash.
        boolean sourceExists = Boolean.TRUE.equals(jdbc.query(
                "SELECT id FROM github_source_binding WHERE id = ? FOR UPDATE",
                (ResultSetExtractor<Boolean>) result -> result.next(), claim.sourceId()));
        if (!sourceExists) {
            return PathMoveDecision.of(PathMoveOutcome.RETRY);
        }
        Integer boundPaths = jdbc.queryForObject("""
                SELECT count(*) FROM github_source_skill_binding
                WHERE source_id = ? AND namespace_id = ? AND repository_id = ?
                """, Integer.class, claim.sourceId(), claim.namespaceId(), claim.repositoryId());
        if (boundPaths == null || boundPaths == 0) {
            return PathMoveDecision.of(PathMoveOutcome.NONE);
        }
        String taskStatus = jdbc.query("""
                SELECT CASE WHEN COALESCE(task.snapshot_json -> 'ignorePatterns', '[]'::jsonb)
                                     <> source.ignore_patterns
                                 OR EXISTS (SELECT 1 FROM github_sync_task newer
                                    WHERE newer.source_id = task.source_id
                                      AND newer.kind = 'REPOSITORY_SYNC' AND newer.mode = 'FULL'
                                      AND (newer.created_at, newer.id) > (task.created_at, task.id))
                            THEN 'STALE'
                            WHEN task.mode = 'FULL' AND task.status = 'PARTIAL'
                                 AND task.completed_at IS NOT NULL
                                 AND task.incomplete_subtrees = '[]'::jsonb
                                 AND task.fixed_commit_sha = ingestion.commit_sha
                            THEN 'TERMINAL_PARTIAL'
                            WHEN task.mode = 'FULL' AND task.status = 'ACQUISITION_COMPLETE'
                                 AND task.fixed_commit_sha = ingestion.commit_sha
                            THEN 'COMPLETE' ELSE task.status END AS scan_status
                FROM github_sync_task task
                JOIN github_sync_package_ingestion ingestion ON ingestion.task_id = task.id
                JOIN github_source_binding source ON source.id = task.source_id
                WHERE ingestion.id = ? AND task.namespace_id = ? AND task.source_id = ?
                """, result -> result.next() ? result.getString("scan_status") : null,
                claim.id(), claim.namespaceId(), claim.sourceId());
        Integer currentPathPresent = jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_path_result path
                JOIN github_sync_package_ingestion ingestion ON ingestion.task_id = path.task_id
                WHERE ingestion.id = ? AND path.commit_sha = ingestion.commit_sha
                  AND path.skill_path = ingestion.skill_path AND path.outcome = 'ACCEPTED'
                """, Integer.class, claim.id());
        if ("TERMINAL_PARTIAL".equals(taskStatus)) {
            // A terminal failure in another Skill does not invalidate an accepted
            // new path when every existing binding was also observed. Do not infer
            // a move or a deletion from this partial result.
            return PathMoveDecision.of(currentPathPresent != null && currentPathPresent == 1
                    && unobservedBoundPaths(claim) == 0
                    ? PathMoveOutcome.NONE : PathMoveOutcome.INCOMPLETE);
        }
        if (("LEASED".equals(taskStatus) || "RETRY_WAIT".equals(taskStatus))
                && currentPathPresent != null && currentPathPresent == 1
                && unobservedBoundPaths(claim) == 0) {
            // Every existing binding has already appeared in this fixed-revision
            // result. This new path cannot be a move, even if scanning is paused.
            return PathMoveDecision.of(PathMoveOutcome.NONE);
        }
        if (!"COMPLETE".equals(taskStatus)) {
            return PathMoveDecision.of("STALE".equals(taskStatus) ? PathMoveOutcome.STALE
                    : "PARTIAL".equals(taskStatus) || "FAILED".equals(taskStatus)
                    ? PathMoveOutcome.INCOMPLETE
                    : PathMoveOutcome.RETRY);
        }
        if (currentPathPresent == null || currentPathPresent != 1) {
            return PathMoveDecision.of(PathMoveOutcome.AMBIGUOUS);
        }
        List<PathMoveDecision> matches = jdbc.query("""
                SELECT skill_id, skill_path FROM github_source_skill_binding
                WHERE source_id = ? AND namespace_id = ? AND repository_id = ?
                  AND latest_content_sha256 = ? AND skill_path <> ?
                """, (result, row) -> new PathMoveDecision(PathMoveOutcome.CANDIDATE,
                result.getLong("skill_id"), result.getString("skill_path")),
                claim.sourceId(), claim.namespaceId(), claim.repositoryId(),
                claim.contentSha256(), claim.skillPath());
        if (matches.isEmpty()) {
            return PathMoveDecision.of(unobservedBoundPaths(claim) > 0
                    ? PathMoveOutcome.AMBIGUOUS : PathMoveOutcome.NONE);
        }
        if (matches.size() != 1) {
            return PathMoveDecision.of(PathMoveOutcome.AMBIGUOUS);
        }
        PathMoveDecision match = matches.getFirst();
        Integer oldPathPresent = jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_path_result path
                JOIN github_sync_package_ingestion ingestion ON ingestion.task_id = path.task_id
                WHERE ingestion.id = ? AND path.commit_sha = ingestion.commit_sha
                  AND path.skill_path = ?
                """, Integer.class, claim.id(), match.previousPath());
        if (oldPathPresent != null && oldPathPresent > 0) {
            return PathMoveDecision.of(PathMoveOutcome.NONE);
        }
        Integer unsettled = jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion candidate
                JOIN github_sync_package_ingestion current ON current.task_id = candidate.task_id
                WHERE current.id = ? AND candidate.commit_sha = current.commit_sha
                  AND candidate.status IN ('RECEIVED', 'VALIDATING', 'RETRY_WAIT')
                """, Integer.class, claim.id());
        if (unsettled != null && unsettled > 0) {
            return PathMoveDecision.of(PathMoveOutcome.RETRY);
        }
        Integer failed = jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion candidate
                JOIN github_sync_package_ingestion current ON current.task_id = candidate.task_id
                WHERE current.id = ? AND candidate.commit_sha = current.commit_sha
                  AND candidate.status = 'FAILED'
                """, Integer.class, claim.id());
        Integer sameContent = jdbc.queryForObject("""
                SELECT count(*) FROM github_sync_package_ingestion candidate
                JOIN github_sync_package_ingestion current ON current.task_id = candidate.task_id
                WHERE current.id = ? AND candidate.commit_sha = current.commit_sha
                  AND candidate.content_sha256 = ? AND candidate.skill_path <> ?
                """, Integer.class, claim.id(), claim.contentSha256(), match.previousPath());
        if (failed != null && failed > 0 || sameContent == null || sameContent != 1) {
            return PathMoveDecision.of(PathMoveOutcome.AMBIGUOUS);
        }
        return match;
    }

    private int unobservedBoundPaths(ClaimedPublication claim) {
        Integer missing = jdbc.queryForObject("""
                SELECT count(*) FROM github_source_skill_binding binding
                JOIN github_sync_package_ingestion ingestion ON ingestion.id = ?
                WHERE binding.source_id = ? AND binding.namespace_id = ?
                  AND binding.repository_id = ? AND binding.skill_path <> ?
                  AND NOT EXISTS (
                      SELECT 1 FROM github_sync_path_result path
                      WHERE path.task_id = ingestion.task_id
                        AND path.commit_sha = ingestion.commit_sha
                        AND path.skill_path = binding.skill_path
                        AND path.outcome <> 'RETRYABLE_FAILURE')
                """, Integer.class, claim.id(), claim.sourceId(), claim.namespaceId(),
                claim.repositoryId(), claim.skillPath());
        return missing == null ? 1 : missing;
    }

    @Override
    @Transactional
    public boolean movePath(ClaimedPublication claim, PathMoveDecision decision, Instant now) {
        if (decision.outcome() != PathMoveOutcome.CANDIDATE) {
            throw new IllegalArgumentException("GitHub path move requires a unique candidate");
        }
        // Path observations do not replace the current content/review target. Keeping its
        // ingestion identity also preserves pending decisions and explicit rejected retries.
        int updated = jdbc.update("""
                UPDATE github_source_skill_binding
                SET skill_path = ?,
                    upstream_presence = 'PRESENT',
                    presence_commit_sha = (SELECT commit_sha FROM github_sync_package_ingestion
                                           WHERE id = ?),
                    missing_since = NULL, updated_at = ?
                WHERE source_id = ? AND namespace_id = ? AND repository_id = ?
                  AND skill_path = ? AND skill_id = ? AND latest_content_sha256 = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM github_source_skill_binding occupied
                      WHERE occupied.namespace_id = ? AND occupied.repository_id = ?
                        AND occupied.skill_path = ?)
                """, claim.skillPath(), claim.id(), Timestamp.from(now), claim.sourceId(),
                claim.namespaceId(), claim.repositoryId(), decision.previousPath(), decision.skillId(),
                claim.contentSha256(), claim.namespaceId(), claim.repositoryId(), claim.skillPath());
        if (updated != 1) {
            throw new IllegalStateException("GitHub Skill path binding changed while moving");
        }
        return settleUnchanged(claim, decision.skillId(), now);
    }

    private boolean settleUnchanged(ClaimedPublication claim, Long movedSkillId, Instant now) {
        // Record the exact existing content target before a later sync can advance the binding.
        return jdbc.update("""
                UPDATE github_sync_package_ingestion acquired
                SET status = 'NO_CHANGE', published_skill_id = ?,
                    reused_version_ingestion_id = (
                        SELECT reused.id
                        FROM github_source_skill_binding binding
                        JOIN github_sync_package_ingestion previous ON previous.id = binding.latest_ingestion_id
                        JOIN github_sync_package_ingestion reused
                          ON reused.id = COALESCE(previous.reused_version_ingestion_id, previous.id)
                        WHERE binding.source_id = ? AND binding.namespace_id = acquired.namespace_id
                          AND binding.repository_id = acquired.repository_id
                          AND binding.skill_path = acquired.skill_path
                          AND reused.namespace_id = acquired.namespace_id AND reused.repository_id = acquired.repository_id
                          AND reused.published_skill_id = binding.skill_id
                          AND reused.published_version_id IS NOT NULL
                          AND reused.content_sha256 = acquired.content_sha256
                    ),
                    publication_token = NULL, publication_expires_at = NULL,
                    publication_retry_at = NULL, last_error_code = NULL, updated_at = ?
                WHERE acquired.id = ? AND acquired.status = 'VALIDATED' AND acquired.publication_token = ?
                  AND acquired.publication_expires_at > ?
                """, movedSkillId, claim.sourceId(), Timestamp.from(now), claim.id(), claim.token(),
                Timestamp.from(now)) == 1;
    }

    @Override
    @Transactional
    public boolean ignored(ClaimedPublication claim, Instant now) {
        if (!settle(claim, "FAILED", "SKILL_PATH_IGNORED", null, now)) {
            return false;
        }
        jdbc.update("""
                UPDATE github_sync_path_result path
                SET outcome = 'IGNORED', reason = 'SKILL_PATH_IGNORED',
                    retry_at = NULL, updated_at = ?
                FROM github_sync_package_ingestion ingestion
                WHERE ingestion.id = ? AND path.task_id = ingestion.task_id
                  AND path.commit_sha = ingestion.commit_sha AND path.skill_path = ingestion.skill_path
                """, Timestamp.from(now), claim.id());
        return true;
    }

    @Override
    @Transactional
    public boolean blocked(ClaimedPublication claim, String reason, Instant now) {
        return settle(claim, "BLOCKED", reason, null, now);
    }

    @Override
    @Transactional
    public boolean blockedSlugConflict(ClaimedPublication claim, String skillSlug, Instant now) {
        if (skillSlug == null || skillSlug.isBlank() || skillSlug.length() > 128) {
            throw new IllegalArgumentException("GitHub sync conflict requires a valid Skill slug");
        }
        return jdbc.update("""
                UPDATE github_sync_package_ingestion
                SET status = 'BLOCKED', publication_token = NULL, publication_expires_at = NULL,
                    publication_retry_at = NULL, last_error_code = 'SLUG_CONFLICT',
                    target_skill_slug = ?, updated_at = ?
                WHERE id = ? AND status = 'VALIDATED' AND publication_token = ?
                  AND publication_expires_at > ?
                """, skillSlug, Timestamp.from(now), claim.id(), claim.token(),
                Timestamp.from(now)) == 1;
    }

    @Override
    @Transactional
    public boolean retry(ClaimedPublication claim, String reason, Instant retryAt, Instant now) {
        if (retryAt == null || !retryAt.isAfter(now)) {
            throw new IllegalArgumentException("GitHub publication retry must be in the future");
        }
        return settle(claim, "VALIDATED", reason, retryAt, now);
    }

    private boolean settle(ClaimedPublication claim, String status, String reason,
                           Instant retryAt, Instant now) {
        return jdbc.update("""
                UPDATE github_sync_package_ingestion
                SET status = ?, publication_token = NULL, publication_expires_at = NULL,
                    publication_retry_at = ?, last_error_code = ?, updated_at = ?
                WHERE id = ? AND status = 'VALIDATED' AND publication_token = ?
                  AND publication_expires_at > ?
                """, status, retryAt == null ? null : Timestamp.from(retryAt), reason,
                Timestamp.from(now), claim.id(), claim.token(), Timestamp.from(now)) == 1;
    }
}
