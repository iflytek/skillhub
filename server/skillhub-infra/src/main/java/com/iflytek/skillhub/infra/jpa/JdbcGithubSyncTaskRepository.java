package com.iflytek.skillhub.infra.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.githubsync.GithubSyncFailureReason;
import com.iflytek.skillhub.githubsync.GithubDiscoveryBatch;
import com.iflytek.skillhub.githubsync.GithubDiscoveryBatchResult;
import com.iflytek.skillhub.githubsync.GithubDiscoveryCandidate;
import com.iflytek.skillhub.githubsync.GithubDiscoveryCompletionResult;
import com.iflytek.skillhub.githubsync.GithubSyncDeferralReason;
import com.iflytek.skillhub.githubsync.GithubSyncTaskKind;
import com.iflytek.skillhub.githubsync.GithubSyncTaskLease;
import com.iflytek.skillhub.githubsync.GithubSyncTaskMode;
import com.iflytek.skillhub.githubsync.GithubSyncTaskRepository;
import com.iflytek.skillhub.githubsync.GithubPackageAdmissionResult;
import com.iflytek.skillhub.githubsync.GithubRepositoryCompletionResult;
import com.iflytek.skillhub.githubsync.GithubRepositoryMetadataResult;
import com.iflytek.skillhub.githubsync.GithubRepositoryPathResult;
import com.iflytek.skillhub.githubsync.RepositorySkillCoordinate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Claims require PostgreSQL's SKIP LOCKED and a single UPDATE/RETURNING statement: a read followed
 * by a JPA save would allow two application instances to issue leases for the same task.
 */
@Repository
public class JdbcGithubSyncTaskRepository implements GithubSyncTaskRepository {
    private static final String CLAIM_SQL = """
            WITH picked AS (
                SELECT candidate.id, capability.required_capabilities FROM github_sync_task candidate
                LEFT JOIN github_platform_collection_control platform ON platform.namespace_id=candidate.namespace_id
                LEFT JOIN github_deep_discovery_query fair_query ON fair_query.id=candidate.official_query_id
                LEFT JOIN github_sync_claim_cursor cursor
                    ON cursor.kind = candidate.kind AND cursor.namespace_id = candidate.namespace_id
                LEFT JOIN github_candidate_discovery_eligibility eligibility
                    ON candidate.kind = 'REPOSITORY_SYNC' AND candidate.source_id IS NULL
                    AND eligibility.namespace_id = candidate.namespace_id
                    AND eligibility.repository_id::text = candidate.snapshot_json ->> 'repositoryId'
                CROSS JOIN LATERAL (SELECT array_remove(CASE
                    WHEN candidate.kind = 'REPOSITORY_SYNC' AND candidate.source_id IS NOT NULL
                        THEN array_remove(candidate.required_capabilities, 'official-pause/v1')
                    WHEN eligibility.has_official THEN
                    array_remove(candidate.required_capabilities, 'official-pause/v1') ||
                        CASE WHEN eligibility.official_only THEN ARRAY['official-pause/v1'] ELSE ARRAY[]::text[] END
                    ELSE candidate.required_capabilities END, 'platform-pause/v1') || CASE WHEN platform.namespace_id IS NOT NULL
                        THEN ARRAY['platform-pause/v1'] ELSE ARRAY[]::text[] END AS required_capabilities) capability
                WHERE candidate.kind = ? AND ((candidate.status IN ('QUEUED', 'RETRY_WAIT')
                    AND candidate.due_at <= CASE WHEN candidate.official_query_id IS NOT NULL THEN clock_timestamp() ELSE ? END)
                   OR (candidate.status = 'LEASED' AND candidate.lease_expires_at <= CASE WHEN candidate.official_query_id IS NOT NULL THEN clock_timestamp() ELSE ? END))
                  AND (platform.namespace_id IS NULL OR NOT platform.paused)
                  AND capability.required_capabilities <@ ?::text[]
                  AND (candidate.official_query_id IS NULL OR candidate.kind <> 'DISCOVERY' OR (
                      (SELECT next_lease_eligible_at <= clock_timestamp() FROM github_official_discovery_budget WHERE id)
                      AND (SELECT count(*) FROM github_discovery_candidate pending
                           WHERE pending.namespace_id=candidate.namespace_id
                             AND pending.status IN ('PENDING','VALIDATING'))
                          < (SELECT candidate_pending_limit FROM github_sync_admission_policy WHERE id)
                      AND NOT EXISTS (SELECT 1 FROM github_sync_task active
                          WHERE active.kind='DISCOVERY' AND active.official_query_id IS NOT NULL
                            AND active.status='LEASED' AND active.lease_expires_at > clock_timestamp())))
                  AND (eligibility.candidate_id IS NULL OR eligibility.acquisition_allowed)
                  AND (candidate.official_query_id IS NULL OR EXISTS (
                      SELECT 1 FROM github_deep_discovery_query official
                      JOIN github_deep_discovery_config config ON config.namespace_id = official.namespace_id
                      WHERE official.id = candidate.official_query_id
                        AND official.namespace_id = candidate.namespace_id
                        AND official.active_task_id = candidate.id
                        AND config.enabled = TRUE
                        AND NOT EXISTS (SELECT 1 FROM github_official_owner_eligibility owner
                            WHERE owner.query_id = official.id AND owner.excluded)))
                  AND (candidate.source_id IS NULL OR EXISTS (
                      SELECT 1 FROM github_source_binding source
                      WHERE source.id = candidate.source_id AND source.status <> 'PAUSED'))
                  AND (candidate.kind <> 'DISCOVERY' OR NOT EXISTS (
                      SELECT 1 FROM github_discovery_entry entry
                      WHERE entry.id = (candidate.snapshot_json->>'ruleId')::uuid
                        AND entry.namespace_id = candidate.namespace_id
                        AND entry.enabled = FALSE))
                ORDER BY CASE WHEN candidate.source_id IS NOT NULL THEN 0 ELSE 1 END,
                         CASE WHEN candidate.official_query_id IS NOT NULL THEN 0 ELSE 1 END,
                         CASE WHEN candidate.source_id IS NULL AND candidate.official_query_id IS NULL THEN candidate.priority ELSE 0 END DESC,
                         fair_query.last_successful_claim_at NULLS FIRST,
                         cursor.last_claimed_at NULLS FIRST,
                         candidate.priority DESC, candidate.due_at, candidate.id
                LIMIT 1 FOR UPDATE OF candidate SKIP LOCKED
            )
            UPDATE github_sync_task AS task
            SET status = 'LEASED',
                required_capabilities = picked.required_capabilities,
                lease_generation = task.lease_generation + 1,
                lease_token_hash = ?,
                lease_expires_at = CASE WHEN task.official_query_id IS NOT NULL
                    THEN clock_timestamp() + (?::timestamptz - ?::timestamptz) ELSE ? END,
                leased_by = ?,
                credential_id = NULL,
                credential_fingerprint = NULL,
                updated_at = ?
            FROM picked
            WHERE task.id = picked.id
            RETURNING task.id, task.run_id, task.kind, task.mode, task.namespace_id,
                      task.source_id, task.snapshot_json::text AS snapshot_json,
                      task.discovery_checkpoint, task.fixed_commit_sha,
                      task.path_checkpoint, task.incomplete_subtrees::text AS incomplete_subtrees,
                      ARRAY(SELECT path.skill_path FROM github_sync_path_result path
                            WHERE path.task_id = task.id AND path.commit_sha = task.fixed_commit_sha
                              AND path.outcome = 'RETRYABLE_FAILURE'
                            ORDER BY path.skill_path) AS retry_paths,
                      ARRAY(SELECT ingestion.skill_path FROM github_sync_package_ingestion ingestion
                            WHERE ingestion.task_id = task.id
                              AND ingestion.commit_sha = task.fixed_commit_sha
                            ORDER BY ingestion.skill_path) AS accepted_paths,
                      task.lease_generation, task.lease_expires_at, task.required_capabilities,
                      (SELECT version FROM github_platform_collection_control WHERE namespace_id=task.namespace_id) AS platform_control_version
            """;

    private static final String RENEW_SQL = """
            UPDATE github_sync_task
            SET lease_expires_at = CASE WHEN official_query_id IS NOT NULL
                THEN clock_timestamp() + (?::timestamptz - ?::timestamptz) ELSE ? END, updated_at = ?
            WHERE id = ? AND status = 'LEASED' AND lease_generation = ?
              AND lease_token_hash = ? AND lease_expires_at > CASE WHEN official_query_id IS NOT NULL
                  THEN clock_timestamp() ELSE ? END
            RETURNING lease_expires_at
            """;

