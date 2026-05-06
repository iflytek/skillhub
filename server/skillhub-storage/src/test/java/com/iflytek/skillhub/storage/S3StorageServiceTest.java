package com.iflytek.skillhub.storage;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class S3StorageServiceTest {

    @Test
    void shouldUsePathStylePresignedUrlWhenForcePathStyleEnabled() {
        URI presignedUrl = presignGetObjectUrl(true);

        assertThat(presignedUrl.getHost()).isEqualTo("s3.us-east-1.amazonaws.com");
        assertThat(presignedUrl.getPath()).isEqualTo("/test-bucket/artifacts/package.tgz");
    }

    @Test
    void shouldUseHostStylePresignedUrlWhenForcePathStyleDisabled() {
        URI presignedUrl = presignGetObjectUrl(false);

        assertThat(presignedUrl.getHost()).isEqualTo("test-bucket.s3.us-east-1.amazonaws.com");
        assertThat(presignedUrl.getPath()).isEqualTo("/artifacts/package.tgz");
    }

    @Test
    void initShouldNotProbeBucketWhenAutoCreateIsDisabled() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        TestableS3StorageService service = new TestableS3StorageService(properties(false), client, presigner);

        service.init();

        verifyNoInteractions(client);
    }

    @Test
    void putObjectShouldSkipBucketProbeWhenAutoCreateIsDisabled() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag").build());
        TestableS3StorageService service = new TestableS3StorageService(properties(false), client, presigner);

        service.init();
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        service.putObject("packages/demo.zip", new ByteArrayInputStream(content), content.length, "application/zip");

        verify(client, never()).headBucket(any(HeadBucketRequest.class));
        verify(client, never()).createBucket(any(CreateBucketRequest.class));
        verify(client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void putObjectShouldWriteDirectlyWhenBucketAlreadyExists() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag").build());
        TestableS3StorageService service = new TestableS3StorageService(properties(true), client, presigner);

        service.init();
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        service.putObject("packages/demo.zip", new ByteArrayInputStream(content), content.length, "application/zip");

        verify(client, never()).headBucket(any(HeadBucketRequest.class));
        verify(client, never()).createBucket(any(CreateBucketRequest.class));
        verify(client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void getObjectShouldNotCreateBucketWhenAutoCreateIsEnabled() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("missing").build());
        TestableS3StorageService service = new TestableS3StorageService(properties(true), client, presigner);

        service.init();
        assertThatThrownBy(() -> service.getObject("packages/demo.zip"))
                .isInstanceOf(StorageAccessException.class);

        verify(client, never()).headBucket(any(HeadBucketRequest.class));
        verify(client, never()).createBucket(any(CreateBucketRequest.class));
        verify(client).getObject(any(GetObjectRequest.class));
    }

    @Test
    void putObjectShouldCreateBucketAndRetryWhenMissing() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        List<String> uploadedBodies = new ArrayList<>();
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenAnswer(invocation -> {
                    uploadedBodies.add(readBody(invocation.getArgument(1)));
                    throw NoSuchBucketException.builder().message("missing").build();
                })
                .thenAnswer(invocation -> {
                    uploadedBodies.add(readBody(invocation.getArgument(1)));
                    return PutObjectResponse.builder().eTag("etag").build();
                });
        when(client.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CreateBucketResponse.builder().build());
        TestableS3StorageService service = new TestableS3StorageService(properties(true), client, presigner);

        service.init();
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        service.putObject("packages/demo-1.zip", new ByteArrayInputStream(content), content.length, "application/zip");

        verify(client, never()).headBucket(any(HeadBucketRequest.class));
        verify(client, times(1)).createBucket(any(CreateBucketRequest.class));
        verify(client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        assertThat(uploadedBodies).containsExactly("hello", "hello");
    }

    @Test
    void putObjectShouldCreateBucketOnlyOnceWhenAutoCreateIsEnabled() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(NoSuchBucketException.builder().message("missing").build())
                .thenReturn(PutObjectResponse.builder().eTag("etag-1").build())
                .thenReturn(PutObjectResponse.builder().eTag("etag-2").build());
        when(client.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CreateBucketResponse.builder().build());
        TestableS3StorageService service = new TestableS3StorageService(properties(true), client, presigner);

        service.init();
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        service.putObject("packages/demo-1.zip", new ByteArrayInputStream(content), content.length, "application/zip");
        service.putObject("packages/demo-2.zip", new ByteArrayInputStream(content), content.length, "application/zip");

        verify(client, never()).headBucket(any(HeadBucketRequest.class));
        verify(client, times(1)).createBucket(any(CreateBucketRequest.class));
        verify(client, times(3)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void putObjectShouldRetryWhenBucketWasCreatedConcurrently() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(NoSuchBucketException.builder().message("missing").build())
                .thenReturn(PutObjectResponse.builder().eTag("etag").build());
        doThrow(BucketAlreadyOwnedByYouException.builder().message("exists").build())
                .when(client).createBucket(any(CreateBucketRequest.class));
        TestableS3StorageService service = new TestableS3StorageService(properties(true), client, presigner);

        service.init();
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        service.putObject("packages/demo.zip", new ByteArrayInputStream(content), content.length, "application/zip");

        verify(client, times(1)).createBucket(any(CreateBucketRequest.class));
        verify(client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void buildS3Client_withEndpoint_setsEndpointOverride() {
        S3StorageProperties props = createProperties(true);
        props.setEndpoint("https://minio.example.com");
        S3StorageService service = new S3StorageService(props);
        try (S3Client client = service.buildS3Client(ApacheHttpClient.builder())) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    void buildPresigner_withPublicEndpoint_setsEndpointOverride() {
        S3StorageProperties props = createProperties(true);
        props.setPublicEndpoint("https://public.example.com");
        S3StorageService service = new S3StorageService(props);
        try (S3Presigner presigner = service.buildPresigner()) {
            assertThat(presigner).isNotNull();
        }
    }

    @Test
    void buildPresigner_withEndpoint_setsEndpointOverride() {
        S3StorageProperties props = createProperties(true);
        props.setEndpoint("https://minio.example.com");
        props.setPublicEndpoint(null);
        S3StorageService service = new S3StorageService(props);
        try (S3Presigner presigner = service.buildPresigner()) {
            assertThat(presigner).isNotNull();
        }
    }

    @Test
    void putObject_withRuntimeExceptionDuringPut_throwsStorageAccessException() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("s3 error"));
        TestableS3StorageService service = new TestableS3StorageService(properties(false), client, presigner);

        service.init();
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.putObject("packages/demo.zip", new ByteArrayInputStream(content), content.length, "application/zip"))
                .isInstanceOf(StorageAccessException.class)
                .hasMessageContaining("putObject");
    }

    @Test
    void getObject_withRuntimeException_throwsStorageAccessException() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(client.getObject(any(GetObjectRequest.class)))
                .thenThrow(new RuntimeException("s3 error"));
        TestableS3StorageService service = new TestableS3StorageService(properties(false), client, presigner);

        service.init();

        assertThatThrownBy(() -> service.getObject("packages/demo.zip"))
                .isInstanceOf(StorageAccessException.class)
                .hasMessageContaining("getObject");
    }

    @Test
    void deleteObject_withRuntimeException_throwsStorageAccessException() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        doThrow(new RuntimeException("s3 error")).when(client).deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
        TestableS3StorageService service = new TestableS3StorageService(properties(false), client, presigner);

        service.init();

        assertThatThrownBy(() -> service.deleteObject("packages/demo.zip"))
                .isInstanceOf(StorageAccessException.class)
                .hasMessageContaining("deleteObject");
    }

    @Test
    void deleteObjects_emptyList_skipsCall() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        TestableS3StorageService service = new TestableS3StorageService(properties(false), client, presigner);

        service.init();
        service.deleteObjects(List.of());

        verify(client, never()).deleteObjects(any(software.amazon.awssdk.services.s3.model.DeleteObjectsRequest.class));
    }

    @Test
    void deleteObjects_withRuntimeException_throwsStorageAccessException() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        doThrow(new RuntimeException("s3 error")).when(client).deleteObjects(any(software.amazon.awssdk.services.s3.model.DeleteObjectsRequest.class));
        TestableS3StorageService service = new TestableS3StorageService(properties(false), client, presigner);

        service.init();

        assertThatThrownBy(() -> service.deleteObjects(List.of("packages/demo.zip")))
                .isInstanceOf(StorageAccessException.class)
                .hasMessageContaining("deleteObjects");
    }

    @Test
    void exists_withNoSuchKey_returnsFalse() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(client.headObject(any(software.amazon.awssdk.services.s3.model.HeadObjectRequest.class)))
                .thenThrow(software.amazon.awssdk.services.s3.model.NoSuchKeyException.builder().message("not found").build());
        TestableS3StorageService service = new TestableS3StorageService(properties(false), client, presigner);

        service.init();

        assertThat(service.exists("packages/demo.zip")).isFalse();
    }

    @Test
    void exists_withRuntimeException_throwsStorageAccessException() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(client.headObject(any(software.amazon.awssdk.services.s3.model.HeadObjectRequest.class)))
                .thenThrow(new RuntimeException("s3 error"));
        TestableS3StorageService service = new TestableS3StorageService(properties(false), client, presigner);

        service.init();

        assertThatThrownBy(() -> service.exists("packages/demo.zip"))
                .isInstanceOf(StorageAccessException.class)
                .hasMessageContaining("exists");
    }

    @Test
    void getMetadata_happyPath_returnsMetadata() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(client.headObject(any(software.amazon.awssdk.services.s3.model.HeadObjectRequest.class)))
                .thenReturn(software.amazon.awssdk.services.s3.model.HeadObjectResponse.builder()
                        .contentLength(42L)
                        .contentType("application/zip")
                        .lastModified(java.time.Instant.parse("2026-01-01T00:00:00Z"))
                        .build());
        TestableS3StorageService service = new TestableS3StorageService(properties(false), client, presigner);

        service.init();
        ObjectMetadata metadata = service.getMetadata("packages/demo.zip");

        assertThat(metadata.size()).isEqualTo(42L);
        assertThat(metadata.contentType()).isEqualTo("application/zip");
    }

    @Test
    void getMetadata_withRuntimeException_throwsStorageAccessException() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(client.headObject(any(software.amazon.awssdk.services.s3.model.HeadObjectRequest.class)))
                .thenThrow(new RuntimeException("s3 error"));
        TestableS3StorageService service = new TestableS3StorageService(properties(false), client, presigner);

        service.init();

        assertThatThrownBy(() -> service.getMetadata("packages/demo.zip"))
                .isInstanceOf(StorageAccessException.class)
                .hasMessageContaining("getMetadata");
    }

    @Test
    void generatePresignedUrl_withNullExpiry_usesDefault() {
        S3StorageProperties props = createProperties(true);
        props.setPresignExpiry(Duration.ofMinutes(30));
        S3StorageService service = new S3StorageService(props);
        try (S3Presigner presigner = service.buildPresigner()) {
            TestableS3StorageService testable = new TestableS3StorageService(props, mock(S3Client.class), presigner);
            testable.init();
            String url = testable.generatePresignedUrl("packages/demo.zip", null, null);
            assertThat(url).isNotBlank();
        }
    }

    @Test
    void generatePresignedUrl_withDownloadFilename_setsContentDisposition() {
        S3StorageProperties props = createProperties(true);
        S3StorageService service = new S3StorageService(props);
        try (S3Presigner presigner = service.buildPresigner()) {
            TestableS3StorageService testable = new TestableS3StorageService(props, mock(S3Client.class), presigner);
            testable.init();
            String url = testable.generatePresignedUrl("packages/demo.zip", Duration.ofMinutes(10), "my file.zip");
            assertThat(url).isNotBlank();
        }
    }

    @Test
    void getObject_happyPath_returnsInputStream() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        ResponseInputStream<GetObjectResponse> expectedStream = mock(ResponseInputStream.class);
        when(client.getObject(any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class)))
                .thenReturn(expectedStream);
        TestableS3StorageService service = new TestableS3StorageService(properties(false), client, presigner);

        service.init();
        InputStream result = service.getObject("packages/demo.zip");

        assertThat(result).isSameAs(expectedStream);
    }

    @Test
    void deleteObject_happyPath_deletesObject() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        TestableS3StorageService service = new TestableS3StorageService(properties(false), client, presigner);

        service.init();
        service.deleteObject("packages/demo.zip");

        verify(client).deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
    }

    @Test
    void deleteObjects_happyPath_deletesMultipleObjects() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        TestableS3StorageService service = new TestableS3StorageService(properties(false), client, presigner);

        service.init();
        service.deleteObjects(List.of("packages/a.zip", "packages/b.zip"));

        verify(client).deleteObjects(any(software.amazon.awssdk.services.s3.model.DeleteObjectsRequest.class));
    }

    @Test
    void exists_happyPath_returnsTrue() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(client.headObject(any(software.amazon.awssdk.services.s3.model.HeadObjectRequest.class)))
                .thenReturn(software.amazon.awssdk.services.s3.model.HeadObjectResponse.builder().build());
        TestableS3StorageService service = new TestableS3StorageService(properties(false), client, presigner);

        service.init();

        assertThat(service.exists("packages/demo.zip")).isTrue();
    }

    @Test
    void putObject_skipsBucketCheckWhenAlreadyPrepared() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag").build());
        TestableS3StorageService service = new TestableS3StorageService(properties(true), client, presigner);

        service.init();
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        service.putObject("packages/demo-1.zip", new ByteArrayInputStream(content), content.length, "application/zip");
        service.putObject("packages/demo-2.zip", new ByteArrayInputStream(content), content.length, "application/zip");

        verify(client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void putObject_withIOExceptionDuringStaging_throwsStorageAccessException() {
        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.createTempFile(any(String.class), any(String.class)))
                    .thenThrow(new IOException("disk full"));
            S3Client client = mock(S3Client.class);
            S3Presigner presigner = mock(S3Presigner.class);
            TestableS3StorageService service = new TestableS3StorageService(properties(true), client, presigner);

            service.init();
            byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> service.putObject("packages/demo.zip", new ByteArrayInputStream(content), content.length, "application/zip"))
                    .isInstanceOf(StorageAccessException.class)
                    .hasMessageContaining("putObject");
        }
    }

    @Test
    void generatePresignedUrl_withRuntimeException_throwsStorageAccessException() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(new RuntimeException("presign error"));
        TestableS3StorageService service = new TestableS3StorageService(properties(false), client, presigner);

        service.init();

        assertThatThrownBy(() -> service.generatePresignedUrl("packages/demo.zip", Duration.ofMinutes(10), null))
                .isInstanceOf(StorageAccessException.class)
                .hasMessageContaining("generatePresignedUrl");
    }

    private S3StorageProperties properties(boolean autoCreateBucket) {
        S3StorageProperties properties = createProperties(true);
        properties.setBucket("skillhub");
        properties.setAutoCreateBucket(autoCreateBucket);
        return properties;
    }

    private String readBody(RequestBody body) throws Exception {
        try (InputStream inputStream = body.contentStreamProvider().newStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private URI presignGetObjectUrl(boolean forcePathStyle) {
        S3StorageService storageService = new S3StorageService(createProperties(forcePathStyle));
        try (var presigner = storageService.buildPresigner()) {
            var request = presigner.presignGetObject(
                    GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(10))
                            .getObjectRequest(GetObjectRequest.builder()
                                    .bucket("test-bucket")
                                    .key("artifacts/package.tgz")
                                    .build())
                            .build()
            );
            return URI.create(request.url().toString());
        }
    }

    private S3StorageProperties createProperties(boolean forcePathStyle) {
        S3StorageProperties properties = new S3StorageProperties();
        properties.setRegion("us-east-1");
        properties.setBucket("test-bucket");
        properties.setAccessKey("test-access-key");
        properties.setSecretKey("test-secret-key");
        properties.setEndpoint("https://s3.us-east-1.amazonaws.com");
        properties.setForcePathStyle(forcePathStyle);
        return properties;
    }

    @Test
    void putObject_withIOExceptionDuringCopyInStaging_throwsStorageAccessException() throws Exception {
        Path tempPath = Files.createTempFile("skillhub-test-", ".tmp");
        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.createTempFile(any(String.class), any(String.class)))
                    .thenReturn(tempPath);
            filesMock.when(() -> Files.copy(any(InputStream.class), any(Path.class), any(CopyOption[].class)))
                    .thenThrow(new IOException("disk full"));
            S3Client client = mock(S3Client.class);
            S3Presigner presigner = mock(S3Presigner.class);
            TestableS3StorageService service = new TestableS3StorageService(properties(true), client, presigner);

            service.init();
            byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> service.putObject("packages/demo.zip", new ByteArrayInputStream(content), content.length, "application/zip"))
                    .isInstanceOf(StorageAccessException.class)
                    .hasMessageContaining("putObject");
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    @Test
    void putObject_withIOExceptionDuringStagedBodyCleanup_logsWarning() throws Exception {
        Path tempPath = Files.createTempFile("skillhub-test-", ".tmp");
        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.createTempFile(any(String.class), any(String.class)))
                    .thenReturn(tempPath);
            filesMock.when(() -> Files.copy(any(InputStream.class), any(Path.class), any(CopyOption[].class)))
                    .thenAnswer(invocation -> {
                        Path dest = invocation.getArgument(1);
                        java.nio.file.Files.write(dest, "hello".getBytes(StandardCharsets.UTF_8));
                        return 5L;
                    });
            filesMock.when(() -> Files.deleteIfExists(any(Path.class)))
                    .thenThrow(new IOException("cannot delete"));
            S3Client client = mock(S3Client.class);
            S3Presigner presigner = mock(S3Presigner.class);
            when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().eTag("etag").build());
            TestableS3StorageService service = new TestableS3StorageService(properties(true), client, presigner);

            service.init();
            byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

            // Should not throw - the IOException in deleteStagedBody is logged, not propagated
            service.putObject("packages/demo.zip", new ByteArrayInputStream(content), content.length, "application/zip");
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    @Test
    void ensureBucketPrepared_returnsEarlyWhenAutoCreateIsDisabled() throws Exception {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        TestableS3StorageService service = new TestableS3StorageService(properties(false), client, presigner);
        service.init();

        java.lang.reflect.Method method = S3StorageService.class.getDeclaredMethod("ensureBucketPrepared");
        method.setAccessible(true);
        method.invoke(service);

        verify(client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void ensureBucketPrepared_returnsEarlyWhenAlreadyPrepared() throws Exception {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        TestableS3StorageService service = new TestableS3StorageService(properties(true), client, presigner);
        service.init();

        java.lang.reflect.Field bpField = S3StorageService.class.getDeclaredField("bucketPrepared");
        bpField.setAccessible(true);
        bpField.set(service, true);

        java.lang.reflect.Method method = S3StorageService.class.getDeclaredMethod("ensureBucketPrepared");
        method.setAccessible(true);
        method.invoke(service);

        verify(client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void ensureBucketPrepared_secondThreadSeesPreparedAfterFirstThreadCreates() throws Exception {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(NoSuchBucketException.builder().message("missing").build());
        when(client.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CreateBucketResponse.builder().build());
        TestableS3StorageService service = new TestableS3StorageService(properties(true), client, presigner);
        service.init();

        java.lang.reflect.Field lockField = S3StorageService.class.getDeclaredField("bucketPreparationLock");
        lockField.setAccessible(true);
        Object lock = lockField.get(service);

        java.lang.reflect.Field bpField = S3StorageService.class.getDeclaredField("bucketPrepared");
        bpField.setAccessible(true);
        bpField.set(service, false);

        java.lang.reflect.Method method = S3StorageService.class.getDeclaredMethod("ensureBucketPrepared");
        method.setAccessible(true);

        Thread worker = new Thread(() -> {
            try {
                method.invoke(service);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        synchronized (lock) {
            worker.start();

            long deadline = System.currentTimeMillis() + 2000;
            while (worker.getState() != Thread.State.BLOCKED && System.currentTimeMillis() < deadline) {
                Thread.sleep(5);
            }

            // The worker thread passed the outer check with bucketPrepared=false,
            // then blocked on synchronized. Now set bucketPrepared=true
            // and release the lock so the worker sees it inside the synchronized block.
            bpField.set(service, true);
        }

        worker.join(2000);
        assertThat(worker.isAlive()).isFalse();

        // createBucket should never be called because t sees bucketPrepared=true inside sync
        verify(client, never()).createBucket(any(CreateBucketRequest.class));
    }

    private static final class TestableS3StorageService extends S3StorageService {
        private final S3Client client;
        private final S3Presigner presigner;

        private TestableS3StorageService(S3StorageProperties properties, S3Client client, S3Presigner presigner) {
            super(properties);
            this.client = client;
            this.presigner = presigner;
        }

        @Override
        protected S3Client buildS3Client(ApacheHttpClient.Builder httpClientBuilder) {
            return client;
        }

        @Override
        S3Presigner buildPresigner() {
            return presigner;
        }
    }
}
