package com.iflytek.skillhub.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.githubsync.GithubSyncUploadRepository;
import com.iflytek.skillhub.githubsync.GithubPackageReportResult;
import com.iflytek.skillhub.githubsync.GithubSourceTag;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.storage.PresignedUpload;
import com.iflytek.skillhub.storage.StorageAccessException;
import com.iflytek.skillhub.storage.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class GithubSyncUploadAppService {
    private static final Logger log = LoggerFactory.getLogger(GithubSyncUploadAppService.class);
    private final GithubSyncUploadRepository uploads;
    private final ObjectStorageService storage;
    private final StorageProperties storageProperties;
    private final SkillPublishProperties publishProperties;
    private final Duration ticketTtl;
    private final SecureRandom random = new SecureRandom();

    public GithubSyncUploadAppService(GithubSyncUploadRepository uploads, ObjectStorageService storage,
            StorageProperties storageProperties, SkillPublishProperties publishProperties,
            @Value("${skillhub.github-sync.upload-ticket-ttl:PT5M}") Duration ticketTtl) {
        if (ticketTtl.isNegative() || ticketTtl.isZero() || ticketTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("GitHub sync upload ticket TTL must be positive and at most one hour");
        }
        this.uploads = uploads;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.publishProperties = publishProperties;
        this.ticketTtl = ticketTtl;
    }

    public Optional<UploadAuthorization> authorize(UUID taskId, long generation, String leaseToken,
            UUID admissionId, String commitSha, String skillPath, long sizeBytes, String sha256) {
        if (taskId == null || admissionId == null || generation < 1 || !isHex(leaseToken, 64)
                || !isHex(commitSha, 40) || skillPath == null || skillPath.isBlank()
                || skillPath.length() > 1024 || !isHex(sha256, 64)) {
            throw new IllegalArgumentException("Invalid GitHub sync upload authorization request");
        }
        long maxBytes = publishProperties.getMaxPackageSize();
        if (maxBytes <= 0) {
            throw new IllegalStateException("Skill package size policy must be positive");
        }
        if (sizeBytes <= 0 || sizeBytes > maxBytes) {
            throw new UploadTooLargeException();
        }
        String provider = storageProperties.getProvider();
        if (!"local".equals(provider) && !"s3".equals(provider)) {
            throw new IllegalStateException("Unsupported GitHub sync staging storage provider");
        }
        UUID uploadId = UUID.randomUUID();
        String objectKey = "github-sync/staging/" + uploadId + ".zip";
        String mode = "s3".equals(provider) ? "S3_PRESIGNED_PUT" : "LOCAL_STREAM";
        String ticket = null;
        if ("local".equals(provider)) {
            byte[] ticketBytes = new byte[32];
            random.nextBytes(ticketBytes);
            ticket = HexFormat.of().formatHex(ticketBytes);
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ticketTtl);
        if (!uploads.issue(taskId, generation, sha256(leaseToken), admissionId, commitSha, skillPath,
                uploadId, objectKey, mode, ticket == null ? null : sha256(ticket),
                sizeBytes, sha256, expiresAt, now)) {
            return Optional.empty();
        }
        if ("local".equals(provider)) {
            return Optional.of(new UploadAuthorization(uploadId, "PUT", mode,
                    "/internal/github-sync/v1/uploads/" + uploadId + "/content",
                    ticket, expiresAt, maxBytes, Map.of()));
        }
        try {
            PresignedUpload target = safeStorageCall(uploadId, "presignPut",
                    () -> storage.presignPut(objectKey, ticketTtl))
                    .orElseThrow(() -> new IllegalStateException("S3 storage cannot sign uploads"));
            return Optional.of(new UploadAuthorization(uploadId, "PUT", mode,
                    target.url(), null, expiresAt, maxBytes, target.headers()));
        } catch (RuntimeException exception) {
            uploads.failUpload(uploadId, Instant.now());
            throw exception;
        }
    }

    public LocalUploadResult uploadLocal(UUID uploadId, String ticket, InputStream body) throws IOException {
        if (uploadId == null || !isHex(ticket, 64) || body == null) {
            return LocalUploadResult.TICKET_INVALID;
        }
        Optional<GithubSyncUploadRepository.LocalUpload> current =
                uploads.beginLocalUpload(uploadId, sha256(ticket), Instant.now());
        if (current.isEmpty()) {
            return LocalUploadResult.TICKET_INVALID;
        }
        GithubSyncUploadRepository.LocalUpload upload = current.get();
        BoundedDigestInputStream bounded = new BoundedDigestInputStream(body, upload.sizeBytes());
        boolean uploaded = false;
        boolean storageCompleted = false;
        try {
            storage.putObject(upload.objectKey(), bounded, upload.sizeBytes(), "application/zip");
            storageCompleted = true;
            if (bounded.bytesRead() != upload.sizeBytes()
                    || !bounded.digestHex().equals(upload.sha256())) {
                return LocalUploadResult.CONTENT_MISMATCH;
            }
            if (!uploads.finishLocalUpload(uploadId, Instant.now())) {
                return LocalUploadResult.LEASE_STALE;
            }
            uploaded = true;
            return LocalUploadResult.UPLOADED;
        } catch (RuntimeException exception) {
            if (bounded.exceeded()) {
                return LocalUploadResult.TOO_LARGE;
            }
            if (!storageCompleted) {
                throw new StorageAccessException("putObject", uploadId.toString(), null);
            }
            throw new IllegalStateException("GitHub sync local upload failed");
        } finally {
            // A failed upload is never accepted as a package, including digest mismatch or lease expiry.
            // Keep FAILED in the upload ledger when best-effort object deletion fails.
            if (!uploaded && uploads.failUpload(uploadId, Instant.now())) {
                try {
                    storage.deleteObject(upload.objectKey());
                } catch (RuntimeException exception) {
                    log.warn("Could not clean up failed GitHub sync staged upload {}: {}",
                            uploadId, exception.getClass().getSimpleName());
                }
            }
        }
    }

    public GithubPackageReportResult report(UUID taskId, long generation, String leaseToken,
            UUID admissionId, String commitSha, String skillPath, UUID uploadId,
            long sizeBytes, String sha256, JsonNode sourceTag) {
        if (taskId == null || admissionId == null || uploadId == null || generation < 1
                || !isHex(leaseToken, 64) || !isHex(commitSha, 40)
                || skillPath == null || skillPath.isBlank() || skillPath.length() > 1024
                || !isHex(sha256, 64) || sizeBytes <= 0) {
            throw new IllegalArgumentException("Invalid GitHub sync package report");
        }
        String tokenHash = sha256(leaseToken);
        GithubPackageReportResult checked = uploads.lookupReport(taskId, generation, tokenHash,
                admissionId, commitSha, skillPath, uploadId, sizeBytes, sha256, Instant.now());
        if (checked.status() != GithubPackageReportResult.Status.READY_TO_CHECK_OBJECT) {
            return checked;
        }
        long maxBytes = publishProperties.getMaxPackageSize();
        if (maxBytes <= 0) {
            throw new IllegalStateException("Skill package size policy must be positive");
        }
        if (sizeBytes > maxBytes) {
            discardOversizedUpload(uploadId, checked.objectKey());
            return GithubPackageReportResult.rejected(GithubPackageReportResult.Status.TOO_LARGE);
        }
        if (!safeStorageCall(uploadId, "exists", () -> storage.exists(checked.objectKey()))) {
            return GithubPackageReportResult.rejected(GithubPackageReportResult.Status.OBJECT_MISSING);
        }
        long actualSize = safeStorageCall(uploadId, "getMetadata",
                () -> storage.getMetadata(checked.objectKey())).size();
        if (actualSize > maxBytes) {
            discardOversizedUpload(uploadId, checked.objectKey());
            return GithubPackageReportResult.rejected(GithubPackageReportResult.Status.TOO_LARGE);
        }
        if (actualSize != sizeBytes) {
            return GithubPackageReportResult.rejected(GithubPackageReportResult.Status.OBJECT_MISMATCH);
        }
        // Source tag provenance is optional and never rejects a package.
        return uploads.acceptReport(taskId, generation, tokenHash, admissionId,
                commitSha, skillPath, uploadId, sizeBytes, sha256,
                sourceTag(sourceTag), Instant.now());
    }

    /**
     * The worker's provenance is optional and untrusted: absent stays unrecorded, and any
     * shape that does not fit (wrong types, overflowing numbers, arrays) is kept as
     * UNKNOWN/INVALID_REPORT instead of rejecting an otherwise valid package report.
     */
    static GithubSourceTag sourceTag(JsonNode reported) {
        if (reported == null || reported.isNull() || reported.isMissingNode()) {
            return null;
        }
        GithubSourceTag invalid = GithubSourceTag.invalidReport();
        if (!reported.isObject()) {
            return invalid;
        }
        for (String field : new String[] {"status", "tag", "tagCommitSha", "reason"}) {
            JsonNode value = reported.get(field);
            if (value != null && !value.isNull() && !value.isTextual()) {
                return invalid;
            }
        }
        JsonNode distance = reported.get("commitsAfterTag");
        if (distance != null && !distance.isNull()
                && (!distance.isIntegralNumber() || !distance.canConvertToInt())) {
            return invalid;
        }
        return GithubSourceTag.fromReport(new GithubSourceTag(
                text(reported, "status"), text(reported, "tag"), text(reported, "tagCommitSha"),
                distance == null || distance.isNull() ? null : distance.intValue(),
                text(reported, "reason")));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private void discardOversizedUpload(UUID uploadId, String objectKey) {
        if (!uploads.failUpload(uploadId, Instant.now())) {
            return;
        }
        try {
            storage.deleteObject(objectKey);
        } catch (RuntimeException exception) {
            // FAILED uploads remain eligible for the existing compensation cleanup task.
            log.warn("Could not immediately clean up oversized GitHub sync staged upload {}", uploadId);
        }
    }

    private <T> T safeStorageCall(UUID uploadId, String operation, Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException exception) {
            throw new StorageAccessException(operation, uploadId.toString(), null);
        }
    }

    private static boolean isHex(String value, int length) {
        return value != null && value.length() == length && value.chars().allMatch(character ->
                (character >= '0' && character <= '9') || (character >= 'a' && character <= 'f'));
    }

    private static String sha256(String value) {
        return HexFormat.of().formatHex(newDigest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UploadAuthorization(UUID uploadId, String method, String mode, String uploadUrl,
                                      String uploadTicket, Instant expiresAt, long maxBytes,
                                      Map<String, String> headers) {}

    public enum LocalUploadResult { UPLOADED, TICKET_INVALID, TOO_LARGE, CONTENT_MISMATCH, LEASE_STALE }

    public static class UploadTooLargeException extends RuntimeException {}

    private static final class BoundedDigestInputStream extends FilterInputStream {
        private final long maxBytes;
        private final MessageDigest digest = newDigest();
        private long bytesRead;
        private boolean exceeded;

        private BoundedDigestInputStream(InputStream input, long maxBytes) {
            super(input);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value < 0) return -1;
            if (bytesRead >= maxBytes) {
                exceeded = true;
                throw new IOException("GitHub sync upload exceeds authorized size");
            }
            digest.update((byte) value);
            bytesRead++;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = in.read(buffer, offset, (int) Math.min(length, maxBytes - bytesRead + 1));
            if (count < 0) return -1;
            if (bytesRead + count > maxBytes) {
                exceeded = true;
                throw new IOException("GitHub sync upload exceeds authorized size");
            }
            digest.update(buffer, offset, count);
            bytesRead += count;
            return count;
        }

        private long bytesRead() { return bytesRead; }
        private boolean exceeded() { return exceeded; }
        private String digestHex() { return HexFormat.of().formatHex(digest.digest()); }
    }
}