    private static final String LOCK_ADMISSION_TASK_SQL = """
            SELECT task.namespace_id, task.source_id, task.kind, task.mode, task.fixed_commit_sha,
                   task.snapshot_json ->> 'repositoryId' AS snapshot_repository_id,
                   task.snapshot_json ->> 'fixedCommitSha' AS snapshot_commit_sha,
                   source.repository_id AS source_repository_id
            FROM github_sync_task task
            LEFT JOIN github_source_binding source ON source.id = task.source_id
            WHERE task.id = ? AND task.status = 'LEASED' AND task.lease_generation = ?
              AND task.lease_token_hash = ? AND task.lease_expires_at > CASE WHEN task.official_query_id IS NOT NULL THEN clock_timestamp() ELSE ? END
            FOR UPDATE OF task
            """;

    private static final String LOCK_LEASE_SQL = """
            SELECT source_id, kind, mode FROM github_sync_task
            WHERE id = ? AND status = 'LEASED' AND lease_generation = ?
              AND lease_token_hash = ? AND lease_expires_at > CASE WHEN official_query_id IS NOT NULL THEN clock_timestamp() ELSE ? END
            FOR UPDATE
            """;

    private static final String LOCK_DISCOVERY_LEASE_SQL = """
            SELECT namespace_id, discovery_checkpoint,
                   COALESCE(snapshot_json ->> 'candidateBudget',
                       snapshot_json #>> '{officialDiscovery,budget,candidateBudget}') AS candidate_budget,
                   CASE WHEN official_query_id IS NOT NULL THEN 'OFFICIAL_DISCOVERY'
                        ELSE snapshot_json ->> 'entryType' END AS entry_type,
                   snapshot_json ->> 'ruleId' AS entry_id
            FROM github_sync_task
            WHERE id = ? AND kind = 'DISCOVERY' AND status = 'LEASED'
              AND lease_generation = ? AND lease_token_hash = ? AND lease_expires_at > CASE WHEN official_query_id IS NOT NULL THEN clock_timestamp() ELSE ? END
            FOR UPDATE
            """;

    private static final String LOCK_REPOSITORY_LEASE_SQL = """
            SELECT source_id, fixed_commit_sha, path_checkpoint,
                   snapshot_json ->> 'fixedCommitSha' AS snapshot_commit_sha
            FROM github_sync_task
            WHERE id = ? AND kind = 'REPOSITORY_SYNC' AND mode = 'FULL'
              AND status = 'LEASED' AND lease_generation = ?
              AND lease_token_hash = ? AND lease_expires_at > CASE WHEN official_query_id IS NOT NULL THEN clock_timestamp() ELSE ? END
            FOR UPDATE
            """;

    private final JdbcTemplate jdbc;
    private final JdbcGithubDiscoveryOutcomeRepository outcomes;
    private final ObjectMapper objectMapper;
    private final JdbcGithubSyncCapacityGate capacityGate;
    private final JdbcGithubSyncSourceCheckpointRepository checkpoints;
    private final JdbcGithubSourceIgnoreRepository ignores;
    private final JdbcGithubSyncPathPresenceRepository presence;

    public JdbcGithubSyncTaskRepository(JdbcTemplate jdbc, ObjectMapper objectMapper,
                                        JdbcGithubSyncCapacityGate capacityGate,
                                        JdbcGithubSyncSourceCheckpointRepository checkpoints,
                                        JdbcGithubSourceIgnoreRepository ignores,
                                        JdbcGithubSyncPathPresenceRepository presence) {
        this.jdbc = jdbc;
        this.outcomes = new JdbcGithubDiscoveryOutcomeRepository(jdbc);
        this.objectMapper = objectMapper;
        this.capacityGate = capacityGate;
        this.checkpoints = checkpoints;
        this.ignores = ignores;
        this.presence = presence;
    }

    @Override
    public Optional<GithubSyncTaskLease> claim(GithubSyncTaskKind kind, String workerId, String leaseTokenHash,
                                                Instant now, Instant expiresAt) {
        return claim(kind, workerId, leaseTokenHash, now, expiresAt, List.of());
    }

    @Override
    public Optional<GithubSyncTaskLease> claim(GithubSyncTaskKind kind, String workerId, String leaseTokenHash,
            Instant now, Instant expiresAt, List<String> capabilities) {
        jdbc.queryForList("SELECT namespace_id FROM github_platform_collection_control FOR SHARE");
        if (kind == GithubSyncTaskKind.DISCOVERY || kind == GithubSyncTaskKind.REPOSITORY_SYNC) {
            // Hold through the lease transaction so a completed disable cannot race an official claim.
            jdbc.queryForObject("SELECT enabled FROM github_deep_discovery_config WHERE id = TRUE FOR SHARE",
                    Boolean.class);
        }
        if (kind == GithubSyncTaskKind.DISCOVERY) {
            // Serialize official grant decisions across all workers. Legacy candidates remain eligible.
            jdbc.queryForObject("SELECT id FROM github_official_discovery_budget WHERE id FOR UPDATE", Boolean.class);
        }
        Optional<GithubSyncTaskLease> claimed = jdbc.query(connection -> {
            var statement = connection.prepareStatement(CLAIM_SQL);
            statement.setString(1, kind.name());
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setArray(4, connection.createArrayOf("text", capabilities.toArray()));
            statement.setString(5, leaseTokenHash);
            statement.setTimestamp(6, Timestamp.from(expiresAt));
            statement.setTimestamp(7, Timestamp.from(now));
            statement.setTimestamp(8, Timestamp.from(expiresAt));
            statement.setString(9, workerId);
            statement.setTimestamp(10, Timestamp.from(now));
            return statement;
        }, result -> result.next() ? Optional.of(mapLease(result)) : Optional.empty());
        claimed.ifPresent(task -> jdbc.update("""
                INSERT INTO github_sync_claim_cursor (kind, namespace_id, last_claimed_at)
                VALUES (?, ?, ?)
                ON CONFLICT (kind, namespace_id) DO UPDATE
                SET last_claimed_at = GREATEST(github_sync_claim_cursor.last_claimed_at,
                                               EXCLUDED.last_claimed_at)
                """, kind.name(), task.namespaceId(), Timestamp.from(now)));
        claimed.ifPresent(task -> jdbc.update("""
                UPDATE github_sync_package_admission
                SET status = 'RELEASED', updated_at = ?
                WHERE task_id = ? AND lease_generation < ? AND status = 'ACTIVE'
                """, Timestamp.from(now), task.taskId(), task.leaseGeneration()));
        if (claimed.isPresent() && claimed.get().mode() == GithubSyncTaskMode.FULL
                && !capacityGate.allowsFullAcquisition(claimed.get().namespaceId(), now)) {
            GithubSyncTaskLease task = claimed.get();
            if (task.sourceId() != null) {
                jdbc.update("UPDATE github_sync_task SET mode = 'HEAD_ONLY', updated_at = ? WHERE id = ?",
                        Timestamp.from(now), task.taskId());
                return Optional.of(new GithubSyncTaskLease(task.taskId(), task.runId(), task.kind(),
                        GithubSyncTaskMode.HEAD_ONLY, task.namespaceId(), task.sourceId(),
                        task.snapshotJson(), task.discoveryCheckpoint(), task.fixedCommitSha(),
                        task.pathCheckpoint(), task.incompleteSubtreesJson(), task.retryPaths(),
                        task.acceptedPaths(),
                        task.leaseGeneration(), task.leaseExpiresAt(), task.requiredCapabilities(), task.platformControlVersion()));
            }
            Instant retryAt = now.plus(Duration.ofMinutes(15));
            jdbc.update("""
                    UPDATE github_sync_task
                    SET status = 'RETRY_WAIT', due_at = ?, retry_at = ?,
                        lease_token_hash = NULL, lease_expires_at = NULL, leased_by = NULL,
                        last_error_code = 'BACKPRESSURE', updated_at = ?
                    WHERE id = ?
                    """, Timestamp.from(retryAt), Timestamp.from(retryAt),
                    Timestamp.from(now), task.taskId());
            return Optional.empty();
        }
        if (claimed.isPresent() && claimed.get().mode() == GithubSyncTaskMode.FULL
                && claimed.get().sourceId() != null) {
            jdbc.update("""
                    UPDATE github_source_binding
                    SET manual_rescan_requested_at = NULL, updated_at = ?
                    WHERE id = ? AND manual_rescan_requested_at IS NOT NULL
                    """, Timestamp.from(now), claimed.get().sourceId());
        }
        return claimed;
    }

