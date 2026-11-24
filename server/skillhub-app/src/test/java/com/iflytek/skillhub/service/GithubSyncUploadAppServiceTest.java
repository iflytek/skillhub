package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.githubsync.GithubPackageReportResult;
import com.iflytek.skillhub.githubsync.GithubSourceTag;
import com.iflytek.skillhub.githubsync.GithubSyncUploadRepository;
import com.iflytek.skillhub.storage.ObjectMetadata;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.storage.PresignedUpload;
import com.iflytek.skillhub.storage.StorageAccessException;
import com.iflytek.skillhub.storage.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubSyncUploadAppServiceTest {
    private static final String COMMIT = "a".repeat(40);
    private static final String DIGEST = "b".repeat(64);
    private static final String TOKEN = "c".repeat(64);
    private static final String OBJECT_KEY = "github-sync/staging/package.zip";

    @Mock private GithubSyncUploadRepository uploads;
    @Mock private ObjectStorageService storage;

    @Test
    void localStorageFailureDoesNotPropagateUntrustedExceptionContent() throws IOException {
        String marker = "STORAGE_ERROR_WITH_SKILL_BODY_AND_TOKEN";
        UUID uploadId = UUID.randomUUID();
        String ticket = "d".repeat(64);
        when(uploads.beginLocalUpload(eq(uploadId), any(), any())).thenReturn(Optional.of(
                new GithubSyncUploadRepository.LocalUpload(OBJECT_KEY, 2, DIGEST)));
        when(uploads.failUpload(eq(uploadId), any())).thenReturn(true);
        doThrow(new StorageAccessException("putObject", OBJECT_KEY,
                new IllegalStateException(marker))).when(storage).putObject(
                eq(OBJECT_KEY), any(InputStream.class), eq(2L), eq("application/zip"));

        assertThatThrownBy(() -> service(new SkillPublishProperties()).uploadLocal(
                uploadId, ticket, new ByteArrayInputStream(new byte[] {1, 2})))
                .isInstanceOf(StorageAccessException.class)
                .hasMessageNotContaining(marker)
                .hasNoCause();
        verify(uploads).failUpload(eq(uploadId), any(Instant.class));
        verify(storage).deleteObject(OBJECT_KEY);
    }

    @Test
    void s3SigningFailureDoesNotPropagateUntrustedExceptionContent() {
        String marker = "S3_ERROR_WITH_SKILL_BODY_AND_TOKEN";
        var properties = new StorageProperties();
        properties.setProvider("s3");
        when(uploads.issue(any(), eq(1L), any(), any(), eq(COMMIT),
                eq("skills/demo"), any(), any(), eq("S3_PRESIGNED_PUT"), isNull(),
                eq(12L), eq(DIGEST), any(), any())).thenReturn(true);
        when(storage.presignPut(any(), eq(Duration.ofMinutes(5))))
                .thenThrow(new StorageAccessException("presignPut", OBJECT_KEY,
                        new IllegalStateException(marker)));
        var service = new GithubSyncUploadAppService(uploads, storage, properties,
                new SkillPublishProperties(), Duration.ofMinutes(5));

        assertThatThrownBy(() -> service.authorize(UUID.randomUUID(), 1, TOKEN,
                UUID.randomUUID(), COMMIT, "skills/demo", 12, DIGEST))
                .isInstanceOf(StorageAccessException.class)
                .hasMessageNotContaining(marker)
                .hasNoCause();
        verify(uploads).failUpload(any(), any(Instant.class));
    }

    @Test
    void reportStorageFailureDoesNotPropagateUntrustedExceptionContent() {
        String marker = "METADATA_ERROR_WITH_SKILL_BODY_AND_TOKEN";
        UUID uploadId = UUID.randomUUID();
        when(uploads.lookupReport(any(), eq(1L), any(), any(), eq(COMMIT),
                eq("skills/demo"), eq(uploadId), eq(12L), eq(DIGEST), any()))
                .thenReturn(new GithubPackageReportResult(
                        GithubPackageReportResult.Status.READY_TO_CHECK_OBJECT, null, OBJECT_KEY));
        when(storage.exists(OBJECT_KEY)).thenReturn(true);
        when(storage.getMetadata(OBJECT_KEY)).thenThrow(new IllegalStateException(marker));

        assertThatThrownBy(() -> service(new SkillPublishProperties()).report(
                UUID.randomUUID(), 1, TOKEN, UUID.randomUUID(), COMMIT, "skills/demo",
                uploadId, 12, DIGEST, null))
                .isInstanceOf(StorageAccessException.class)
                .hasMessageNotContaining(marker)
                .hasNoCause();
        verify(uploads, never()).acceptReport(any(), anyLong(), any(), any(), any(),
                any(), any(), anyLong(), any(), any(), any());
    }

    @Test
    void s3AuthorizationSignsOnlyTheServerGeneratedStagingKey() {
        UUID taskId = UUID.randomUUID();
        UUID admissionId = UUID.randomUUID();
        var properties = new StorageProperties();
        properties.setProvider("s3");
        when(uploads.issue(eq(taskId), eq(1L), any(), eq(admissionId), eq(COMMIT),
                eq("skills/demo"), any(), any(), eq("S3_PRESIGNED_PUT"), isNull(),
                eq(12L), eq(DIGEST), any(), any())).thenReturn(true);
        when(storage.presignPut(any(), eq(Duration.ofMinutes(5))))
                .thenReturn(Optional.of(new PresignedUpload("https://storage.example/upload", Map.of())));
        var service = new GithubSyncUploadAppService(uploads, storage, properties,
                new SkillPublishProperties(), Duration.ofMinutes(5));

        var authorization = service.authorize(taskId, 1, TOKEN, admissionId, COMMIT,
                "skills/demo", 12, DIGEST).orElseThrow();

        ArgumentCaptor<UUID> uploadIds = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> objectKeys = ArgumentCaptor.forClass(String.class);
        verify(uploads).issue(eq(taskId), eq(1L), any(), eq(admissionId), eq(COMMIT),
                eq("skills/demo"), uploadIds.capture(), objectKeys.capture(),
                eq("S3_PRESIGNED_PUT"), isNull(), eq(12L), eq(DIGEST), any(), any());
        assertThat(uploadIds.getValue()).isEqualTo(authorization.uploadId());
        assertThat(objectKeys.getValue()).isEqualTo(
                "github-sync/staging/" + authorization.uploadId() + ".zip");
        verify(storage).presignPut(objectKeys.getValue(), Duration.ofMinutes(5));
    }

    @Test
    void localUploadCannotUseTicketForAnotherUploadId() throws IOException {
        UUID wrongUploadId = UUID.randomUUID();
        String ticket = "d".repeat(64);
        when(uploads.beginLocalUpload(eq(wrongUploadId), any(), any()))
                .thenReturn(Optional.empty());

        var result = service(new SkillPublishProperties()).uploadLocal(
                wrongUploadId, ticket, new ByteArrayInputStream(new byte[] {1, 2}));

        assertThat(result).isEqualTo(GithubSyncUploadAppService.LocalUploadResult.TICKET_INVALID);
        verifyNoInteractions(storage);
    }

    @Test
    void removesTicketObjectWhenPackagePolicyShrinksBeforeReport() {
        SkillPublishProperties policy = new SkillPublishProperties();
        policy.setMaxPackageSize(10);
        UUID uploadId = UUID.randomUUID();
        when(uploads.lookupReport(any(), eq(1L), any(), any(), eq(COMMIT),
                eq("skills/demo"), eq(uploadId), eq(12L), eq(DIGEST), any()))
                .thenReturn(new GithubPackageReportResult(
                        GithubPackageReportResult.Status.READY_TO_CHECK_OBJECT, null, OBJECT_KEY));
        when(uploads.failUpload(eq(uploadId), any())).thenReturn(true);

        var result = service(policy).report(UUID.randomUUID(), 1, TOKEN, UUID.randomUUID(),
                COMMIT, "skills/demo", uploadId, 12, DIGEST, null);

        assertThat(result.status()).isEqualTo(GithubPackageReportResult.Status.TOO_LARGE);
        verify(uploads).failUpload(eq(uploadId), any(Instant.class));
        verify(storage).deleteObject(OBJECT_KEY);
        verify(uploads, never()).acceptReport(any(), anyLong(), any(), any(), any(),
                any(), any(), anyLong(), any(), any(), any());
    }

    @Test
    void reportStoresNormalizedSourceTagAndNeverRejectsForIt() throws Exception {
        UUID uploadId = UUID.randomUUID();
        when(uploads.lookupReport(any(), eq(1L), any(), any(), eq(COMMIT),
                eq("skills/demo"), eq(uploadId), eq(12L), eq(DIGEST), any()))
                .thenReturn(new GithubPackageReportResult(
                        GithubPackageReportResult.Status.READY_TO_CHECK_OBJECT, null, OBJECT_KEY));
        when(storage.exists(OBJECT_KEY)).thenReturn(true);
        when(storage.getMetadata(OBJECT_KEY)).thenReturn(new ObjectMetadata(12, "application/zip", Instant.now()));
        UUID ingestionId = UUID.randomUUID();
        when(uploads.acceptReport(any(), eq(1L), any(), any(), eq(COMMIT), eq("skills/demo"),
                eq(uploadId), eq(12L), eq(DIGEST), any(), any()))
                .thenReturn(new GithubPackageReportResult(GithubPackageReportResult.Status.RECEIVED, ingestionId, null));

        var result = service(new SkillPublishProperties()).report(UUID.randomUUID(), 1, TOKEN,
                UUID.randomUUID(), COMMIT, "skills/demo", uploadId, 12, DIGEST,
                new ObjectMapper().readTree("""
                        {"status":"AFTER_TAG","tag":"v1.3.2 extra","tagCommitSha":"%s","commitsAfterTag":3}
                        """.formatted("d".repeat(40))));

        assertThat(result.ingestionId()).isEqualTo(ingestionId);
        verify(uploads).acceptReport(any(), eq(1L), any(), any(), eq(COMMIT), eq("skills/demo"),
                eq(uploadId), eq(12L), eq(DIGEST),
                eq(new GithubSourceTag("UNKNOWN", null, null, null, "INVALID_REPORT")), any());
    }

    @Test
    void sourceTagShapesThatCannotBindAreKeptAsInvalidReports() throws Exception {
        ObjectMapper json = new ObjectMapper();
        GithubSourceTag invalid = new GithubSourceTag("UNKNOWN", null, null, null, "INVALID_REPORT");
        assertThat(GithubSyncUploadAppService.sourceTag(null)).isNull();
        assertThat(GithubSyncUploadAppService.sourceTag(json.readTree("null"))).isNull();
        for (String reported : List.of("[]", "\"EXACT\"", "{\"status\":{\"nested\":true}}",
                "{\"status\":\"AFTER_TAG\",\"tag\":\"v1.0.0\",\"tagCommitSha\":\"%s\",\"commitsAfterTag\":2147483648}",
                "{\"status\":\"AFTER_TAG\",\"tag\":\"v1.0.0\",\"tagCommitSha\":\"%s\",\"commitsAfterTag\":\"3\"}",
                "{\"status\":\"AFTER_TAG\",\"tag\":\"v1.0.0\",\"tagCommitSha\":\"%s\",\"commitsAfterTag\":1.5}",
                "{\"status\":\"EXACT\",\"tag\":7,\"tagCommitSha\":\"%s\",\"commitsAfterTag\":0}")) {
            assertThat(GithubSyncUploadAppService.sourceTag(json.readTree(reported.formatted("d".repeat(40)))))
                    .as(reported).isEqualTo(invalid);
        }
        assertThat(GithubSyncUploadAppService.sourceTag(json.readTree("{\"status\":\"NO_TAG\"}")))
                .isEqualTo(new GithubSourceTag("NO_TAG", null, null, null, null));
    }

    @Test
    void removesOversizedS3ObjectBeforeAcceptingReport() {
        SkillPublishProperties policy = new SkillPublishProperties();
        policy.setMaxPackageSize(10);
        UUID uploadId = UUID.randomUUID();
        when(uploads.lookupReport(any(), eq(1L), any(), any(), eq(COMMIT),
                eq("skills/demo"), eq(uploadId), eq(8L), eq(DIGEST), any()))
                .thenReturn(new GithubPackageReportResult(
                        GithubPackageReportResult.Status.READY_TO_CHECK_OBJECT, null, OBJECT_KEY));
        when(uploads.failUpload(eq(uploadId), any())).thenReturn(true);
        when(storage.exists(OBJECT_KEY)).thenReturn(true);
        when(storage.getMetadata(OBJECT_KEY)).thenReturn(
                new ObjectMetadata(12, "application/zip", Instant.now()));

        var result = service(policy).report(UUID.randomUUID(), 1, TOKEN, UUID.randomUUID(),
                COMMIT, "skills/demo", uploadId, 8, DIGEST, null);

        assertThat(result.status()).isEqualTo(GithubPackageReportResult.Status.TOO_LARGE);
        verify(uploads).failUpload(eq(uploadId), any(Instant.class));
        verify(storage).deleteObject(OBJECT_KEY);
    }

    @Test
    void existingAcceptedReportRemainsIdempotentAfterPolicyShrinks() {
        SkillPublishProperties policy = new SkillPublishProperties();
        policy.setMaxPackageSize(10);
        UUID uploadId = UUID.randomUUID();
        UUID ingestionId = UUID.randomUUID();
        when(uploads.lookupReport(any(), eq(1L), any(), any(), eq(COMMIT),
                eq("skills/demo"), eq(uploadId), eq(12L), eq(DIGEST), any()))
                .thenReturn(new GithubPackageReportResult(
                        GithubPackageReportResult.Status.RECEIVED, ingestionId, null));

        var result = service(policy).report(UUID.randomUUID(), 1, TOKEN, UUID.randomUUID(),
                COMMIT, "skills/demo", uploadId, 12, DIGEST, null);

        assertThat(result.ingestionId()).isEqualTo(ingestionId);
        verifyNoInteractions(storage);
    }

    @Test
    void failedImmediateDeleteLeavesOversizedUploadForCompensation() {
        SkillPublishProperties policy = new SkillPublishProperties();
        policy.setMaxPackageSize(10);
        UUID uploadId = UUID.randomUUID();
        when(uploads.lookupReport(any(), eq(1L), any(), any(), eq(COMMIT),
                eq("skills/demo"), eq(uploadId), eq(12L), eq(DIGEST), any()))
                .thenReturn(new GithubPackageReportResult(
                        GithubPackageReportResult.Status.READY_TO_CHECK_OBJECT, null, OBJECT_KEY));
        when(uploads.failUpload(eq(uploadId), any())).thenReturn(true);
        doThrow(new IllegalStateException("temporary storage failure"))
                .when(storage).deleteObject(OBJECT_KEY);

        var result = service(policy).report(UUID.randomUUID(), 1, TOKEN, UUID.randomUUID(),
                COMMIT, "skills/demo", uploadId, 12, DIGEST, null);

        assertThat(result.status()).isEqualTo(GithubPackageReportResult.Status.TOO_LARGE);
        verify(uploads).failUpload(eq(uploadId), any(Instant.class));
    }

    @Test
    void acceptedUploadIsNeverDeletedByConcurrentOversizeReport() {
        SkillPublishProperties policy = new SkillPublishProperties();
        policy.setMaxPackageSize(10);
        UUID uploadId = UUID.randomUUID();
        when(uploads.lookupReport(any(), eq(1L), any(), any(), eq(COMMIT),
                eq("skills/demo"), eq(uploadId), eq(12L), eq(DIGEST), any()))
                .thenReturn(new GithubPackageReportResult(
                        GithubPackageReportResult.Status.READY_TO_CHECK_OBJECT, null, OBJECT_KEY));

        var result = service(policy).report(UUID.randomUUID(), 1, TOKEN, UUID.randomUUID(),
                COMMIT, "skills/demo", uploadId, 12, DIGEST, null);

        assertThat(result.status()).isEqualTo(GithubPackageReportResult.Status.TOO_LARGE);
        verify(uploads).failUpload(eq(uploadId), any(Instant.class));
        verifyNoInteractions(storage);
    }

    @Test
    void localStreamRejectsBytesBeyondTicketSize() throws IOException {
        UUID uploadId = UUID.randomUUID();
        String ticket = "d".repeat(64);
        when(uploads.beginLocalUpload(eq(uploadId), any(), any())).thenReturn(Optional.of(
                new GithubSyncUploadRepository.LocalUpload(OBJECT_KEY, 2, DIGEST)));
        when(uploads.failUpload(eq(uploadId), any())).thenReturn(true);
        doAnswer(invocation -> {
            InputStream body = invocation.getArgument(1);
            try {
                body.transferTo(OutputStream.nullOutputStream());
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return null;
        }).when(storage).putObject(eq(OBJECT_KEY), any(InputStream.class), eq(2L),
                eq("application/zip"));

        var result = service(new SkillPublishProperties()).uploadLocal(uploadId, ticket,
                new ByteArrayInputStream(new byte[] {1, 2, 3}));

        assertThat(result).isEqualTo(GithubSyncUploadAppService.LocalUploadResult.TOO_LARGE);
        verify(uploads).failUpload(eq(uploadId), any(Instant.class));
        verify(storage).deleteObject(OBJECT_KEY);
        verify(uploads, never()).finishLocalUpload(eq(uploadId), any());
    }

    private GithubSyncUploadAppService service(SkillPublishProperties policy) {
        return new GithubSyncUploadAppService(uploads, storage, new StorageProperties(),
                policy, Duration.ofMinutes(5));
    }
}
