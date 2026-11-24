package com.iflytek.skillhub.githubsync;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GithubSyncUploadRepository {
    boolean issue(UUID taskId, long generation, String leaseTokenHash, UUID admissionId,
                  String commitSha, String skillPath, UUID uploadId, String objectKey,
                  String mode, String ticketHash, long sizeBytes, String sha256, Instant expiresAt, Instant now);

    Optional<LocalUpload> beginLocalUpload(UUID uploadId, String ticketHash, Instant now);

    boolean finishLocalUpload(UUID uploadId, Instant now);

    boolean failUpload(UUID uploadId, Instant now);

    List<CleanupUpload> claimCleanup(Instant now, Duration signedUrlGrace, int limit);

    void markCleaned(UUID uploadId, Instant now);

    GithubPackageReportResult lookupReport(UUID taskId, long generation, String leaseTokenHash,
            UUID admissionId, String commitSha, String skillPath, UUID uploadId,
            long sizeBytes, String sha256, Instant now);

    /**
     * Creates the ingestion once. {@code sourceTag} is stored only on that insert;
     * replayed reports never rewrite it.
     */
    GithubPackageReportResult acceptReport(UUID taskId, long generation, String leaseTokenHash,
            UUID admissionId, String commitSha, String skillPath, UUID uploadId,
            long sizeBytes, String sha256, GithubSourceTag sourceTag, Instant now);

    record LocalUpload(String objectKey, long sizeBytes, String sha256) {}
    record CleanupUpload(UUID uploadId, String objectKey) {}
}
