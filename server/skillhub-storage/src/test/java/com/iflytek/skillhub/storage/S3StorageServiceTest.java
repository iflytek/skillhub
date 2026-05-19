package com.iflytek.skillhub.storage;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
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
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class S3StorageServiceTest {

    @Test
    void buildS3ClientShouldDisableChunkedEncodingWhenConfigured() {
        S3StorageProperties props = createProperties(true);
        props.setDisableChunkedEncoding(true);
        S3StorageService service = new S3StorageService(props);
        ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder();
        S3Client client = service.buildS3Client(httpClientBuilder);
        assertThat(client).isNotNull();
        client.close();
    }

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
    void shouldUseStaticCredentialsWhenAccessKeyAndSecretKeyProvided() {
        S3StorageProperties props = createProperties(true);
        S3StorageService service = new S3StorageService(props);

        AwsCredentialsProvider provider = service.buildCredentialsProvider();

        assertThat(provider).isInstanceOf(StaticCredentialsProvider.class);
    }

    @Test
    void shouldUseDefaultCredentialsProviderWhenAccessKeyIsBlank() {
        S3StorageProperties props = createProperties(true);
        props.setAccessKey("");
        props.setSecretKey("");
        S3StorageService service = new S3StorageService(props);

        AwsCredentialsProvider provider = service.buildCredentialsProvider();

        assertThat(provider).isInstanceOf(DefaultCredentialsProvider.class);
    }

    @Test
    void shouldUseDefaultCredentialsProviderWhenAccessKeyIsNull() {
        S3StorageProperties props = createProperties(true);
        props.setAccessKey(null);
        props.setSecretKey(null);
        S3StorageService service = new S3StorageService(props);

        AwsCredentialsProvider provider = service.buildCredentialsProvider();

        assertThat(provider).isInstanceOf(DefaultCredentialsProvider.class);
    }

    @Test
    void shouldUseDefaultCredentialsProviderWhenOnlyAccessKeyIsSet() {
        S3StorageProperties props = createProperties(true);
        props.setAccessKey("some-key");
        props.setSecretKey(null);
        S3StorageService service = new S3StorageService(props);

        AwsCredentialsProvider provider = service.buildCredentialsProvider();

        assertThat(provider).isInstanceOf(DefaultCredentialsProvider.class);
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
