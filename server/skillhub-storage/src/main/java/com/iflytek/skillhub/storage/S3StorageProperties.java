package com.iflytek.skillhub.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "skillhub.storage.s3")
public class S3StorageProperties {
    private String endpoint;
    private String publicEndpoint;
    private String bucket = "skillhub";
    private String accessKey;
    private String secretKey;
    private String region = "us-east-1";
    private boolean forcePathStyle = true;
    private boolean disableChunkedEncoding = false;
    private boolean autoCreateBucket = false;
    private Duration presignExpiry = Duration.ofMinutes(10);
    private Integer maxConnections = 100;
    private Duration connectionAcquisitionTimeout = Duration.ofSeconds(2);
    private Duration apiCallAttemptTimeout = Duration.ofSeconds(10);
    private Duration apiCallTimeout = Duration.ofSeconds(30);

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getPublicEndpoint() { return publicEndpoint; }
    public void setPublicEndpoint(String publicEndpoint) { this.publicEndpoint = publicEndpoint; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public boolean isForcePathStyle() { return forcePathStyle; }
    public void setForcePathStyle(boolean forcePathStyle) { this.forcePathStyle = forcePathStyle; }
    public boolean isDisableChunkedEncoding() { return disableChunkedEncoding; }
    public void setDisableChunkedEncoding(boolean disableChunkedEncoding) { this.disableChunkedEncoding = disableChunkedEncoding; }
    public boolean isAutoCreateBucket() { return autoCreateBucket; }
    public void setAutoCreateBucket(boolean autoCreateBucket) { this.autoCreateBucket = autoCreateBucket; }
    public Duration getPresignExpiry() { return presignExpiry; }
    public void setPresignExpiry(Duration presignExpiry) { this.presignExpiry = presignExpiry; }
    public Integer getMaxConnections() { return maxConnections; }
    public void setMaxConnections(Integer maxConnections) { this.maxConnections = maxConnections; }
    public Duration getConnectionAcquisitionTimeout() { return connectionAcquisitionTimeout; }
    public void setConnectionAcquisitionTimeout(Duration connectionAcquisitionTimeout) { this.connectionAcquisitionTimeout = connectionAcquisitionTimeout; }
    public Duration getApiCallAttemptTimeout() { return apiCallAttemptTimeout; }
    public void setApiCallAttemptTimeout(Duration apiCallAttemptTimeout) { this.apiCallAttemptTimeout = apiCallAttemptTimeout; }
    public Duration getApiCallTimeout() { return apiCallTimeout; }
    public void setApiCallTimeout(Duration apiCallTimeout) { this.apiCallTimeout = apiCallTimeout; }
}
