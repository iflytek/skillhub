package com.iflytek.skillhub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.iflytek.skillhub.githubsync.GithubPackageReportResult;
import com.iflytek.skillhub.service.GithubSyncUploadAppService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

@RestController
@Hidden
@RequestMapping("/internal/github-sync/v1")
public class GithubSyncUploadController {
    private final GithubSyncUploadAppService uploads;

    public GithubSyncUploadController(GithubSyncUploadAppService uploads) {
        this.uploads = uploads;
    }

    @PostMapping("/tasks/{taskId}/upload-authorizations")
    public ResponseEntity<?> authorize(@PathVariable UUID taskId,
            @RequestBody UploadAuthorizationRequest request) {
        if (request.lease() == null) {
            throw new IllegalArgumentException("lease is required");
        }
        try {
            return uploads.authorize(taskId, request.lease().generation(), request.lease().token(),
                            request.admissionId(), request.commitSha(), request.skillPath(),
                            request.sizeBytes(), request.sha256())
                    .<ResponseEntity<?>>map(authorization -> ResponseEntity.ok()
                            .cacheControl(CacheControl.noStore()).body(authorization))
                    .orElseGet(() -> ResponseEntity.status(409)
                            .body(new GithubSyncInternalController.ApiError("ADMISSION_STALE",
                                    "Package admission is no longer current")));
        } catch (GithubSyncUploadAppService.UploadTooLargeException exception) {
            return ResponseEntity.status(413)
                    .body(new GithubSyncInternalController.ApiError("PACKAGE_TOO_LARGE",
                            "Package exceeds the active upload size limit"));
        }
    }

    @PutMapping(value = "/uploads/{uploadId}/content", consumes = "application/octet-stream")
    public ResponseEntity<?> uploadLocal(@PathVariable UUID uploadId,
            @RequestHeader(value = "X-Upload-Ticket", required = false) String ticket,
            HttpServletRequest request) throws IOException {
        return switch (uploads.uploadLocal(uploadId, ticket, request.getInputStream())) {
            case UPLOADED -> ResponseEntity.noContent().build();
            case TICKET_INVALID -> ResponseEntity.status(401)
                    .body(new GithubSyncInternalController.ApiError("UPLOAD_TICKET_INVALID",
                            "Upload ticket is invalid or expired"));
            case TOO_LARGE -> ResponseEntity.status(413)
                    .body(new GithubSyncInternalController.ApiError("PACKAGE_TOO_LARGE",
                            "Package exceeds the authorized size"));
            case CONTENT_MISMATCH -> ResponseEntity.unprocessableEntity()
                    .body(new GithubSyncInternalController.ApiError("UPLOAD_MISMATCH",
                            "Uploaded package size or digest does not match"));
            case LEASE_STALE -> ResponseEntity.status(409)
                    .body(new GithubSyncInternalController.ApiError("LEASE_STALE",
                            "Lease is no longer current"));
        };
    }

    @PostMapping("/tasks/{taskId}/packages/report")
    public ResponseEntity<?> report(@PathVariable UUID taskId, @RequestBody PackageReportRequest request) {
        if (request.lease() == null) {
            throw new IllegalArgumentException("lease is required");
        }
        GithubPackageReportResult result = uploads.report(taskId, request.lease().generation(),
                request.lease().token(), request.admissionId(), request.commitSha(),
                request.skillPath(), request.uploadId(), request.sizeBytes(), request.sha256(),
                request.sourceTag());
        return switch (result.status()) {
            case RECEIVED -> ResponseEntity.accepted().body(new PackageReportResponse(result.ingestionId(), "RECEIVED"));
            case LEASE_STALE -> ResponseEntity.status(409)
                    .body(new GithubSyncInternalController.ApiError("LEASE_STALE", "Lease is no longer current"));
            case RESULT_CONFLICT -> ResponseEntity.status(409)
                    .body(new GithubSyncInternalController.ApiError("RESULT_CONFLICT", "Package report conflicts with persisted state"));
            case UPLOAD_NOT_READY, OBJECT_MISSING -> ResponseEntity.status(409)
                    .body(new GithubSyncInternalController.ApiError("UPLOAD_NOT_READY", "Staged package is not ready"));
            case OBJECT_MISMATCH -> ResponseEntity.unprocessableEntity()
                    .body(new GithubSyncInternalController.ApiError("UPLOAD_MISMATCH", "Staged package size differs"));
            case TOO_LARGE -> ResponseEntity.status(413)
                    .body(new GithubSyncInternalController.ApiError("PACKAGE_TOO_LARGE", "Staged package exceeds the active size limit"));
            case READY_TO_CHECK_OBJECT -> throw new IllegalStateException("Package report was not settled");
        };
    }

    public record UploadAuthorizationRequest(GithubSyncInternalController.LeaseProof lease,
            UUID admissionId, String commitSha, String skillPath, long sizeBytes, String sha256) {}
    public record PackageReportRequest(GithubSyncInternalController.LeaseProof lease,
            UUID admissionId, String commitSha, String skillPath, UUID uploadId,
            long sizeBytes, String sha256, JsonNode sourceTag) {}
    public record PackageReportResponse(UUID ingestionId, String status) {}
}