    @Override
    public Optional<Instant> renew(UUID taskId, long leaseGeneration, String leaseTokenHash,
                                   Instant now, Instant expiresAt) {
        return jdbc.query(connection -> {
            var statement = connection.prepareStatement(RENEW_SQL);
            statement.setTimestamp(1, Timestamp.from(expiresAt));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setTimestamp(3, Timestamp.from(expiresAt));
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setObject(5, taskId);
            statement.setLong(6, leaseGeneration);
            statement.setString(7, leaseTokenHash);
            statement.setTimestamp(8, Timestamp.from(now));
            return statement;
        }, result -> result.next()
                ? Optional.of(result.getTimestamp("lease_expires_at").toInstant())
                : Optional.empty());
    }

    @Override
    public GithubRepositoryMetadataResult updateRepositoryMetadata(UUID taskId, long leaseGeneration,
            String leaseTokenHash, long repositoryId, String owner, String name,
            String defaultBranch, Instant now) {
        return updateRepositoryMetadata(taskId, leaseGeneration, leaseTokenHash, repositoryId, owner, name, defaultBranch, null, now);
    }

    @Override
    public GithubRepositoryMetadataResult updateRepositoryMetadata(UUID taskId, long leaseGeneration,
            String leaseTokenHash, long repositoryId, String owner, String name,
            String defaultBranch, Long ownerId, Instant now) {
        List<RepositoryMetadataTask> leased = jdbc.query("""
                SELECT task.source_id, (task.snapshot_json ->> 'repositoryId')::bigint AS repository_id
                FROM github_sync_task task
                WHERE task.id = ? AND task.kind = 'REPOSITORY_SYNC' AND task.status = 'LEASED'
                  AND task.lease_generation = ? AND task.lease_token_hash = ?
                  AND task.lease_expires_at > CASE WHEN task.official_query_id IS NOT NULL THEN clock_timestamp() ELSE ? END
                FOR UPDATE OF task
                """, (result, row) -> new RepositoryMetadataTask(
                (Long) result.getObject("source_id"), result.getLong("repository_id")),
                taskId, leaseGeneration, leaseTokenHash, Timestamp.from(now));
        if (leased.isEmpty()) {
            return GithubRepositoryMetadataResult.LEASE_STALE;
        }
        RepositoryMetadataTask task = leased.getFirst();
        if (task.repositoryId() != repositoryId) {
            return GithubRepositoryMetadataResult.REPOSITORY_MISMATCH;
        }
        if (task.sourceId() != null) {
            int updated = jdbc.update("""
                    UPDATE github_source_binding
                    SET owner_login = ?, repository_name = ?, default_branch = ?, updated_at = ?
                    WHERE id = ? AND repository_id = ?
                    """, owner, name, defaultBranch, Timestamp.from(now), task.sourceId(), repositoryId);
            if (updated != 1) {
                return GithubRepositoryMetadataResult.REPOSITORY_MISMATCH;
            }
        } else {
            int updated = jdbc.update("""
                    UPDATE github_discovery_candidate
                    SET owner_login = ?, repository_name = ?, default_branch = ?, last_seen_at = ?
                    WHERE validation_task_id = ? AND repository_id = ?
                    """, owner, name, defaultBranch, Timestamp.from(now), taskId, repositoryId);
            if (updated != 1) {
                return GithubRepositoryMetadataResult.REPOSITORY_MISMATCH;
            }
        }
        jdbc.update("""
                UPDATE github_sync_task
                SET snapshot_json = snapshot_json || jsonb_build_object(
                        'owner', ?, 'name', ?, 'defaultBranch', ?), updated_at = ?
                WHERE id = ?
                """, owner, name, defaultBranch, Timestamp.from(now), taskId);
        if (ownerId == null) {
            jdbc.update("UPDATE github_sync_task SET snapshot_json = snapshot_json - 'ownerId' WHERE id = ?", taskId);
            jdbc.update("""
                    UPDATE github_discovery_candidate candidate SET github_owner_id = NULL
                    FROM github_sync_task task WHERE task.id = ? AND candidate.namespace_id = task.namespace_id
                        AND candidate.repository_id = ?
                    """, taskId, repositoryId);
            jdbc.update("""
                    UPDATE github_source_binding source SET github_owner_id = NULL
                    FROM github_sync_task task WHERE task.id = ? AND source.namespace_id = task.namespace_id
                        AND source.repository_id = ?
                    """, taskId, repositoryId);
        }
        if (ownerId != null) {
            jdbc.update("UPDATE github_sync_task SET snapshot_json = snapshot_json || jsonb_build_object('ownerId', ?::bigint) WHERE id = ?", ownerId, taskId);
            jdbc.update("""
                    UPDATE github_discovery_candidate candidate SET github_owner_id = ?, owner_login = ?,
                        repository_name = ?, default_branch = ?
                    FROM github_sync_task task WHERE task.id = ? AND candidate.namespace_id = task.namespace_id
                        AND candidate.repository_id = ?
                    """, ownerId, owner, name, defaultBranch, taskId, repositoryId);
            jdbc.update("""
                    UPDATE github_source_binding source SET github_owner_id = ?
                    FROM github_sync_task task WHERE task.id = ? AND source.namespace_id = task.namespace_id
                        AND source.repository_id = ?
                    """, ownerId, taskId, repositoryId);
        }
        return GithubRepositoryMetadataResult.UPDATED;
    }

    private record RepositoryMetadataTask(Long sourceId, long repositoryId) {}

