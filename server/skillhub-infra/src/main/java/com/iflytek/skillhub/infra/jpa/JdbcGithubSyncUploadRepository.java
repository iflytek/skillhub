package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.githubsync.GithubSyncUploadRepository;
import com.iflytek.skillhub.githubsync.GithubPackageReportResult;
import com.iflytek.skillhub.githubsync.GithubSourceTag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcGithubSyncUploadRepository implements GithubSyncUploadRepository {
    private final JdbcTemplate jdbc;

    public JdbcGithubSyncUploadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public boolean issue(UUID taskId, long generation, String leaseTokenHash, UUID admissionId,
            String commitSha, String skillPath, UUID uploadId, String objectKey,
            String mode, String ticketHash, long sizeBytes, String sha256, Instant expiresAt, Instant now) {
        Integer valid = jdbc.query("""
                SELECT 1 FROM github_sync_package_admission admission
                JOIN github_sync_task task ON task.id = admission.task_id
                WHERE admission.id = ? AND admission.task_id = ? AND admission.lease_generation = ?
                  AND admission.commit_sha = ? AND admission.skill_path = ? AND admission.status = 'ACTIVE'
                  AND task.status = 'LEASED' AND task.lease_generation = ?
                  AND task.lease_token_hash = ? AND task.lease_expires_at > ?
                FOR UPDATE OF task, admission
                """, statement -> {
            statement.setObject(1, admissionId);
            statement.setObject(2, taskId);
            statement.setLong(3, generation);
            statement.setString(4, commitSha);
            statement.setString(5, skillPath);
            statement.setLong(6, generation);
            statement.setString(7, leaseTokenHash);
            statement.setTimestamp(8, Timestamp.from(now));
        }, result -> result.next() ? 1 : null);
        if (valid == null) {
            return false;
        }
        jdbc.update("""
                INSERT INTO github_sync_staged_upload
                    (id, admission_id, object_key, mode, ticket_hash, size_bytes, sha256, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, uploadId, admissionId, objectKey, mode, ticketHash,
                sizeBytes, sha256, Timestamp.from(expiresAt));
        return true;
    }

    @Override
    @Transactional
    public Optional<LocalUpload> beginLocalUpload(UUID uploadId, String ticketHash, Instant now) {
        LocalUpload upload = jdbc.query("""
                SELECT upload.object_key, upload.size_bytes, upload.sha256
                FROM github_sync_staged_upload upload
                JOIN github_sync_package_admission admission ON admission.id = upload.admission_id
                JOIN github_sync_task task ON task.id = admission.task_id
                WHERE upload.id = ? AND upload.mode = 'LOCAL_STREAM' AND upload.status = 'ISSUED'
                  AND upload.ticket_hash = ? AND upload.expires_at > ?
                  AND admission.status = 'ACTIVE' AND task.status = 'LEASED'
                  AND task.lease_generation = admission.lease_generation AND task.lease_expires_at > ?
                FOR UPDATE OF upload
                """, statement -> {
            statement.setObject(1, uploadId);
            statement.setString(2, ticketHash);
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setTimestamp(4, Timestamp.from(now));
        }, result -> result.next() ? new LocalUpload(result.getString("object_key"),
                result.getLong("size_bytes"), result.getString("sha256")) : null);
        if (upload == null) {
            return Optional.empty();
        }
        jdbc.update("""
                UPDATE github_sync_staged_upload SET status = 'UPLOADING', updated_at = ? WHERE id = ?
                """, Timestamp.from(now), uploadId);
        return Optional.of(upload);
    }

    @Override
    @Transactional
    public boolean finishLocalUpload(UUID uploadId, Instant now) {
        return jdbc.update("""
                UPDATE github_sync_staged_upload AS upload
                SET status = 'UPLOADED', updated_at = ?
                FROM github_sync_package_admission admission, github_sync_task task
                WHERE upload.id = ? AND upload.status = 'UPLOADING'
                  AND admission.id = upload.admission_id AND admission.status = 'ACTIVE'
                  AND task.id = admission.task_id AND task.status = 'LEASED'
                  AND task.lease_generation = admission.lease_generation AND task.lease_expires_at > ?
                """, Timestamp.from(now), uploadId, Timestamp.from(now)) == 1;
    }

    @Override
    public boolean failUpload(UUID uploadId, Instant now) {
        return jdbc.update("""
                UPDATE github_sync_staged_upload SET status = 'FAILED', updated_at = ?
                WHERE id = ? AND status IN ('ISSUED', 'UPLOADING', 'UPLOADED')
                """, Timestamp.from(now), uploadId) == 1;
    }

    @Override
    @Transactional
    public List<CleanupUpload> claimCleanup(Instant now, Duration signedUrlGrace, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("cleanup batch limit must be between 1 and 100");
        }
        return jdbc.query("""
                WITH picked AS (
                    SELECT upload.id FROM github_sync_staged_upload upload
                    JOIN github_sync_package_admission admission ON admission.id = upload.admission_id
                    JOIN github_sync_task task ON task.id = admission.task_id
                    WHERE upload.status IN ('ISSUED', 'UPLOADING', 'UPLOADED', 'FAILED', 'DELETING')
                      AND (upload.mode = 'LOCAL_STREAM' OR upload.expires_at < ?)
                      AND (
                          (upload.status = 'FAILED')
                          OR (upload.status = 'DELETING' AND upload.updated_at < ?)
                          OR (upload.status IN ('ISSUED', 'UPLOADING', 'UPLOADED')
                              AND (task.status <> 'LEASED' OR task.lease_generation <> admission.lease_generation
                                   OR task.lease_expires_at <= ?)
                              AND (upload.status <> 'UPLOADING' OR upload.updated_at < ?))
                      )
                    ORDER BY upload.expires_at, upload.id
                    LIMIT ? FOR UPDATE OF upload SKIP LOCKED
                )
                UPDATE github_sync_staged_upload upload
                SET status = 'DELETING', updated_at = ?
                FROM picked WHERE upload.id = picked.id
                RETURNING upload.id, upload.object_key
                """, statement -> {
            statement.setTimestamp(1, Timestamp.from(now.minus(signedUrlGrace)));
            statement.setTimestamp(2, Timestamp.from(now.minus(Duration.ofMinutes(15))));
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setTimestamp(4, Timestamp.from(now.minus(Duration.ofHours(1))));
            statement.setInt(5, limit);
            statement.setTimestamp(6, Timestamp.from(now));
        }, (result, row) -> new CleanupUpload(result.getObject("id", UUID.class),
                result.getString("object_key")));
    }

    @Override
    public void markCleaned(UUID uploadId, Instant now) {
        jdbc.update("""
                UPDATE github_sync_staged_upload
                SET status = 'CLEANED', updated_at = ? WHERE id = ? AND status = 'DELETING'
                """, Timestamp.from(now), uploadId);
    }

    @Override
    public GithubPackageReportResult lookupReport(UUID taskId, long generation, String leaseTokenHash,
            UUID admissionId, String commitSha, String skillPath, UUID uploadId,
            long sizeBytes, String sha256, Instant now) {
        return readReport(taskId, generation, leaseTokenHash, admissionId, commitSha, skillPath,
                uploadId, sizeBytes, sha256, now, false);
    }

    @Override
    @Transactional
    public GithubPackageReportResult acceptReport(UUID taskId, long generation, String leaseTokenHash,
            UUID admissionId, String commitSha, String skillPath, UUID uploadId,
            long sizeBytes, String sha256, GithubSourceTag sourceTag, Instant now) {
        GithubPackageReportResult checked = readReport(taskId, generation, leaseTokenHash,
                admissionId, commitSha, skillPath, uploadId, sizeBytes, sha256, now, true);
        if (checked.status() != GithubPackageReportResult.Status.READY_TO_CHECK_OBJECT) {
            return checked;
        }
        ReportCoordinate coordinate = jdbc.query("""
                SELECT namespace_id, repository_id FROM github_sync_package_admission WHERE id = ?
                """, statement -> statement.setObject(1, admissionId), result ->
                result.next() ? new ReportCoordinate(result.getLong("namespace_id"),
                        result.getLong("repository_id")) : null);
        if (coordinate == null) {
            throw new IllegalStateException("GitHub sync package admission disappeared");
        }
        UUID ingestionId = UUID.randomUUID();
        // A null source tag means "not recorded"; the stored value is never updated afterwards.
        boolean recorded = sourceTag != null;
        jdbc.update("""
                INSERT INTO github_sync_package_ingestion
                    (id, task_id, admission_id, upload_id, namespace_id, repository_id,
                     commit_sha, skill_path, object_key, size_bytes, sha256, source_tag_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        CASE WHEN ? THEN jsonb_strip_nulls(jsonb_build_object(
                            'schemaVersion', ?::integer, 'status', ?::text, 'tag', ?::text,
                            'tagCommitSha', ?::text, 'commitsAfterTag', ?::integer,
                            'reason', ?::text)) END)
                """, ingestionId, taskId, admissionId, uploadId, coordinate.namespaceId(),
                coordinate.repositoryId(), commitSha, skillPath, checked.objectKey(), sizeBytes, sha256,
                recorded, GithubSourceTag.SCHEMA_VERSION,
                recorded ? sourceTag.status() : null, recorded ? sourceTag.tag() : null,
                recorded ? sourceTag.tagCommitSha() : null, recorded ? sourceTag.commitsAfterTag() : null,
                recorded ? sourceTag.reason() : null);
        jdbc.update("""
                UPDATE github_sync_package_admission SET status = 'ACCEPTED', updated_at = ? WHERE id = ?
                """, Timestamp.from(now), admissionId);
        jdbc.update("""
                UPDATE github_sync_staged_upload SET status = 'ACCEPTED', updated_at = ? WHERE id = ?
                """, Timestamp.from(now), uploadId);
        jdbc.update("""
                INSERT INTO github_sync_path_result
                    (task_id, commit_sha, skill_path, outcome, ingestion_id, sha256)
                VALUES (?, ?, ?, 'ACCEPTED', ?, ?)
                ON CONFLICT (task_id, commit_sha, skill_path) DO UPDATE
                SET outcome = 'ACCEPTED', ingestion_id = EXCLUDED.ingestion_id,
                    sha256 = EXCLUDED.sha256, reason = NULL, retry_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE github_sync_path_result.outcome <> 'ACCEPTED'
                """, taskId, commitSha, skillPath, ingestionId, sha256);
        return new GithubPackageReportResult(GithubPackageReportResult.Status.RECEIVED, ingestionId, null);
    }

    private GithubPackageReportResult readReport(UUID taskId, long generation, String leaseTokenHash,
            UUID admissionId, String commitSha, String skillPath, UUID uploadId,
            long sizeBytes, String sha256, Instant now, boolean lock) {
        String sql = """
                SELECT admission.status AS admission_status, upload.status AS upload_status,
                       upload.mode, upload.object_key, upload.size_bytes, upload.sha256 AS upload_sha256,
                       ingestion.id AS ingestion_id, ingestion.size_bytes AS ingestion_size,
                       ingestion.sha256 AS ingestion_sha256
                FROM github_sync_package_admission admission
                JOIN github_sync_task task ON task.id = admission.task_id
                JOIN github_sync_staged_upload upload ON upload.admission_id = admission.id
                LEFT JOIN github_sync_package_ingestion ingestion
                  ON ingestion.task_id = task.id AND ingestion.commit_sha = admission.commit_sha
                 AND ingestion.skill_path = admission.skill_path
                WHERE admission.id = ? AND admission.task_id = ? AND admission.lease_generation = ?
                  AND admission.commit_sha = ? AND admission.skill_path = ? AND upload.id = ?
                  AND task.status = 'LEASED' AND task.lease_generation = ?
                  AND task.lease_token_hash = ? AND task.lease_expires_at > ?
                """ + (lock ? " FOR UPDATE OF task, admission, upload" : "");
        ReportRow row = jdbc.query(sql, statement -> {
            statement.setObject(1, admissionId);
            statement.setObject(2, taskId);
            statement.setLong(3, generation);
            statement.setString(4, commitSha);
            statement.setString(5, skillPath);
            statement.setObject(6, uploadId);
            statement.setLong(7, generation);
            statement.setString(8, leaseTokenHash);
            statement.setTimestamp(9, Timestamp.from(now));
        }, result -> result.next() ? new ReportRow(
                result.getString("admission_status"), result.getString("upload_status"),
                result.getString("mode"), result.getString("object_key"),
                result.getLong("size_bytes"), result.getString("upload_sha256"),
                result.getObject("ingestion_id", UUID.class),
                (Long) result.getObject("ingestion_size"), result.getString("ingestion_sha256")) : null);
        if (row == null) {
            return GithubPackageReportResult.rejected(GithubPackageReportResult.Status.LEASE_STALE);
        }
        if (row.ingestionId() != null) {
            if (row.ingestionSize() == sizeBytes && row.ingestionSha256().equals(sha256)) {
                return new GithubPackageReportResult(GithubPackageReportResult.Status.RECEIVED,
                        row.ingestionId(), null);
            }
            return GithubPackageReportResult.rejected(GithubPackageReportResult.Status.RESULT_CONFLICT);
        }
        if (!"ACTIVE".equals(row.admissionStatus()) || row.sizeBytes() != sizeBytes
                || !row.uploadSha256().equals(sha256)) {
            return GithubPackageReportResult.rejected(GithubPackageReportResult.Status.RESULT_CONFLICT);
        }
        if (("LOCAL_STREAM".equals(row.mode()) && !"UPLOADED".equals(row.uploadStatus()))
                || ("S3_PRESIGNED_PUT".equals(row.mode()) && !"ISSUED".equals(row.uploadStatus()))) {
            return GithubPackageReportResult.rejected(GithubPackageReportResult.Status.UPLOAD_NOT_READY);
        }
        return new GithubPackageReportResult(GithubPackageReportResult.Status.READY_TO_CHECK_OBJECT,
                null, row.objectKey());
    }

    private record ReportCoordinate(long namespaceId, long repositoryId) {}
    private record ReportRow(String admissionStatus, String uploadStatus, String mode,
            String objectKey, long sizeBytes, String uploadSha256,
            UUID ingestionId, Long ingestionSize, String ingestionSha256) {}
}
