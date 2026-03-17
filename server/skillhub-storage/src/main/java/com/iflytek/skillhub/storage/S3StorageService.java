package com.iflytek.skillhub.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
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

@Service
@ConditionalOnProperty(name = "skillhub.storage.provider", havingValue = "s3")
public class S3StorageService implements ObjectStorageService {
    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);
    private final S3StorageProperties properties;
    private S3Client s3Client;
    private S3Presigner s3Presigner;

    public S3StorageService(S3StorageProperties properties) { this.properties = properties; }

    @PostConstruct
    void init() {
        var builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                .forcePathStyle(properties.isForcePathStyle());
        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        this.s3Client = builder.build();
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
        ensureBucketExists();
    }

    private void ensureBucketExists() {
        if (!properties.isAutoCreateBucket()) {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.getBucket()).build());
            return;
        }
        try { s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.getBucket()).build()); }
        catch (NoSuchBucketException e) {
            log.info("Bucket '{}' does not exist, creating...", properties.getBucket());
            s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.getBucket()).build());
        }
    }

    @Override public void putObject(String key, InputStream data, long size, String contentType) {
        s3Client.putObject(PutObjectRequest.builder().bucket(properties.getBucket()).key(key).contentType(contentType).contentLength(size).build(), RequestBody.fromInputStream(data, size));
    }

    @Override public InputStream getObject(String key) {
        return s3Client.getObject(GetObjectRequest.builder().bucket(properties.getBucket()).key(key).build());
    }

    @Override public void deleteObject(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(properties.getBucket()).key(key).build());
    }

    @Override public void deleteObjects(List<String> keys) {
        if (keys.isEmpty()) return;
        List<ObjectIdentifier> ids = keys.stream().map(k -> ObjectIdentifier.builder().key(k).build()).toList();
        s3Client.deleteObjects(DeleteObjectsRequest.builder().bucket(properties.getBucket()).delete(Delete.builder().objects(ids).build()).build());
    }

    @Override public boolean exists(String key) {
        try { s3Client.headObject(HeadObjectRequest.builder().bucket(properties.getBucket()).key(key).build()); return true; }
        catch (NoSuchKeyException e) { return false; }
    }

    @Override public ObjectMetadata getMetadata(String key) {
        HeadObjectResponse resp = s3Client.headObject(HeadObjectRequest.builder().bucket(properties.getBucket()).key(key).build());
        return new ObjectMetadata(resp.contentLength(), resp.contentType(), resp.lastModified());
    }

    @Override
    public String generatePresignedUrl(String key, Duration expiry, String downloadFilename) {
        Duration signatureDuration = expiry != null ? expiry : properties.getPresignExpiry();
        String contentDisposition = downloadFilename == null || downloadFilename.isBlank()
                ? "attachment"
                : "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(downloadFilename, StandardCharsets.UTF_8)
                    .replace("+", "%20");
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
    }
}
