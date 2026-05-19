package com.iflytek.skillhub.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private final S3StorageProperties properties;
    private final Object bucketPreparationLock = new Object();
    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private volatile boolean bucketPrepared;

    public S3StorageService(S3StorageProperties properties) { this.properties = properties; }

    @PostConstruct
    void init() {
        ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder()
                .maxConnections(properties.getMaxConnections())
                .connectionAcquisitionTimeout(properties.getConnectionAcquisitionTimeout());
        this.s3Client = buildS3Client(httpClientBuilder);
        this.s3Presigner = buildPresigner();
        String authMode = isStaticCredentials() ? "static credentials" : "IAM credentials (default provider chain)";
        if (properties.isAutoCreateBucket()) {
            log.info("Initialized S3 storage client for bucket '{}' using {} (bucket auto-creation is deferred until first write)",
                    properties.getBucket(), authMode);
        } else {
            log.info("Initialized S3 storage client for bucket '{}' using {}", properties.getBucket(), authMode);
        }
    }

    private boolean isStaticCredentials() {
        return properties.getAccessKey() != null && !properties.getAccessKey().isBlank()
                && properties.getSecretKey() != null && !properties.getSecretKey().isBlank();
    }

    AwsCredentialsProvider buildCredentialsProvider() {
        if (isStaticCredentials()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
        }
        return DefaultCredentialsProvider.create();
    }

    protected S3Client buildS3Client(ApacheHttpClient.Builder httpClientBuilder) {
        var builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(buildCredentialsProvider())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isForcePathStyle())
                        .chunkedEncodingEnabled(!properties.isDisableChunkedEncoding())
                        .build())
                .httpClientBuilder(httpClientBuilder)
                .overrideConfiguration(config -> config
                        .apiCallAttemptTimeout(properties.getApiCallAttemptTimeout())
                        .apiCallTimeout(properties.getApiCallTimeout()));
        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        return builder.build();
    }

    S3Presigner buildPresigner() {
        var presignerBuilder = S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(buildCredentialsProvider())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isForcePathStyle())
                        .build());
        if (properties.getPublicEndpoint() != null && !properties.getPublicEndpoint().isBlank()) {
            presignerBuilder.endpointOverride(URI.create(properties.getPublicEndpoint()));
        } else if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            presignerBuilder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        return presignerBuilder.build();
    }

    private void ensureBucketPrepared() {
        if (!properties.isAutoCreateBucket() || bucketPrepared) {
            return;
        }

        synchronized (bucketPreparationLock) {
            if (bucketPrepared) {
                return;
            }
            try {
                log.info("Bucket '{}' does not exist, creating...", properties.getBucket());
                s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.getBucket()).build());
            } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
                log.debug("Bucket '{}' was created concurrently, continuing", properties.getBucket());
            }
            bucketPrepared = true;
        }
    }

    private void putObjectInternal(String key, InputStream data, long size, String contentType) {
        putObjectInternal(key, RequestBody.fromInputStream(data, size), size, contentType);
    }

    private void putObjectInternal(String key, RequestBody requestBody, long size, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(key)
                        .contentType(contentType)
                        .contentLength(size)
                        .build(),
                requestBody);
    }

    private Path stagePutObjectBody(InputStream data) throws IOException {
        Path stagedBody = Files.createTempFile("skillhub-s3-upload-", ".tmp");
        try {
            Files.copy(data, stagedBody, StandardCopyOption.REPLACE_EXISTING);
            return stagedBody;
        } catch (IOException e) {
            Files.deleteIfExists(stagedBody);
            throw e;
        }
    }

    private void deleteStagedBody(Path stagedBody) {
        if (stagedBody == null) {
            return;
        }
        try {
            Files.deleteIfExists(stagedBody);
        } catch (IOException e) {
            log.warn("Failed to clean up staged S3 upload body {}", stagedBody, e);
        }
    }

    @Override public void putObject(String key, InputStream data, long size, String contentType) {
        Path stagedBody = null;
        try {
            if (!properties.isAutoCreateBucket() || bucketPrepared) {
                putObjectInternal(key, data, size, contentType);
                return;
            }

            stagedBody = stagePutObjectBody(data);
            try {
                putObjectInternal(key, RequestBody.fromFile(stagedBody), size, contentType);
                bucketPrepared = true;
            } catch (NoSuchBucketException e) {
                ensureBucketPrepared();
                putObjectInternal(key, RequestBody.fromFile(stagedBody), size, contentType);
            }
        } catch (IOException e) {
            throw new StorageAccessException("putObject", key, e);
        } catch (RuntimeException e) {
            throw new StorageAccessException("putObject", key, e);
        } finally {
            deleteStagedBody(stagedBody);
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
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(properties.getBucket()).key(key).build());
            return true;
        }
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
