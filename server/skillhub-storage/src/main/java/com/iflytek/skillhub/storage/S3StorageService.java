package com.iflytek.skillhub.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * S3-compatible object storage implementation used for persisted skill packages and generated
 * download URLs.
 */
@Service
@ConditionalOnProperty(name = "skillhub.storage.provider", havingValue = "s3")
public class S3StorageService implements ObjectStorageService {
    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);
    private static final int HTTP_FORBIDDEN = 403;
    private static final int HTTP_NOT_FOUND = 404;
    private final S3StorageProperties properties;
    private S3Client s3Client;
    private S3Presigner s3Presigner;

    public S3StorageService(S3StorageProperties properties) { this.properties = properties; }

    S3StorageService(S3StorageProperties properties, S3Client s3Client, S3Presigner s3Presigner) {
        this.properties = properties;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @PostConstruct
    void init() {
        if (s3Client == null) {
            ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder()
                    .maxConnections(properties.getMaxConnections())
                    .connectionAcquisitionTimeout(properties.getConnectionAcquisitionTimeout());
            var builder = S3Client.builder()
                    .region(Region.of(properties.getRegion()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                    .forcePathStyle(properties.isForcePathStyle())
                    .httpClientBuilder(httpClientBuilder)
                    .overrideConfiguration(config -> config
                            .apiCallAttemptTimeout(properties.getApiCallAttemptTimeout())
                            .apiCallTimeout(properties.getApiCallTimeout()));
            if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
                builder.endpointOverride(URI.create(properties.getEndpoint()));
            }
            this.s3Client = builder.build();
        }
        if (s3Presigner == null) {
            var presignerBuilder = S3Presigner.builder()
                    .region(Region.of(properties.getRegion()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())));
            if (properties.getPublicEndpoint() != null && !properties.getPublicEndpoint().isBlank()) {
                presignerBuilder.endpointOverride(URI.create(properties.getPublicEndpoint()));
            } else if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
                presignerBuilder.endpointOverride(URI.create(properties.getEndpoint()));
            }
            this.s3Presigner = presignerBuilder.build();
        }
        ensureBucketExists();
    }

    private void ensureBucketExists() {
        String bucket = properties.getBucket();
        if (!properties.isAutoCreateBucket()) {
            verifyBucketAccessForConfiguredProvider(bucket);
            return;
        }
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            createBucket(bucket);
        } catch (S3Exception e) {
            if (e.statusCode() == HTTP_NOT_FOUND) {
                createBucket(bucket);
                return;
            }
            if (e.statusCode() == HTTP_FORBIDDEN) {
                log.warn("Unable to verify bucket '{}' at startup because the storage provider returned 403 to HeadBucket. Continuing with configured S3-compatible storage and deferring validation to runtime object operations.", bucket);
                return;
            }
            throw e;
        }
    }

    private void verifyBucketAccessForConfiguredProvider(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception e) {
            if (e.statusCode() == HTTP_FORBIDDEN) {
                log.warn("Storage provider returned 403 to HeadBucket for bucket '{}' during startup verification. Continuing because some S3-compatible providers deny bucket-level probes even when object operations are permitted.", bucket);
                return;
            }
            throw e;
        }
    }

    private void createBucket(String bucket) {
        log.info("Bucket '{}' does not exist, creating...", bucket);
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
    }

    @Override public void putObject(String key, InputStream data, long size, String contentType) {
        try {
            s3Client.putObject(PutObjectRequest.builder().bucket(properties.getBucket()).key(key).contentType(contentType).contentLength(size).build(), RequestBody.fromInputStream(data, size));
        } catch (RuntimeException e) {
            throw new StorageAccessException("putObject", key, e);
        }
    }

    @Override public InputStream getObject(String key) {
        try {
            return s3Client.getObject(GetObjectRequest.builder().bucket(properties.getBucket()).key(key).build());
        } catch (RuntimeException e) {
            throw new StorageAccessException("getObject", key, e);
        }
    }

    @Override public void deleteObject(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(properties.getBucket()).key(key).build());
        } catch (RuntimeException e) {
            throw new StorageAccessException("deleteObject", key, e);
        }
    }

    @Override public void deleteObjects(List<String> keys) {
        if (keys.isEmpty()) return;
        try {
            List<ObjectIdentifier> ids = keys.stream().map(k -> ObjectIdentifier.builder().key(k).build()).toList();
            s3Client.deleteObjects(DeleteObjectsRequest.builder().bucket(properties.getBucket()).delete(Delete.builder().objects(ids).build()).build());
        } catch (RuntimeException e) {
            throw new StorageAccessException("deleteObjects", String.join(",", keys), e);
        }
    }

    @Override public boolean exists(String key) {
        try { s3Client.headObject(HeadObjectRequest.builder().bucket(properties.getBucket()).key(key).build()); return true; }
        catch (NoSuchKeyException e) { return false; }
        catch (RuntimeException e) { throw new StorageAccessException("exists", key, e); }
    }

    @Override public ObjectMetadata getMetadata(String key) {
        try {
            HeadObjectResponse resp = s3Client.headObject(HeadObjectRequest.builder().bucket(properties.getBucket()).key(key).build());
            return new ObjectMetadata(resp.contentLength(), resp.contentType(), resp.lastModified());
        } catch (RuntimeException e) {
            throw new StorageAccessException("getMetadata", key, e);
        }
    }

    @Override
    public String generatePresignedUrl(String key, Duration expiry, String downloadFilename) {
        Duration signatureDuration = expiry != null ? expiry : properties.getPresignExpiry();
        String contentDisposition = downloadFilename == null || downloadFilename.isBlank()
                ? "attachment"
                : "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(downloadFilename, StandardCharsets.UTF_8)
                    .replace("+", "%20");
        try {
            PresignedGetObjectRequest request = s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                    .signatureDuration(signatureDuration)
                    .getObjectRequest(GetObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(key)
                        .responseContentDisposition(contentDisposition)
                        .build())
                    .build()
            );
            return request.url().toString();
        } catch (RuntimeException e) {
            throw new StorageAccessException("generatePresignedUrl", key, e);
        }
    }
}