    @Override
    public GithubPackageAdmissionResult admitPackage(UUID taskId, long leaseGeneration,
            String leaseTokenHash, String commitSha, String skillPath, Instant now) {
        jdbc.queryForList("SELECT namespace_id FROM github_platform_collection_control FOR SHARE");
        jdbc.queryForObject("SELECT enabled FROM github_deep_discovery_config WHERE id = TRUE FOR SHARE", Boolean.class);
        // This single row serializes capacity checks and reservations across all application instances.
        AdmissionPolicy policy = jdbc.query("""
                SELECT global_high, global_low, namespace_high, namespace_low, global_paused
                FROM github_sync_admission_policy WHERE id = TRUE FOR UPDATE
                """, result -> result.next() ? new AdmissionPolicy(
                (Integer) result.getObject("global_high"), (Integer) result.getObject("global_low"),
                (Integer) result.getObject("namespace_high"), (Integer) result.getObject("namespace_low"),
                result.getBoolean("global_paused")) : null);
        if (policy == null) {
            throw new IllegalStateException("GitHub sync admission policy is missing");
        }
        AdmissionTask task = jdbc.query(connection -> {
            var statement = connection.prepareStatement(LOCK_ADMISSION_TASK_SQL);
            statement.setObject(1, taskId);
            statement.setLong(2, leaseGeneration);
            statement.setString(3, leaseTokenHash);
            statement.setTimestamp(4, Timestamp.from(now));
            return statement;
        }, result -> result.next() ? new AdmissionTask(
                result.getLong("namespace_id"), (Long) result.getObject("source_id"),
                result.getString("kind"), result.getString("mode"),
                result.getString("fixed_commit_sha"), result.getString("snapshot_repository_id"),
                result.getString("snapshot_commit_sha"),
                (Long) result.getObject("source_repository_id")) : null);
        if (task == null) {
            return GithubPackageAdmissionResult.rejected(GithubPackageAdmissionResult.Status.LEASE_STALE);
        }
        if (!"REPOSITORY_SYNC".equals(task.kind()) || !"FULL".equals(task.mode())) {
            throw new IllegalArgumentException("package admission requires a FULL repository task");
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM github_platform_collection_control WHERE namespace_id=? AND paused)", Boolean.class,task.namespaceId()))) {
            return GithubPackageAdmissionResult.rejected(GithubPackageAdmissionResult.Status.BACKPRESSURE_ACTIVE);
        }
        UUID previous = jdbc.query("""
                SELECT id FROM github_sync_package_admission
                WHERE task_id = ? AND lease_generation = ? AND commit_sha = ? AND skill_path = ?
                """, statement -> {
            statement.setObject(1, taskId);
            statement.setLong(2, leaseGeneration);
            statement.setString(3, commitSha);
            statement.setString(4, skillPath);
        }, result -> result.next() ? result.getObject("id", UUID.class) : null);
        if (previous != null) {
            return new GithubPackageAdmissionResult(GithubPackageAdmissionResult.Status.ADMITTED, previous);
        }
        if (new JdbcGithubOfficialQuotaRepository(jdbc).cooldown("CORE", now) != null) {
            return GithubPackageAdmissionResult.rejected(GithubPackageAdmissionResult.Status.BACKPRESSURE_ACTIVE);
        }
        if (task.sourceId() != null) {
            String sourceStatus = jdbc.queryForObject("""
                    SELECT status FROM github_source_binding WHERE id = ? FOR UPDATE
                    """, String.class, task.sourceId());
            if ("PAUSED".equals(sourceStatus)) {
                return GithubPackageAdmissionResult.rejected(
                        GithubPackageAdmissionResult.Status.BACKPRESSURE_ACTIVE);
            }
        }
        long repositoryId;
        try {
            repositoryId = Long.parseLong(task.snapshotRepositoryId());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Repository task snapshot has no valid repository ID", exception);
        }
        Long effectiveSourceId = task.sourceId();
        if (effectiveSourceId == null) {
            effectiveSourceId = jdbc.query("""
                    SELECT id, status FROM github_source_binding
                    WHERE namespace_id = ? AND repository_id = ? FOR SHARE
                    """, result -> result.next() ? result.getLong("id") : null, task.namespaceId(), repositoryId);
            if (effectiveSourceId != null && "PAUSED".equals(jdbc.queryForObject(
                    "SELECT status FROM github_source_binding WHERE id = ?", String.class, effectiveSourceId))) {
                return GithubPackageAdmissionResult.rejected(GithubPackageAdmissionResult.Status.BACKPRESSURE_ACTIVE);
            }
        }
        if (task.sourceId() == null && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM github_candidate_discovery_eligibility eligibility
                    WHERE namespace_id = ? AND repository_id = ?
                      AND (NOT acquisition_allowed OR (official_only AND NOT EXISTS (
                          SELECT 1 FROM github_sync_task held WHERE held.id = ?
                              AND held.required_capabilities @> ARRAY['official-pause/v1']))))
                """, Boolean.class, task.namespaceId(), repositoryId, taskId))) {
            return GithubPackageAdmissionResult.rejected(GithubPackageAdmissionResult.Status.BACKPRESSURE_ACTIVE);
        }
        new RepositorySkillCoordinate(task.namespaceId(), repositoryId, skillPath);
        if (effectiveSourceId != null && ignores.ignoredForSource(effectiveSourceId, skillPath)) {
            return GithubPackageAdmissionResult.rejected(GithubPackageAdmissionResult.Status.PATH_IGNORED);
        }
        if (task.sourceRepositoryId() != null && task.sourceRepositoryId() != repositoryId) {
            throw new IllegalStateException("Repository task source and snapshot disagree");
        }
        if ((task.fixedCommitSha() != null && !task.fixedCommitSha().equals(commitSha))
                || (task.snapshotCommitSha() != null && !task.snapshotCommitSha().equals(commitSha))) {
            return GithubPackageAdmissionResult.rejected(GithubPackageAdmissionResult.Status.COMMIT_MISMATCH);
        }
        if (policy.globalHigh() == null || policy.namespaceHigh() == null) {
            return GithubPackageAdmissionResult.rejected(GithubPackageAdmissionResult.Status.BACKPRESSURE_ACTIVE);
        }
        int globalCount = capacityGate.activeCount(null, now);
        if (policy.globalPaused() && globalCount <= policy.globalLow()) {
            jdbc.update("UPDATE github_sync_admission_policy SET global_paused = FALSE, updated_at = ? WHERE id = TRUE",
                    Timestamp.from(now));
        } else if (policy.globalPaused() || globalCount >= policy.globalHigh()) {
            jdbc.update("UPDATE github_sync_admission_policy SET global_paused = TRUE, updated_at = ? WHERE id = TRUE",
                    Timestamp.from(now));
            return GithubPackageAdmissionResult.rejected(GithubPackageAdmissionResult.Status.BACKPRESSURE_ACTIVE);
        }
        int namespaceCount = capacityGate.activeCount(task.namespaceId(), now);
        jdbc.update("""
                INSERT INTO github_sync_namespace_admission_state (namespace_id)
                VALUES (?) ON CONFLICT (namespace_id) DO NOTHING
                """, task.namespaceId());
        Boolean namespacePaused = jdbc.queryForObject("""
                SELECT paused FROM github_sync_namespace_admission_state WHERE namespace_id = ?
                """, Boolean.class, task.namespaceId());
        if (Boolean.TRUE.equals(namespacePaused) && namespaceCount <= policy.namespaceLow()) {
            jdbc.update("""
                    UPDATE github_sync_namespace_admission_state SET paused = FALSE, updated_at = ?
                    WHERE namespace_id = ?
                    """, Timestamp.from(now), task.namespaceId());
        } else if (Boolean.TRUE.equals(namespacePaused) || namespaceCount >= policy.namespaceHigh()) {
            jdbc.update("""
                    UPDATE github_sync_namespace_admission_state SET paused = TRUE, updated_at = ?
                    WHERE namespace_id = ?
                    """, Timestamp.from(now), task.namespaceId());
            return GithubPackageAdmissionResult.rejected(GithubPackageAdmissionResult.Status.BACKPRESSURE_ACTIVE);
        }
        UUID admissionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO github_sync_package_admission
                    (id, task_id, namespace_id, repository_id, commit_sha, skill_path, lease_generation)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, admissionId, taskId, task.namespaceId(), repositoryId, commitSha, skillPath,
                leaseGeneration);
        jdbc.update("""
                UPDATE github_sync_task SET fixed_commit_sha = ?, updated_at = ? WHERE id = ?
                """, commitSha, Timestamp.from(now), taskId);
        if (capacityGate.activeCount(null, now) >= policy.globalHigh()) {
            jdbc.update("UPDATE github_sync_admission_policy SET global_paused = TRUE, updated_at = ? WHERE id = TRUE",
                    Timestamp.from(now));
        }
        if (capacityGate.activeCount(task.namespaceId(), now) >= policy.namespaceHigh()) {
            jdbc.update("""
                    UPDATE github_sync_namespace_admission_state
                    SET paused = TRUE, updated_at = ? WHERE namespace_id = ?
                    """, Timestamp.from(now), task.namespaceId());
        }
        return new GithubPackageAdmissionResult(GithubPackageAdmissionResult.Status.ADMITTED, admissionId);
    }

    private record AdmissionPolicy(Integer globalHigh, Integer globalLow, Integer namespaceHigh,
                                   Integer namespaceLow, boolean globalPaused) {}
    private record AdmissionTask(long namespaceId, Long sourceId, String kind, String mode,
                                 String fixedCommitSha,
                                 String snapshotRepositoryId, String snapshotCommitSha,
                                 Long sourceRepositoryId) {}

    @Override
    public boolean completeHeadOnly(UUID taskId, long leaseGeneration, String leaseTokenHash,
                                    String observedCommitSha, Instant now, Instant nextCheckAt) {
        LockedTask task = lockLease(taskId, leaseGeneration, leaseTokenHash, now);
        if (task == null) {
            return false;
        }
        if (!"REPOSITORY_SYNC".equals(task.kind()) || !"HEAD_ONLY".equals(task.mode())
                || task.sourceId() == null) {
            throw new IllegalArgumentException("HEAD_ONLY completion requires a registered repository source");
        }
        int updated = jdbc.update("""
                UPDATE github_source_binding
                SET last_observed_commit = ?, unreachable_attempts = 0, last_error_code = NULL,
                    status = CASE WHEN status = 'PAUSED' THEN status ELSE 'ACTIVE' END,
                    next_check_at = ?, updated_at = ?
                WHERE id = ?
                """, observedCommitSha, Timestamp.from(nextCheckAt), Timestamp.from(now), task.sourceId());
        if (updated != 1) {
            throw new IllegalStateException("GitHub source disappeared during task completion");
        }
        jdbc.update("""
                UPDATE github_sync_task
                SET status = 'ACQUISITION_COMPLETE', fixed_commit_sha = ?, completed_at = ?,
                    retry_at = NULL, lease_token_hash = NULL, lease_expires_at = NULL,
                    leased_by = NULL, last_error_code = NULL, last_error_detail = NULL, updated_at = ?
                WHERE id = ?
                """, observedCommitSha, Timestamp.from(now), Timestamp.from(now), taskId);
        return true;
    }

    @Override
    public Optional<Instant> fail(UUID taskId, long leaseGeneration, String leaseTokenHash,
                                  GithubSyncFailureReason reason, String detail,
                                  Instant requestedRetryAt, Instant now) {
        LockedTask task = lockLease(taskId, leaseGeneration, leaseTokenHash, now);
        if (task == null) {
            return Optional.empty();
        }
        Instant retryAt = requestedRetryAt;
        if (reason == GithubSyncFailureReason.SOURCE_UNREADABLE) {
            Integer attempts = task.sourceId() == null
                    ? jdbc.query("""
                            SELECT unreachable_attempts FROM github_discovery_candidate
                            WHERE validation_task_id = ? FOR UPDATE
                            """, result -> result.next() ? result.getInt("unreachable_attempts") : null,
                            taskId)
                    : jdbc.query("""
                            SELECT unreachable_attempts FROM github_source_binding WHERE id = ? FOR UPDATE
                            """, statement -> statement.setLong(1, task.sourceId()), result ->
                            result.next() ? result.getInt("unreachable_attempts") : null);
            if (attempts == null) {
                throw new IllegalStateException("GitHub repository source or candidate disappeared");
            }
            int nextAttempts = attempts + 1;
            Duration backoff = nextAttempts == 1 ? Duration.ofDays(1)
                    : nextAttempts == 2 ? Duration.ofDays(3) : Duration.ofDays(7);
            retryAt = now.plus(backoff);
            if (task.sourceId() == null) {
                jdbc.update("""
                        UPDATE github_discovery_candidate
                        SET unreachable_attempts = ?, last_error_code = ?, next_validation_at = ?,
                            status = CASE WHEN ? >= 3 THEN 'UNREACHABLE' ELSE status END
                        WHERE validation_task_id = ?
                        """, nextAttempts, reason.name(), Timestamp.from(retryAt),
                        nextAttempts, taskId);
            } else {
                jdbc.update("""
                        UPDATE github_source_binding
                        SET unreachable_attempts = ?, last_error_code = ?, next_check_at = ?,
                            status = CASE WHEN status = 'PAUSED' THEN status
                                          WHEN ? >= 3 THEN 'UNREACHABLE' ELSE status END,
                            updated_at = ?
                        WHERE id = ?
                        """, nextAttempts, reason.name(), Timestamp.from(retryAt),
                        nextAttempts, Timestamp.from(now), task.sourceId());
            }
        }
        if (retryAt == null) {
            retryAt = now.plus(reason == GithubSyncFailureReason.INCOMPLETE_TREE
                    ? Duration.ofMinutes(30) : Duration.ofMinutes(15));
        }
        jdbc.update("""
                UPDATE github_sync_task
                SET status = 'RETRY_WAIT', retry_at = ?, due_at = ?, last_error_code = ?,
                    last_error_detail = ?,
                    lease_token_hash = NULL, lease_expires_at = NULL, leased_by = NULL,
                    updated_at = ?
                WHERE id = ?
                """, Timestamp.from(retryAt), Timestamp.from(retryAt), reason.name(), detail,
                Timestamp.from(now), taskId);
        jdbc.update("""
                UPDATE github_sync_package_admission
                SET status = 'RELEASED', updated_at = ?
                WHERE task_id = ? AND lease_generation = ? AND status = 'ACTIVE'
                """, Timestamp.from(now), taskId, leaseGeneration);
        return Optional.of(retryAt);
    }

    @Override
    public GithubDiscoveryBatchResult recordDiscoveryBatch(UUID taskId, long leaseGeneration,
            String leaseTokenHash, GithubDiscoveryBatch batch, String requestSha256, Instant now) {
        DiscoveryLease lease = lockDiscoveryLease(taskId, leaseGeneration, leaseTokenHash, now);
        if (lease == null) {
            return GithubDiscoveryBatchResult.LEASE_STALE;
        }
        if (!List.of("SEARCH_RULE", "CURATED_OWNER", "CURATED_REPOSITORY")
                .contains(lease.entryType())) {
            return GithubDiscoveryBatchResult.RESULT_CONFLICT;
        }
        String priorDigest = jdbc.query("""
                SELECT request_sha256 FROM github_discovery_batch
                WHERE task_id = ? AND checkpoint = ?
                """, statement -> {
            statement.setObject(1, taskId);
            statement.setString(2, batch.checkpoint());
        }, result -> result.next() ? result.getString("request_sha256") : null);
        if (priorDigest != null) {
            return priorDigest.equals(requestSha256)
                    ? GithubDiscoveryBatchResult.ACCEPTED : GithubDiscoveryBatchResult.RESULT_CONFLICT;
        }
        if (!Objects.equals(lease.checkpoint(), batch.previousCheckpoint())) {
            return GithubDiscoveryBatchResult.CHECKPOINT_CONFLICT;
        }
        // Serialize retention checks across discovery tasks for this namespace.
        jdbc.queryForObject("SELECT id FROM namespace WHERE id = ? FOR UPDATE",
                Long.class, lease.namespaceId());
        if ("CURATED_REPOSITORY".equals(lease.entryType())) {
            if (batch.repositories().size() > 1 || lease.entryId() == null) {
                return GithubDiscoveryBatchResult.RESULT_CONFLICT;
            }
            String namespaceType = jdbc.queryForObject(
                    "SELECT type FROM namespace WHERE id = ?", String.class, lease.namespaceId());
            if (!"CATALOG".equals(namespaceType) && !"TEAM".equals(namespaceType)) {
                return GithubDiscoveryBatchResult.RESULT_CONFLICT;
            }
            ConfiguredRepository configured = jdbc.query("""
                    SELECT source_visibility, enabled FROM github_discovery_entry
                    WHERE id = ? AND namespace_id = ? AND entry_type = 'CURATED_REPOSITORY'
                    FOR UPDATE
                    """, result -> result.next()
                            ? new ConfiguredRepository(result.getString("source_visibility"),
                                    result.getBoolean("enabled")) : null,
                    UUID.fromString(lease.entryId()), lease.namespaceId());
            if (configured == null || "TEAM".equals(namespaceType)
                    && !List.of("PUBLIC", "NAMESPACE_ONLY").contains(configured.visibility())) {
                return GithubDiscoveryBatchResult.RESULT_CONFLICT;
            }
            Integer priorRepository = jdbc.queryForObject("""
                    SELECT count(*) FROM github_discovery_batch
                    WHERE task_id = ? AND curated_repository_id IS NOT NULL
                    """, Integer.class, taskId);
            if (!batch.repositories().isEmpty() && priorRepository != null && priorRepository > 0) {
                return GithubDiscoveryBatchResult.RESULT_CONFLICT;
            }
            for (GithubDiscoveryCandidate candidate : batch.repositories()) {
                if ("TEAM".equals(namespaceType)) {
                    Integer conflictingOrigin = jdbc.queryForObject("""
                            SELECT count(*) FROM github_source_binding source
                            JOIN github_source_origin origin ON origin.source_id = source.id
                            WHERE source.namespace_id = ? AND source.repository_id = ?
                              AND origin.origin_type = 'TEAM_CONFIG' AND origin.origin_key <> ?
                            """, Integer.class, lease.namespaceId(), candidate.repositoryId(),
                            lease.entryId());
                    if (conflictingOrigin != null && conflictingOrigin > 0) {
                        return GithubDiscoveryBatchResult.RESULT_CONFLICT;
                    }
                }
                int pinned = jdbc.update("""
                        UPDATE github_discovery_entry
                        SET repository_id = COALESCE(repository_id, ?), updated_at = ?
                        WHERE id = ? AND namespace_id = ? AND entry_type = 'CURATED_REPOSITORY'
                          AND (repository_id IS NULL OR repository_id = ?)
                        """, candidate.repositoryId(), Timestamp.from(now),
                        UUID.fromString(lease.entryId()), lease.namespaceId(), candidate.repositoryId());
                if (pinned != 1) {
                    return GithubDiscoveryBatchResult.RESULT_CONFLICT;
                }
                Long sourceId = jdbc.query(connection -> {
                    var statement = connection.prepareStatement("""
                        INSERT INTO github_source_binding
                            (namespace_id, repository_id, owner_login, repository_name,
                             default_branch, visibility, status, next_check_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (namespace_id, repository_id) DO UPDATE
                        SET owner_login = EXCLUDED.owner_login,
                            repository_name = EXCLUDED.repository_name,
                            default_branch = EXCLUDED.default_branch,
                            updated_at = CURRENT_TIMESTAMP
                        RETURNING id
                        """);
                    statement.setLong(1, lease.namespaceId());
                    statement.setLong(2, candidate.repositoryId());
                    statement.setString(3, candidate.owner());
                    statement.setString(4, candidate.name());
                    statement.setString(5, candidate.defaultBranch());
                    statement.setString(6, "CATALOG".equals(namespaceType)
                            ? "PUBLIC" : configured.visibility());
                    statement.setString(7, "TEAM".equals(namespaceType) && !configured.enabled()
                            ? "PAUSED" : "ACTIVE");
                    statement.setTimestamp(8, Timestamp.from(now));
                    return statement;
                }, result -> result.next() ? result.getLong("id") : null);
                if (sourceId == null) {
                    throw new IllegalStateException("Could not persist curated GitHub repository");
                }
                jdbc.update("""
                        INSERT INTO github_source_origin (source_id, origin_type, origin_key)
                        VALUES (?, ?, ?)
                        ON CONFLICT (source_id, origin_type, origin_key) DO NOTHING
                        """, sourceId,
                        "TEAM".equals(namespaceType) ? "TEAM_CONFIG" : "CURATED_REPOSITORY",
                        lease.entryId());
            }
            Long repositoryId = batch.repositories().isEmpty()
                    ? null : batch.repositories().get(0).repositoryId();
            recordDiscoveryCheckpoint(taskId, batch, requestSha256, now, repositoryId);
            return GithubDiscoveryBatchResult.ACCEPTED;
        }
        var previousHits = new HashSet<>(jdbc.queryForList(
                "SELECT repository_id FROM github_discovery_task_hit WHERE task_id = ?",
                Long.class, taskId));
        long newCount = batch.repositories().stream()
                .filter(candidate -> !previousHits.contains(candidate.repositoryId())).count();
        Integer leaseHits = jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_task_hit
                WHERE task_id = ? AND lease_generation = ?
                """, Integer.class, taskId, leaseGeneration);
        Integer pendingCount = jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_candidate
                WHERE namespace_id = ? AND status IN ('PENDING', 'VALIDATING')
                """, Integer.class, lease.namespaceId());
        Integer pendingLimit = jdbc.queryForObject("""
                SELECT candidate_pending_limit FROM github_sync_admission_policy WHERE id = TRUE
                """, Integer.class);
        int newPendingCount = 0;
        for (GithubDiscoveryCandidate candidate : batch.repositories()) {
            String existingStatus = jdbc.query("""
                    SELECT status FROM github_discovery_candidate
                    WHERE namespace_id = ? AND repository_id = ?
                    """, statement -> {
                statement.setLong(1, lease.namespaceId());
                statement.setLong(2, candidate.repositoryId());
            }, result -> result.next() ? result.getString("status") : null);
            Integer existingSource = jdbc.queryForObject("""
                    SELECT count(*) FROM github_source_binding
                    WHERE namespace_id = ? AND repository_id = ?
                    """, Integer.class, lease.namespaceId(), candidate.repositoryId());
            if (existingStatus == null && existingSource == 0) {
                newPendingCount++;
            }
        }
        if (newCount > 0 && (leaseHits == null ? 0 : leaseHits) + newCount > lease.budget()
                || newPendingCount > 0
                && (pendingCount == null ? 0 : pendingCount) + newPendingCount > pendingLimit) {
            return GithubDiscoveryBatchResult.BUDGET_EXCEEDED;
        }
        var createdRepositories = new java.util.ArrayList<Long>();
        for (GithubDiscoveryCandidate candidate : batch.repositories().stream()
                .sorted(Comparator.comparingLong(GithubDiscoveryCandidate::repositoryId)).toList()) {
            Long candidateId = jdbc.query(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO github_discovery_candidate
                            (namespace_id, repository_id, owner_login, repository_name,
                             default_branch, first_seen_at, last_seen_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (namespace_id, repository_id) DO NOTHING
                        RETURNING id
                        """);
                statement.setLong(1, lease.namespaceId());
                statement.setLong(2, candidate.repositoryId());
                statement.setString(3, candidate.owner());
                statement.setString(4, candidate.name());
                statement.setString(5, candidate.defaultBranch());
                statement.setTimestamp(6, Timestamp.from(now));
                statement.setTimestamp(7, Timestamp.from(now));
                return statement;
            }, result -> result.next() ? result.getLong("id") : null);
            if (candidateId != null) {
                createdRepositories.add(candidate.repositoryId());
            } else {
                candidateId = jdbc.queryForObject("""
                        UPDATE github_discovery_candidate SET owner_login = ?, repository_name = ?, default_branch = ?,
                            last_seen_at = ? WHERE namespace_id = ? AND repository_id = ? RETURNING id
                        """, Long.class, candidate.owner(), candidate.name(), candidate.defaultBranch(), Timestamp.from(now),
                        lease.namespaceId(), candidate.repositoryId());
            }
            jdbc.update("""
                    INSERT INTO github_discovery_task_hit
                        (task_id, repository_id, candidate_id, lease_generation, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (task_id, repository_id) DO NOTHING
                    """, taskId, candidate.repositoryId(), candidateId,
                    leaseGeneration, Timestamp.from(now));
        }
        jdbc.update("""
                INSERT INTO github_discovery_candidate_origin
                    (candidate_id, origin_type, origin_key, first_seen_at, last_seen_at)
                SELECT hit.candidate_id, task.snapshot_json ->> 'entryType',
                       CASE WHEN task.snapshot_json ->> 'entryType' = 'CURATED_OWNER'
                            THEN task.snapshot_json ->> 'owner'
                            ELSE task.snapshot_json ->> 'ruleId' END,
                       ?, ?
                FROM github_discovery_task_hit hit
                JOIN github_sync_task task ON task.id = hit.task_id
                WHERE hit.task_id = ?
                  AND task.snapshot_json ->> 'entryType' IN ('SEARCH_RULE', 'CURATED_OWNER')
                  AND CASE WHEN task.snapshot_json ->> 'entryType' = 'CURATED_OWNER'
                           THEN task.snapshot_json ->> 'owner'
                           ELSE task.snapshot_json ->> 'ruleId' END IS NOT NULL
                ON CONFLICT (candidate_id, origin_type, origin_key) DO UPDATE
                SET last_seen_at = GREATEST(github_discovery_candidate_origin.last_seen_at,
                                           EXCLUDED.last_seen_at)
                """, Timestamp.from(now), Timestamp.from(now), taskId);
        createdRepositories.forEach(repositoryId -> outcomes.candidateCreated(lease.namespaceId(), repositoryId, now));
        jdbc.update("""
                UPDATE github_discovery_candidate candidate
                SET status = 'BOUND', last_error_code = NULL
                WHERE candidate.namespace_id = ?
                  AND candidate.id IN (
                      SELECT hit.candidate_id FROM github_discovery_task_hit hit
                      WHERE hit.task_id = ?)
                  AND EXISTS (SELECT 1 FROM github_source_binding source
                      WHERE source.namespace_id = candidate.namespace_id
                        AND source.repository_id = candidate.repository_id)
                """, lease.namespaceId(), taskId);
        jdbc.update("""
                INSERT INTO github_source_origin (source_id, origin_type, origin_key)
                SELECT source.id, origin.origin_type, origin.origin_key
                FROM github_discovery_task_hit hit
                JOIN github_discovery_candidate candidate ON candidate.id = hit.candidate_id
                JOIN github_source_binding source
                  ON source.namespace_id = candidate.namespace_id
                 AND source.repository_id = candidate.repository_id
                JOIN github_discovery_candidate_origin origin ON origin.candidate_id = candidate.id
                WHERE hit.task_id = ?
                ON CONFLICT (source_id, origin_type, origin_key) DO NOTHING
                """, taskId);
        recordDiscoveryCheckpoint(taskId, batch, requestSha256, now, null);
        return GithubDiscoveryBatchResult.ACCEPTED;
    }

    private void recordDiscoveryCheckpoint(UUID taskId, GithubDiscoveryBatch batch,
                                           String requestSha256, Instant now, Long curatedRepositoryId) {
        jdbc.update("""
                INSERT INTO github_discovery_batch
                    (task_id, checkpoint, request_sha256, deferred_count, deferral_reason,
                     curated_repository_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, taskId, batch.checkpoint(), requestSha256, batch.deferredCount(),
                batch.deferralReason() == null ? null : batch.deferralReason().name(),
                curatedRepositoryId, Timestamp.from(now));
        jdbc.update("""
                UPDATE github_sync_task SET discovery_checkpoint = ?, updated_at = ? WHERE id = ?
                """, batch.checkpoint(), Timestamp.from(now), taskId);
    }


    @Override
    public GithubDiscoveryCompletionResult completeDiscovery(UUID taskId, long leaseGeneration,
            String leaseTokenHash, String checkpoint, boolean complete,
            Instant retryAt, GithubSyncDeferralReason deferralReason, Instant now) {
        DiscoveryLease lease = lockDiscoveryLease(taskId, leaseGeneration, leaseTokenHash, now);
        if (lease == null) {
            return GithubDiscoveryCompletionResult.LEASE_STALE;
        }
        if (!List.of("SEARCH_RULE", "CURATED_OWNER", "CURATED_REPOSITORY")
                .contains(lease.entryType())) {
            return GithubDiscoveryCompletionResult.CHECKPOINT_CONFLICT;
        }
        if (!Objects.equals(lease.checkpoint(), checkpoint)) {
            return GithubDiscoveryCompletionResult.CHECKPOINT_CONFLICT;
        }
        Integer persisted = jdbc.queryForObject("""
                SELECT count(*) FROM github_discovery_batch WHERE task_id = ? AND checkpoint = ?
                """, Integer.class, taskId, checkpoint);
        if (persisted == null || persisted == 0) {
            return GithubDiscoveryCompletionResult.CHECKPOINT_CONFLICT;
        }
        if (complete) {
            jdbc.update("""
                    UPDATE github_sync_task
                    SET status = 'ACQUISITION_COMPLETE', completed_at = ?, retry_at = NULL,
                        lease_token_hash = NULL, lease_expires_at = NULL, leased_by = NULL,
                        last_error_code = NULL, last_error_detail = NULL, updated_at = ?
                    WHERE id = ?
                    """, Timestamp.from(now), Timestamp.from(now), taskId);
            return GithubDiscoveryCompletionResult.ACQUISITION_COMPLETE;
        }
        jdbc.update("""
                UPDATE github_sync_task
                SET status = 'RETRY_WAIT', retry_at = ?, due_at = ?,
                    last_error_code = ?, last_error_detail = NULL,
                    lease_token_hash = NULL, lease_expires_at = NULL,
                    leased_by = NULL, updated_at = ?
                WHERE id = ?
                """, Timestamp.from(retryAt), Timestamp.from(retryAt),
                deferralReason == null ? "DISCOVERY_INCOMPLETE" : deferralReason.name(),
                Timestamp.from(now), taskId);
        return GithubDiscoveryCompletionResult.RETRY_WAIT;
    }

    @Override
    public boolean completePreview(UUID taskId, long leaseGeneration, String leaseTokenHash,
                                   String resultJson, Instant now) {
        int updated = jdbc.update("""
                UPDATE github_sync_task
                SET snapshot_json = jsonb_set(snapshot_json, '{previewResult}', ?::jsonb),
                    status = 'ACQUISITION_COMPLETE', completed_at = ?, retry_at = NULL,
                    lease_token_hash = NULL, lease_expires_at = NULL, leased_by = NULL,
                    last_error_code = NULL, last_error_detail = NULL, updated_at = ?
                WHERE id = ? AND kind = 'DISCOVERY' AND status = 'LEASED'
                  AND snapshot_json ->> 'entryType' = 'SEARCH_PREVIEW'
                  AND lease_generation = ? AND lease_token_hash = ? AND lease_expires_at > CASE WHEN official_query_id IS NOT NULL THEN clock_timestamp() ELSE ? END
                """, resultJson, Timestamp.from(now), Timestamp.from(now), taskId,
                leaseGeneration, leaseTokenHash, Timestamp.from(now));
        if (updated == 1) {
            jdbc.update("""
                    UPDATE github_sync_run SET status = 'ACQUISITION_COMPLETE', finished_at = ?
                    WHERE id = (SELECT run_id FROM github_sync_task WHERE id = ?)
                    """, Timestamp.from(now), taskId);
        }
        return updated == 1;
    }

    @Override
    public GithubRepositoryCompletionResult completeRepository(UUID taskId, long leaseGeneration,
            String leaseTokenHash, String commitSha, boolean complete, String pathCheckpoint,
            List<String> incompleteSubtrees, List<GithubRepositoryPathResult> pathResults,
            Instant retryAt, GithubSyncDeferralReason deferralReason, Instant now, Instant nextCheckAt) {
        RepositoryLease lease = jdbc.query(connection -> {
            var statement = connection.prepareStatement(LOCK_REPOSITORY_LEASE_SQL);
            statement.setObject(1, taskId);
            statement.setLong(2, leaseGeneration);
            statement.setString(3, leaseTokenHash);
            statement.setTimestamp(4, Timestamp.from(now));
            return statement;
        }, result -> result.next() ? new RepositoryLease((Long) result.getObject("source_id"),
                result.getString("fixed_commit_sha"), result.getString("snapshot_commit_sha"),
                result.getString("path_checkpoint")) : null);
        if (lease == null) {
            return GithubRepositoryCompletionResult.LEASE_STALE;
        }
        if (lease.fixedCommitSha() != null && !lease.fixedCommitSha().equals(commitSha)
                || lease.snapshotCommitSha() != null && !lease.snapshotCommitSha().equals(commitSha)) {
            return GithubRepositoryCompletionResult.COMMIT_MISMATCH;
        }
        if (lease.pathCheckpoint() != null && pathCheckpoint != null
                && comparePathCheckpoints(pathCheckpoint, lease.pathCheckpoint()) < 0) {
            return GithubRepositoryCompletionResult.CHECKPOINT_CONFLICT;
        }
        Map<String, PathResult> persistedPaths = new HashMap<>();
        jdbc.query("""
                SELECT skill_path, outcome, ingestion_id, reason FROM github_sync_path_result
                WHERE task_id = ? AND commit_sha = ? FOR UPDATE
                """, (result, rowNumber) -> Map.entry(result.getString("skill_path"),
                new PathResult(result.getString("outcome"),
                        result.getObject("ingestion_id", UUID.class), result.getString("reason"))),
                taskId, commitSha).forEach(entry -> persistedPaths.put(entry.getKey(), entry.getValue()));
        for (GithubRepositoryPathResult path : pathResults) {
            PathResult persisted = persistedPaths.get(path.skillPath());
            if (path.outcome() == GithubRepositoryPathResult.Outcome.ACCEPTED) {
                if (persisted == null || !"ACCEPTED".equals(persisted.outcome())
                        || !path.ingestionId().equals(persisted.ingestionId())) {
                    return GithubRepositoryCompletionResult.RESULT_CONFLICT;
                }
                Integer ingestion = jdbc.queryForObject("""
                        SELECT count(*) FROM github_sync_package_ingestion
                        WHERE id = ? AND task_id = ? AND commit_sha = ? AND skill_path = ?
                          AND status NOT IN ('FAILED', 'NO_CHANGE')
                        """, Integer.class, path.ingestionId(), taskId, commitSha, path.skillPath());
                if (ingestion == null || ingestion != 1) {
                    return GithubRepositoryCompletionResult.RESULT_CONFLICT;
                }
            } else if (persisted != null && !"RETRYABLE_FAILURE".equals(persisted.outcome())
                    && (!persisted.outcome().equals(path.outcome().name())
                    || !Objects.equals(persisted.reason(), path.reason()))) {
                return GithubRepositoryCompletionResult.RESULT_CONFLICT;
            }
        }
        if (complete) {
            var resolvedPaths = new HashSet<String>();
            var failedAdmissions = new HashSet<String>();
            for (GithubRepositoryPathResult path : pathResults) {
                if (path.outcome() != GithubRepositoryPathResult.Outcome.RETRYABLE_FAILURE) {
                    resolvedPaths.add(path.skillPath());
                }
                if (path.outcome() == GithubRepositoryPathResult.Outcome.TERMINAL_FAILURE
                        || path.outcome() == GithubRepositoryPathResult.Outcome.IGNORED) {
                    failedAdmissions.add(path.skillPath());
                }
            }
            for (Map.Entry<String, PathResult> persisted : persistedPaths.entrySet()) {
                if ("RETRYABLE_FAILURE".equals(persisted.getValue().outcome())
                        && !resolvedPaths.contains(persisted.getKey())) {
                    return GithubRepositoryCompletionResult.COVERAGE_CONFLICT;
                }
            }
            List<String> activePaths = jdbc.query("""
                    SELECT skill_path FROM github_sync_package_admission
                    WHERE task_id = ? AND lease_generation = ? AND status = 'ACTIVE'
                    FOR UPDATE
                    """, (result, row) -> result.getString("skill_path"), taskId, leaseGeneration);
            if (activePaths.stream().anyMatch(path -> !failedAdmissions.contains(path))) {
                return GithubRepositoryCompletionResult.COVERAGE_CONFLICT;
            }
            for (String path : activePaths) {
                jdbc.update("""
                        UPDATE github_sync_package_admission
                        SET status = 'RELEASED', updated_at = ?
                        WHERE task_id = ? AND lease_generation = ? AND skill_path = ? AND status = 'ACTIVE'
                        """, Timestamp.from(now), taskId, leaseGeneration, path);
            }
        }
        for (GithubRepositoryPathResult path : pathResults) {
            if (path.outcome() == GithubRepositoryPathResult.Outcome.ACCEPTED) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO github_sync_path_result
                        (task_id, commit_sha, skill_path, outcome, reason, retry_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (task_id, commit_sha, skill_path) DO UPDATE
                    SET outcome = EXCLUDED.outcome, reason = EXCLUDED.reason,
                        retry_at = EXCLUDED.retry_at, updated_at = CURRENT_TIMESTAMP
                    WHERE github_sync_path_result.outcome = 'RETRYABLE_FAILURE'
                    """, taskId, commitSha, path.skillPath(), path.outcome().name(),
                    path.reason(), path.outcome() == GithubRepositoryPathResult.Outcome.RETRYABLE_FAILURE
                            ? Timestamp.from(retryAt) : null);
        }
        String subtreesJson;
        try {
            subtreesJson = objectMapper.writeValueAsString(incompleteSubtrees);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize incomplete GitHub subtrees", exception);
        }
        if (complete) {
            Integer terminal = jdbc.queryForObject("""
                    SELECT count(*) FROM github_sync_path_result
                    WHERE task_id = ? AND commit_sha = ? AND outcome = 'TERMINAL_FAILURE'
                    """, Integer.class, taskId, commitSha);
            String status = terminal != null && terminal > 0 ? "PARTIAL" : "ACQUISITION_COMPLETE";
            if (lease.sourceId() != null) {
                int updated = jdbc.update("""
                        UPDATE github_source_binding source
                        SET last_observed_commit = ?,
                            last_partial_commit = CASE
                                WHEN ? = 'PARTIAL'
                                     AND COALESCE(task.snapshot_json -> 'ignorePatterns', '[]'::jsonb)
                                         = source.ignore_patterns
                                THEN ? ELSE NULL END,
                            unreachable_attempts = 0, last_error_code = NULL,
                            status = CASE WHEN source.status = 'PAUSED' THEN source.status ELSE 'ACTIVE' END,
                            next_check_at = CASE
                                WHEN COALESCE(task.snapshot_json -> 'ignorePatterns', '[]'::jsonb)
                                     = source.ignore_patterns THEN ?::timestamptz ELSE ?::timestamptz END,
                            updated_at = ?
                        FROM github_sync_task task
                        WHERE source.id = ? AND task.id = ?
                        """, commitSha, status, commitSha, Timestamp.from(nextCheckAt),
                        Timestamp.from(now), Timestamp.from(now), lease.sourceId(), taskId);
                if (updated != 1) {
                    throw new IllegalStateException("GitHub source disappeared during repository completion");
                }
            }
            jdbc.update("""
                    UPDATE github_sync_task
                    SET status = ?, fixed_commit_sha = ?, path_checkpoint = COALESCE(?, path_checkpoint),
                        incomplete_subtrees = '[]'::jsonb, completed_at = ?, retry_at = NULL,
                        lease_token_hash = NULL, lease_expires_at = NULL, leased_by = NULL,
                        last_error_code = ?, last_error_detail = NULL, updated_at = ?
                    WHERE id = ?
                    """, status, commitSha, pathCheckpoint, Timestamp.from(now),
                    terminal != null && terminal > 0 ? "PATH_TERMINAL_FAILURE" : null,
                    Timestamp.from(now), taskId);
            // A publication deferred while the tree was still being scanned can be decided now.
            jdbc.update("""
                    UPDATE github_sync_package_ingestion
                    SET publication_retry_at = ?, updated_at = ?
                    WHERE task_id = ? AND status = 'VALIDATED'
                      AND last_error_code = 'PATH_MOVE_SCAN_INCOMPLETE'
                      AND publication_token IS NULL AND publication_retry_at > ?
                    """, Timestamp.from(now), Timestamp.from(now), taskId, Timestamp.from(now));
            if ("ACQUISITION_COMPLETE".equals(status)) {
                if (lease.sourceId() != null) {
                    presence.reconcile(lease.sourceId(), taskId, commitSha, now);
                    checkpoints.captureRepositorySnapshot(taskId, commitSha, now);
                }
                checkpoints.advanceIfComplete(taskId, commitSha, now);
            }
            return terminal != null && terminal > 0
                    ? GithubRepositoryCompletionResult.PARTIAL
                    : GithubRepositoryCompletionResult.ACQUISITION_COMPLETE;
        }
        jdbc.update("""
                UPDATE github_sync_task
                SET status = 'RETRY_WAIT', fixed_commit_sha = ?,
                    path_checkpoint = COALESCE(?, path_checkpoint),
                    incomplete_subtrees = ?::jsonb, retry_at = ?, due_at = ?,
                    last_error_code = ?, last_error_detail = NULL,
                    lease_token_hash = NULL, lease_expires_at = NULL,
                    leased_by = NULL, updated_at = ?
                WHERE id = ?
                """, commitSha, pathCheckpoint, subtreesJson, Timestamp.from(retryAt),
                Timestamp.from(retryAt), deferralReason == null ? "REPOSITORY_INCOMPLETE"
                        : deferralReason.name(), Timestamp.from(now), taskId);
        jdbc.update("""
                UPDATE github_sync_package_admission
                SET status = 'RELEASED', updated_at = ?
                WHERE task_id = ? AND lease_generation = ? AND status = 'ACTIVE'
                """, Timestamp.from(now), taskId, leaseGeneration);
        if (lease.sourceId() != null) {
            jdbc.update("""
                    UPDATE github_source_binding SET last_observed_commit = ?, updated_at = ? WHERE id = ?
                    """, commitSha, Timestamp.from(now), lease.sourceId());
        }
        return GithubRepositoryCompletionResult.RETRY_WAIT;
    }

    private record RepositoryLease(Long sourceId, String fixedCommitSha, String snapshotCommitSha,
                                   String pathCheckpoint) {}
    private record PathResult(String outcome, UUID ingestionId, String reason) {}

    // Go sorts UTF-8 paths by code point. String.compareTo uses UTF-16 code units and can
    // disagree when a path contains supplementary Unicode characters.
    private int comparePathCheckpoints(String left, String right) {
        int leftOffset = 0;
        int rightOffset = 0;
        while (leftOffset < left.length() && rightOffset < right.length()) {
            int leftCodePoint = left.codePointAt(leftOffset);
            int rightCodePoint = right.codePointAt(rightOffset);
            if (leftCodePoint != rightCodePoint) {
                return Integer.compare(leftCodePoint, rightCodePoint);
            }
            leftOffset += Character.charCount(leftCodePoint);
            rightOffset += Character.charCount(rightCodePoint);
        }
        return Integer.compare(left.length() - leftOffset, right.length() - rightOffset);
    }

    private DiscoveryLease lockDiscoveryLease(UUID taskId, long leaseGeneration,
            String leaseTokenHash, Instant now) {
        return jdbc.query(connection -> {
            var statement = connection.prepareStatement(LOCK_DISCOVERY_LEASE_SQL);
            statement.setObject(1, taskId);
            statement.setLong(2, leaseGeneration);
            statement.setString(3, leaseTokenHash);
            statement.setTimestamp(4, Timestamp.from(now));
            return statement;
        }, result -> {
            if (!result.next()) {
                return null;
            }
            String budgetText = result.getString("candidate_budget");
            if (budgetText == null) {
                throw new IllegalStateException("Discovery task has no candidate budget");
            }
            int budget;
            try {
                budget = Integer.parseInt(budgetText);
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("Discovery task has an invalid candidate budget", exception);
            }
            if (budget < 1) {
                throw new IllegalStateException("Discovery candidate budget must be positive");
            }
            return new DiscoveryLease(result.getLong("namespace_id"),
                    result.getString("discovery_checkpoint"), budget,
                    result.getString("entry_type"), result.getString("entry_id"));
        });
    }

    private record DiscoveryLease(long namespaceId, String checkpoint, int budget,
                                  String entryType, String entryId) {}

    private record ConfiguredRepository(String visibility, boolean enabled) {}

    private LockedTask lockLease(UUID taskId, long generation, String tokenHash, Instant now) {
        return jdbc.query(connection -> {
            var statement = connection.prepareStatement(LOCK_LEASE_SQL);
            statement.setObject(1, taskId);
            statement.setLong(2, generation);
            statement.setString(3, tokenHash);
            statement.setTimestamp(4, Timestamp.from(now));
            return statement;
        }, result -> {
            if (!result.next()) {
                return null;
            }
            long sourceId = result.getLong("source_id");
            return new LockedTask(result.wasNull() ? null : sourceId,
                    result.getString("kind"), result.getString("mode"));
        });
    }

    private record LockedTask(Long sourceId, String kind, String mode) {}

    private GithubSyncTaskLease mapLease(ResultSet result) throws SQLException {
        long sourceId = result.getLong("source_id");
        Long nullableSourceId = result.wasNull() ? null : sourceId;
        String mode = result.getString("mode");
        return new GithubSyncTaskLease(
                result.getObject("id", UUID.class),
                result.getObject("run_id", UUID.class),
                GithubSyncTaskKind.valueOf(result.getString("kind")),
                mode == null ? null : GithubSyncTaskMode.valueOf(mode),
                result.getLong("namespace_id"),
                nullableSourceId,
                result.getString("snapshot_json"),
                result.getString("discovery_checkpoint"),
                result.getString("fixed_commit_sha"),
                result.getString("path_checkpoint"),
                result.getString("incomplete_subtrees"),
                List.of((String[]) result.getArray("retry_paths").getArray()),
                List.of((String[]) result.getArray("accepted_paths").getArray()),
                result.getLong("lease_generation"),
                result.getTimestamp("lease_expires_at").toInstant(),
                List.of((String[]) result.getArray("required_capabilities").getArray()),
                result.getObject("platform_control_version",Long.class));
    }
}
