---
title: Storage SPI
sidebar_position: 2
description: Storage service provider extension
---

# Storage SPI

## SPI Interface

```java
public interface ObjectStorageService {
    void store(String key, InputStream content, String contentType);
    InputStream retrieve(String key);
    void delete(String key);
    boolean exists(String key);
}
```

## Built-in Implementations

### LocalFileStorageService

Local filesystem implementation for development environment.

### S3StorageService

S3 protocol compatible implementation, supports:
- AWS S3
- MinIO
- Alibaba Cloud OSS
- Tencent Cloud COS
- Other S3-compatible storage

## Configuration

### Static Credentials (Access Key / Secret Key)

```bash
# Select storage provider
SKILLHUB_STORAGE_PROVIDER=s3

# S3 configuration
SKILLHUB_STORAGE_S3_ENDPOINT=https://s3.example.com
SKILLHUB_STORAGE_S3_BUCKET=skillhub
SKILLHUB_STORAGE_S3_ACCESS_KEY=xxx
SKILLHUB_STORAGE_S3_SECRET_KEY=xxx
```

### IAM Authentication

When deployed on AWS, you can omit the Access Key / Secret Key and let the SDK use IAM role authentication via the [Default Credentials Provider Chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html):

```bash
SKILLHUB_STORAGE_PROVIDER=s3
SKILLHUB_STORAGE_S3_BUCKET=skillhub
SKILLHUB_STORAGE_S3_REGION=us-east-1
# Leave ACCESS_KEY / SECRET_KEY blank to use IAM authentication
SKILLHUB_STORAGE_S3_ACCESS_KEY=
SKILLHUB_STORAGE_S3_SECRET_KEY=
```

Supported IAM authentication methods (in SDK priority order):
- Environment variables (`AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`)
- Java system properties
- Web Identity Token (EKS IRSA)
- AWS config file (`~/.aws/credentials`)
- EC2 Instance Profile
- ECS Task Role

## Custom Implementation

Implement `ObjectStorageService` interface and register as Spring Bean.

## Next Steps

- [FAQ](../../reference/faq) - FAQ
