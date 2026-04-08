package com.iflytek.skillhub.storage;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3StorageServiceTest {

    @Test
    void initShouldIgnoreForbiddenHeadBucketWhenAutoCreateDisabled() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenThrow(s3Exception(403, "AccessDenied"));

        S3StorageService service = new S3StorageService(properties(false), s3Client, mock(S3Presigner.class));

        assertDoesNotThrow(service::init);
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void initShouldIgnoreForbiddenHeadBucketWhenAutoCreateEnabled() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenThrow(s3Exception(403, "AccessDenied"));

        S3StorageService service = new S3StorageService(properties(true), s3Client, mock(S3Presigner.class));

        assertDoesNotThrow(service::init);
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void initShouldCreateBucketWhenHeadBucketReturnsNotFound() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenThrow(s3Exception(404, "NoSuchBucket"));
        when(s3Client.createBucket(any(CreateBucketRequest.class))).thenReturn(CreateBucketResponse.builder().build());

        S3StorageService service = new S3StorageService(properties(true), s3Client, mock(S3Presigner.class));

        assertDoesNotThrow(service::init);
        verify(s3Client).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void initShouldCreateBucketWhenHeadBucketThrowsNoSuchBucketException() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenThrow(
                (NoSuchBucketException) NoSuchBucketException.builder()
                        .statusCode(404)
                        .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchBucket").build())
                        .message("NoSuchBucket")
                        .build()
        );
        when(s3Client.createBucket(any(CreateBucketRequest.class))).thenReturn(CreateBucketResponse.builder().build());

        S3StorageService service = new S3StorageService(properties(true), s3Client, mock(S3Presigner.class));

        assertDoesNotThrow(service::init);
        verify(s3Client).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void initShouldPropagateNotFoundWhenAutoCreateDisabled() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenThrow(s3Exception(404, "NoSuchBucket"));

        S3StorageService service = new S3StorageService(properties(false), s3Client, mock(S3Presigner.class));

        assertThrows(S3Exception.class, service::init);
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void initShouldPropagateUnexpectedHeadBucketErrors() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenThrow(s3Exception(500, "InternalError"));

        S3StorageService service = new S3StorageService(properties(false), s3Client, mock(S3Presigner.class));

        assertThrows(S3Exception.class, service::init);
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void initShouldPassWhenHeadBucketSucceeds() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());

        S3StorageService service = new S3StorageService(properties(false), s3Client, mock(S3Presigner.class));

        assertDoesNotThrow(service::init);
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    private static S3StorageProperties properties(boolean autoCreateBucket) {
        S3StorageProperties properties = new S3StorageProperties();
        properties.setBucket("skillhub-test");
        properties.setRegion("us-east-1");
        properties.setAccessKey("test-access-key");
        properties.setSecretKey("test-secret-key");
        properties.setAutoCreateBucket(autoCreateBucket);
        return properties;
    }

    private static S3Exception s3Exception(int statusCode, String errorCode) {
        return (S3Exception) S3Exception.builder()
                .statusCode(statusCode)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode(errorCode).build())
                .message(errorCode)
                .build();
    }
}
