package com.iflytek.skillhub.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skillhub.storage.s3")
public class S3StorageProperties {
    private String endpoint;
    private String bucket = "skillhub";
    private String accessKey;
    private String secretKey;
    private String region = "us-east-1";

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
}
