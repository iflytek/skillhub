package com.iflytek.skillhub.storage;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

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
}
